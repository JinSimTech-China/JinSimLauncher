package com.jinsim.account

import org.json.JSONArray
import org.json.JSONObject

/**
 * JinSim 后端接口（https://jst-api.jcmj.ren）。
 *
 * 约定：
 *  - 全部走 HTTPS，会话令牌放在 Authorization: Bearer
 *  - 返回体一律 { ok: true, ... }，字段使用 snake_case
 *  - 微软令牌在服务端加密落库，客户端再本地加密一层
 */
object JslApi {

    const val BASE_URL = "https://jst-api.jcmj.ren/api/v1"

    data class LoginResult(
        val token: String,
        val account: JinsimAccount,
        val accounts: List<BoundAccount>
    )

    /** 登录 JinSim 主账号：POST /auth/login */
    fun login(email: String, password: String): LoginResult {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val text = Http.postJson("$BASE_URL/auth/login", body)
        val json = JSONObject(text)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("message").ifBlank { "登录失败" })
        }
        val token = json.optString("session_token")
        if (token.isBlank()) throw IllegalStateException("服务端没有返回会话令牌")
        return LoginResult(token, parseUser(json.optJSONObject("user"), email), parseAccounts(json.optJSONArray("accounts")))
    }

    /** 注册 JinSim 主账号：POST /auth/register */
    fun register(email: String, password: String, displayName: String?): LoginResult {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("display_name", displayName ?: email.substringBefore('@'))
            .toString()
        val text = Http.postJson("$BASE_URL/auth/register", body)
        val json = JSONObject(text)
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("message").ifBlank { "注册失败" })
        }
        val token = json.optString("session_token")
        if (token.isBlank()) throw IllegalStateException("服务端没有返回会话令牌")
        return LoginResult(token, parseUser(json.optJSONObject("user"), email), parseAccounts(json.optJSONArray("accounts")))
    }

    /** 当前会话是否还有效：GET /me */
    fun me(token: String): JinsimAccount {
        val json = JSONObject(Http.get("$BASE_URL/me", token))
        if (!json.optBoolean("ok", false)) throw IllegalStateException("会话已失效")
        return parseUser(json.optJSONObject("user"), "")
    }

    fun logout(token: String) {
        runCatching { Http.postJson("$BASE_URL/auth/logout", "{}", token) }
    }

    /** 拉当前 JinSim 账号下全部附属账号：GET /accounts */
    @JvmStatic
    fun pullAccounts(token: String): List<BoundAccount> {
        val json = JSONObject(Http.get("$BASE_URL/accounts", token))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("message").ifBlank { "拉取账号失败" })
        return parseAccounts(json.optJSONArray("accounts"))
    }

    /** 把本地离线账号登记到云端：POST /accounts/offline（幂等由调用方按 uuid 判定） */
    @JvmStatic
    fun pushOffline(token: String, name: String, uuid: String): String? {
        val body = JSONObject().put("name", name).put("uuid", uuid).toString()
        val json = JSONObject(Http.postJson("$BASE_URL/accounts/offline", body, token))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("message").ifBlank { "登记离线账号失败" })
        return json.optJSONObject("account")?.optString("id")
    }

    /** 把第三方皮肤站账号登记到云端：POST /accounts/authlib */
    @JvmStatic
    fun pushAuthlib(token: String, name: String, uuid: String, serverUrl: String): String? {
        val body = JSONObject().put("name", name).put("uuid", uuid).put("server_url", serverUrl).toString()
        val json = JSONObject(Http.postJson("$BASE_URL/accounts/authlib", body, token))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("message").ifBlank { "登记第三方账号失败" })
        return json.optJSONObject("account")?.optString("id")
    }

    fun deleteAccount(token: String, id: String) {
        Http.delete("$BASE_URL/accounts/$id", token)
    }

    fun setDefault(token: String, id: String) {
        Http.postJson("$BASE_URL/accounts/$id/default", "{}", token)
    }

    /**
     * 发起微软授权：走官方 MSA 客户端（00000000402b5328）+ desktop.srf 回调。
     * 后端没法用我们自己的应用换令牌（缺 Xbox / Minecraft 权限），所以只能用官方客户端。
     */
    fun msaStart(token: String): String {
        val json = JSONObject(Http.postJson("$BASE_URL/mc/msa/start", "{}", token))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("message").ifBlank { "发起授权失败" })
        val authUrl = json.optString("auth_url")
        if (authUrl.isBlank()) throw IllegalStateException("服务端没给出授权地址")
        return authUrl
    }

    /** 用授权码换令牌并落库。扩展拦到的 code 或手抄的 code 都走这里。 */
    fun msaExchange(token: String, code: String): String {
        val body = JSONObject().put("code", code.trim()).toString()
        val json = JSONObject(Http.postJson("$BASE_URL/mc/msa/exchange", body, token))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("message").ifBlank { "兑换失败" })
        return json.optString("name").ifBlank { "Minecraft 账号" }
    }

    /** 启动前取本账号的 Minecraft 令牌：POST /accounts/{id}/session（令牌由后端加密保存，只在本人会话可取） */
    @JvmStatic
    fun session(token: String, accountId: Long): JSONObject {
        val json = JSONObject(Http.postJson("$BASE_URL/accounts/$accountId/session", "{}", token))
        if (!json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("message").ifBlank { "令牌已失效，请重新绑定微软账号" })
        }
        return json
    }

    // ---------------------------------------------------------------- 解析

    private fun parseUser(o: JSONObject?, fallbackEmail: String): JinsimAccount {
        if (o == null) return JinsimAccount("", fallbackEmail, fallbackEmail.substringBefore('@'), null)
        val email = o.optString("email").ifBlank { fallbackEmail }
        return JinsimAccount(
            id = o.optString("id"),
            email = email,
            displayName = o.optString("display_name").ifBlank { email.substringBefore('@') },
            avatarUrl = null
        )
    }

    private fun parseAccounts(arr: JSONArray?): List<BoundAccount> {
        if (arr == null) return emptyList()
        val out = ArrayList<BoundAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            out.add(
                BoundAccount(
                    id = id,
                    type = AccountType.of(o.optString("type")),
                    displayName = o.optString("name").ifBlank { o.optString("mc_name") },
                    playerName = o.optString("mc_name"),
                    uuid = o.optString("uuid"),
                    ownerId = "",
                    refreshTokenCipher = null,
                    accessTokenCipher = null,
                    accessTokenExpiresAt = 0L,
                    serverUrl = o.optString("server_url").ifBlank { null },
                    isDefault = o.optBoolean("is_default", false),
                    addedAt = 0L
                )
            )
        }
        return out
    }
}
