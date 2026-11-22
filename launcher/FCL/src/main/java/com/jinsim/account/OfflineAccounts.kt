package com.jinsim.account

import java.util.UUID

/** 离线账号：UUID 本地生成（与离线服一致的 OfflinePlayer 规则）或用户手动指定 */
object OfflineAccounts {

    /** 与 Minecraft 离线模式一致：UUID.nameUUIDFromBytes("OfflinePlayer:" + name) */
    fun uuidForName(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString()

    /** 允许 32 位无横线或标准 36 位写法，返回规范小写格式；非法返回 null */
    fun parseUuid(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val uuid = if (trimmed.length == 32 && trimmed.none { it == '-' }) {
                UUID.fromString(
                    trimmed.substring(0, 8) + "-" + trimmed.substring(8, 12) + "-" +
                            trimmed.substring(12, 16) + "-" + trimmed.substring(16, 20) + "-" +
                            trimmed.substring(20, 32)
                )
            } else UUID.fromString(trimmed)
            uuid.toString().lowercase()
        } catch (e: Exception) {
            null
        }
    }

    /** 玩家名基础校验：1~16 字符，只允许字母数字下划线 */
    fun isValidName(name: String): Boolean = name.length in 1..16 && name.matches(Regex("[A-Za-z0-9_]+"))
}
