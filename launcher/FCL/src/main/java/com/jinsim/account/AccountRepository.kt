package com.jinsim.account

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本地账号仓库。
 *
 * 落盘策略：整个 JSON 用 AccountCrypto 加密后写入 filesDir/jsl_accounts.dat；
 * 里面的微软 refresh/access token 再加一层 AccountCrypto 加密（双层，防单点泄露）。
 * 任何 token 进仓库前都会被强制转成密文，明文持久化会在 sanitize 阶段被拦。
 *
 * 归属约束：附属账号的 ownerId 必须等于当前 Jinsim 主账号 id；主账号 id 也不允许出现在附属列表里。
 */
class AccountRepository private constructor(private val context: Context) {

    data class Snapshot(
        val jinsim: JinsimAccount?,
        val sessionTokenCipher: String?,
        val accounts: List<BoundAccount>
    )

    private val file = File(context.filesDir, "jsl_accounts.dat")
    private val listeners = mutableListOf<() -> Unit>()
    private var snapshot = Snapshot(null, null, emptyList())

    init {
        load()
    }

    // ---------- 读写 ----------

    private fun load() {
        if (!file.isFile) return
        snapshot = try {
            val plain = AccountCrypto.decrypt(file.readText())
            val root = JSONObject(plain)
            val jinsimObj = root.optJSONObject("jinsim")
            val jinsim = jinsimObj?.let {
                JinsimAccount(
                    id = it.optString("id"),
                    email = it.optString("email"),
                    displayName = it.optString("displayName"),
                    avatarUrl = it.optString("avatarUrl").ifBlank { null }
                )
            }
            val list = root.optJSONArray("accounts")?.let { arr ->
                (0 until arr.length()).map { i -> BoundAccount.fromJson(arr.getJSONObject(i)) }
            } ?: emptyList()
            Snapshot(jinsim, root.optString("sessionTokenCipher").ifBlank { null }, list)
        } catch (e: Exception) {
            // 密钥轮换 / 文件损坏：不要崩，清空重来，账号可重新从云端拉
            Snapshot(null, null, emptyList())
        }
    }

    private fun persist() {
        val root = JSONObject().apply {
            snapshot.jinsim?.let {
                put("jinsim", JSONObject().apply {
                    put("id", it.id)
                    put("email", it.email)
                    put("displayName", it.displayName)
                    put("avatarUrl", it.avatarUrl)
                })
            }
            put("sessionTokenCipher", snapshot.sessionTokenCipher)
            put("accounts", JSONArray().apply {
                snapshot.accounts.forEach { put(it.toJson()) }
            })
        }
        file.writeText(AccountCrypto.encrypt(root.toString()))
        notifyChanged()
    }

    private fun notifyChanged() {
        listeners.forEach { it() }
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    // ---------- 主账号 ----------

    fun setJinsim(account: JinsimAccount, sessionToken: String) {
        snapshot = snapshot.copy(
            jinsim = account,
            sessionTokenCipher = AccountCrypto.encrypt(sessionToken),
            // 切换主账号时，本地旧绑定清空，随后从云端重新拉
            accounts = emptyList()
        )
        persist()
    }

    fun logout() {
        snapshot = Snapshot(null, null, emptyList())
        persist()
    }

    /**
     * 一次性彻底清空本地账号数据。
     * 用 tag 做幂等标记：同一个 tag 只清一次，之后正常读写。
     */
    fun hardResetOnce(context: Context, tag: String) {
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (prefs.getBoolean(tag, false)) return
        runCatching { if (file.isFile) file.delete() }
        snapshot = Snapshot(null, null, emptyList())
        prefs.edit().putBoolean(tag, true).apply()
        notifyChanged()
    }

    fun jinsim(): JinsimAccount? = snapshot.jinsim

    fun sessionToken(): String? = snapshot.sessionTokenCipher?.let {
        try { AccountCrypto.decrypt(it) } catch (e: Exception) { null }
    }

    // ---------- 附属账号 ----------

    fun accounts(): List<BoundAccount> = snapshot.accounts

    fun defaultAccount(): BoundAccount? = snapshot.accounts.firstOrNull { it.isDefault }
        ?: snapshot.accounts.firstOrNull()

    fun byId(id: String): BoundAccount? = snapshot.accounts.firstOrNull { it.id == id }

    /** 新增或覆盖。返回 null 表示成功，否则是拒绝原因 */
    fun upsert(account: BoundAccount): String? {
        val owner = snapshot.jinsim ?: return "请先登录 Jinsim 主账号"
        if (account.ownerId != owner.id) return "账号不属于当前 Jinsim 主账号"
        if (account.id == owner.id) return "主账号不能作为附属账号"
        if (AccountCrypto.looksLikePlainToken(account.refreshTokenCipher) ||
            AccountCrypto.looksLikePlainToken(account.accessTokenCipher)
        ) return "敏感令牌必须加密后再入库"

        val existing = snapshot.accounts.filter { it.id != account.id }
        val defaultFlag = account.isDefault || existing.isEmpty() && snapshot.accounts.isEmpty()
        val newList = existing + account.copy(
            isDefault = defaultFlag,
            displayName = account.displayName.ifBlank { account.playerName }
        )
        snapshot = snapshot.copy(accounts = newList.map {
            if (it.id != account.id && defaultFlag) it.copy(isDefault = false) else it
        })
        persist()
        return null
    }

    /** 解绑：主账号永远不在这个列表里，所以这里天然解不掉主账号 */
    fun remove(id: String): Boolean {
        val owner = snapshot.jinsim ?: return false
        if (id == owner.id) return false
        val removed = snapshot.accounts.firstOrNull { it.id == id } ?: return false
        var list = snapshot.accounts - removed
        if (removed.isDefault && list.isNotEmpty()) list = list.mapIndexed { i, a -> a.copy(isDefault = i == 0) }
        snapshot = snapshot.copy(accounts = list)
        persist()
        return true
    }

    fun setDefault(id: String): Boolean {
        if (snapshot.accounts.none { it.id == id }) return false
        snapshot = snapshot.copy(accounts = snapshot.accounts.map { it.copy(isDefault = it.id == id) })
        persist()
        return true
    }

    /** 微软令牌入库：统一在这里加密，调用方永远拿不到明文落盘的机会 */
    fun putMicrosoft(
        displayName: String,
        playerName: String,
        uuid: String,
        tokens: MicrosoftPkce.TokenResult
    ): BoundAccount? {
        val owner = snapshot.jinsim ?: return null
        val account = BoundAccount(
            id = "ms-" + (uuid.ifBlank { System.currentTimeMillis().toString() }),
            type = AccountType.MICROSOFT,
            displayName = displayName,
            playerName = playerName,
            uuid = uuid,
            ownerId = owner.id,
            refreshTokenCipher = tokens.refreshToken?.let { AccountCrypto.encrypt(it) },
            accessTokenCipher = AccountCrypto.encrypt(tokens.accessToken),
            accessTokenExpiresAt = tokens.expiresAt
        )
        return if (upsert(account) == null) account else null
    }

    fun putOffline(name: String, uuid: String): BoundAccount? {
        val owner = snapshot.jinsim ?: return null
        val account = BoundAccount(
            id = "off-" + uuid,
            type = AccountType.OFFLINE,
            displayName = name,
            playerName = name,
            uuid = uuid,
            ownerId = owner.id
        )
        return if (upsert(account) == null) account else null
    }

    /** 云端拉下来的列表覆盖本地（保留本地默认选中） */
    fun replaceFromCloud(list: List<BoundAccount>) {
        val owner = snapshot.jinsim ?: return
        val defaultId = defaultAccount()?.id
        val sanitized = list.filter { it.ownerId == owner.id }.map { acc ->
            acc.copy(
                refreshTokenCipher = acc.refreshTokenCipher?.let { safeCipher(it) },
                accessTokenCipher = acc.accessTokenCipher?.let { safeCipher(it) }
            )
        }
        snapshot = snapshot.copy(accounts = sanitized.map { it.copy(isDefault = it.id == defaultId) })
        persist()
    }

    private fun safeCipher(value: String): String =
        if (AccountCrypto.looksLikePlainToken(value)) AccountCrypto.encrypt(value) else value

    companion object {
        @Volatile
        private var instance: AccountRepository? = null

        fun get(context: Context): AccountRepository =
            instance ?: synchronized(this) {
                instance ?: AccountRepository(context.applicationContext).also { instance = it }
            }
    }
}
