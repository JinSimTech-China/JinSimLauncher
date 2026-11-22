package com.jinsim.account

import org.json.JSONObject

/** 账号类型：Jinsim 主账号 / 微软 / 离线 */
enum class AccountType {
    JINSIM,
    MICROSOFT,
    OFFLINE;

    companion object {
        fun of(raw: String?): AccountType = try {
            valueOf((raw ?: "").uppercase())
        } catch (e: Exception) {
            OFFLINE
        }
    }
}

/**
 * 当前登录的 Jinsim 主账号。它是“账号列表的容器”，所有绑定账号都挂在它下面。
 * sessionToken 只存在内存 + 加密文件里，绝不明文写日志/SharedPreferences。
 */
data class JinsimAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null
)

/**
 * 绑定在 Jinsim 主账号下的附属账号。
 *
 * 安全约束：refreshToken / accessToken 存进来时就必须已经是密文（AccountCrypto.encrypt 的结果），
 * 仓库层会拒收疑似明文的字符串（见 AccountRepository.sanitize）。
 */
data class BoundAccount(
    val id: String,
    val type: AccountType,
    /** 展示名：离线账号=玩家名，微软=邮箱/ gamertag */
    val displayName: String,
    /** MC 启动用 username */
    val playerName: String,
    /** MC 启动用 uuid（离线可自定义，微软取 profile） */
    val uuid: String,
    /** 所属 Jinsim 主账号 id，云端同步的主键之一 */
    val ownerId: String,
    /** 微软 refresh_token 的密文；离线账号为 null */
    val refreshTokenCipher: String? = null,
    /** 微软 access_token 的密文 + 过期时间（毫秒），启动前会按需刷新 */
    val accessTokenCipher: String? = null,
    val accessTokenExpiresAt: Long = 0L,
    val isDefault: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("displayName", displayName)
        put("playerName", playerName)
        put("uuid", uuid)
        put("ownerId", ownerId)
        put("refreshTokenCipher", refreshTokenCipher)
        put("accessTokenCipher", accessTokenCipher)
        put("accessTokenExpiresAt", accessTokenExpiresAt)
        put("isDefault", isDefault)
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): BoundAccount = BoundAccount(
            id = o.optString("id"),
            type = AccountType.of(o.optString("type")),
            displayName = o.optString("displayName"),
            playerName = o.optString("playerName"),
            uuid = o.optString("uuid"),
            ownerId = o.optString("ownerId"),
            refreshTokenCipher = o.optString("refreshTokenCipher").ifBlank { null },
            accessTokenCipher = o.optString("accessTokenCipher").ifBlank { null },
            accessTokenExpiresAt = o.optLong("accessTokenExpiresAt"),
            isDefault = o.optBoolean("isDefault"),
            addedAt = o.optLong("addedAt")
        )
    }
}
