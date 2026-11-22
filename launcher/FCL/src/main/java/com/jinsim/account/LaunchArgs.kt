package com.jinsim.account

/**
 * 启动参数拼装：不同账号类型给出不同的字段。
 * 具体怎么塞进 FCL 的启动命令由调用方处理（离线传 username+uuid，微软传 accessToken+uuid）。
 */
object LaunchArgs {

    data class Args(
        val username: String,
        val uuid: String,
        /** 微软账号才有：已刷新过的 Xbox/Minecraft 链路 access token */
        val accessToken: String? = null,
        /** offline / msa */
        val userType: String
    )

    /**
     * 微软账号：先确认 access token 没过期；快过期就 refresh 并回写仓库。
     * 刷新失败抛异常，交给界面提示重新登录。
     */
    fun build(repo: AccountRepository, account: BoundAccount): Args {
        return when (account.type) {
            AccountType.OFFLINE -> Args(
                username = account.playerName,
                uuid = account.uuid.ifBlank { OfflineAccounts.uuidForName(account.playerName) },
                userType = "legacy"
            )

            AccountType.MICROSOFT -> {
                val now = System.currentTimeMillis()
                val cached = account.accessTokenCipher?.let { runCatching { AccountCrypto.decrypt(it) }.getOrNull() }
                val valid = cached != null && account.accessTokenExpiresAt - now > 5 * 60 * 1000L
                val access = if (valid) cached!! else refresh(repo, account)
                Args(
                    username = account.playerName,
                    uuid = account.uuid,
                    accessToken = access,
                    userType = "msa"
                )
            }

            AccountType.JINSIM -> Args(
                username = account.playerName,
                uuid = account.uuid,
                userType = "legacy"
            )
        }
    }

    private fun refresh(repo: AccountRepository, account: BoundAccount): String {
        val refreshCipher = account.refreshTokenCipher
            ?: throw IllegalStateException("微软账号缺少 refresh_token，请重新绑定")
        val refreshToken = AccountCrypto.decrypt(refreshCipher)
        val tokens = MicrosoftPkce.refresh(refreshToken)
        // 回写新令牌（refresh_token 可能轮换）
        val updated = account.copy(
            refreshTokenCipher = (tokens.refreshToken ?: refreshToken).let { AccountCrypto.encrypt(it) },
            accessTokenCipher = AccountCrypto.encrypt(tokens.accessToken),
            accessTokenExpiresAt = tokens.expiresAt
        )
        repo.upsert(updated)
        return tokens.accessToken
    }
}
