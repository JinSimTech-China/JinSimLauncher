package com.jinsim.account

import org.json.JSONObject

/**
 * Xbox Live → XSTS → Minecraft 登录。
 *
 * 为什么放在手机端跑：Mojang 的网关会把来自 Cloudflare 的请求直接挡掉
 * （返回 Azure Front Door 的 “request blocked”），服务端换完 MSA 令牌就走到头了。
 * 手机用的是运营商/WiFi 的正常出口，不会触发这个风控。
 *
 * 全流程只在内存中处理令牌，算完立刻把 MC 令牌交给后端加密入库，不在本地明文落盘。
 */
object McLogin {

    private const val UA = "MinecraftLauncher/2.4.22"
    private val HEADERS = mapOf("user-agent" to UA, "accept" to "application/json")

    data class Result(val accessToken: String, val uuid: String, val name: String)

    /** 拿 MSA access token 换 Minecraft 令牌和档案 */
    fun login(msaAccessToken: String): Result {
        val uhs = xboxUserToken(msaAccessToken)
        val xsts = xstsToken(uhs.first)
        val mcToken = loginWithXbox(uhs.second, xsts)
        val profile = profile(mcToken)
        return Result(mcToken, profile.first, profile.second)
    }

    /** 1. Xbox Live 用户认证，返回 (XBL token, uhs) */
    private fun xboxUserToken(msaAccessToken: String): Pair<String, String> {
        val body = JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$msaAccessToken")
            })
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }.toString()
        val json = JSONObject(Http.postJson("https://user.auth.xboxlive.com/user/authenticate", body, null, HEADERS))
        val token = json.optString("Token")
        val uhs = json.optJSONObject("DisplayClaims")
            ?.optJSONArray("xui")?.optJSONObject(0)?.optString("uhs")
        if (token.isBlank() || uhs.isNullOrBlank()) throw IllegalStateException("Xbox Live 认证失败")
        return token to uhs
    }

    /** 2. XSTS 授权，换 Minecraft 服务的令牌 */
    private fun xstsToken(xblToken: String): String {
        val body = JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("SandboxId", "RETAIL")
                put("UserTokens", org.json.JSONArray().put(xblToken))
            })
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }.toString()
        val json = JSONObject(Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body, null, HEADERS))
        val token = json.optString("Token")
        if (token.isBlank()) {
            throw IllegalStateException(xstsMessage(json.optString("XErr")))
        }
        return token
    }

    /** 3. Minecraft 服务登录 */
    private fun loginWithXbox(uhs: String, xstsToken: String): String {
        val body = JSONObject().put("identityToken", "XBL3.0 x=$uhs;$xstsToken").toString()
        val json = JSONObject(
            Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body, null, HEADERS)
        )
        val token = json.optString("access_token")
        if (token.isBlank()) {
            throw IllegalStateException(json.optString("errorMessage").ifBlank { "Minecraft 登录被拒" })
        }
        return token
    }

    /** 4. 拉游戏档案，拿 uuid 和玩家名 */
    private fun profile(mcToken: String): Pair<String, String> {
        val json = JSONObject(
            Http.get("https://api.minecraftservices.com/minecraft/profile", mcToken, HEADERS)
        )
        val uuid = json.optString("id")
        if (uuid.isBlank()) throw IllegalStateException("这个账号没有 Minecraft 游戏档案")
        return uuid to json.optString("name")
    }

    private fun xstsMessage(xerr: String?): String = when (xerr) {
        "2148916233" -> "这个微软账号还没注册 Xbox 账号"
        "2148916235" -> "所在地区不支持 Xbox Live"
        "2148916236", "2148916237" -> "需要先完成成人验证"
        "2148916238" -> "未成年账号需要加入家庭组"
        "2148916227", "2148916229" -> "该账号被 Xbox Live 封禁"
        else -> "XSTS 授权失败" + if (xerr.isNullOrBlank()) "" else "（XErr $xerr）"
    }
}
