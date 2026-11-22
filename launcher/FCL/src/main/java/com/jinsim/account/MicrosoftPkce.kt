package com.jinsim.account

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 微软 OAuth2：授权码 + PKCE（公共客户端，无 client_secret）。
 *
 * 后台配置（Entra 应用注册）：
 *   - 受支持账户类型：仅个人 Microsoft 帐户
 *   - 重定向 URI：公共客户端/本机(移动和桌面) → jsl://oauth-callback
 *   - 身份验证 → 允许公共客户端流 = 是
 */
object MicrosoftPkce {

    /** Entra 应用注册里的“应用程序(客户端) ID” */
    const val CLIENT_ID = "bf8c9317-2acb-4df9-b76d-0fd515a02b31"

    /** 必须与 Entra 后台填的重定向 URI 完全一致 */
    const val REDIRECT_URI = "jsl://oauth-callback"

    /** Xbox Live 登录 + 离线刷新 + openid（openid 只是为了拿 id_token 读邮箱/昵称） */
    const val SCOPE = "XboxLive.signin offline_access openid profile"

    private const val AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0"

    data class Pkce(val verifier: String, val challenge: String, val state: String)

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAt: Long
    ) {
        /** 从 id_token 的 payload 里读展示用信息，不验签（只用于界面显示，token 本身由微软签发） */
        val email: String?
            get() = claim("preferred_username") ?: claim("email")
        val displayName: String?
            get() = claim("name")

        private fun claim(key: String): String? {
            val token = idToken ?: return null
            val parts = token.split('.')
            if (parts.size < 2) return null
            return try {
                val json = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                JSONObject(json).optString(key).ifBlank { null }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val raw = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun newPkce(): Pkce {
        val verifier = randomUrlSafe(32) // 43 字符，符合 RFC 7636
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return Pkce(verifier, challenge, randomUrlSafe(16))
    }

    fun authorizeUrl(pkce: Pkce): String {
        val query = listOf(
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "response_mode" to "query",
            "scope" to SCOPE,
            "state" to pkce.state,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
            "prompt" to "select_account"
        ).joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8") }
        return "$AUTHORITY/authorize?$query"
    }

    fun exchangeCode(code: String, verifier: String): TokenResult {
        val text = Http.postForm(
            "$AUTHORITY/token",
            mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier,
                "scope" to SCOPE
            )
        )
        return parseToken(text)
    }

    fun refresh(refreshToken: String): TokenResult {
        val text = Http.postForm(
            "$AUTHORITY/token",
            mapOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "redirect_uri" to REDIRECT_URI,
                "scope" to SCOPE
            )
        )
        return parseToken(text)
    }

    private fun parseToken(text: String): TokenResult {
        val json = JSONObject(text)
        val access = json.optString("access_token")
        if (access.isBlank()) {
            throw IllegalStateException("token endpoint returned no access_token: ${json.optString("error")} ${json.optString("error_description")}")
        }
        val expiresIn = json.optLong("expires_in", 3600L)
        return TokenResult(
            accessToken = access,
            refreshToken = json.optString("refresh_token").ifBlank { null },
            idToken = json.optString("id_token").ifBlank { null },
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L
        )
    }

    /** 从回调 URI 里取出 code / state */
    fun parseCallback(uri: Uri): Pair<String?, String?> =
        uri.getQueryParameter("code") to uri.getQueryParameter("state")
}
