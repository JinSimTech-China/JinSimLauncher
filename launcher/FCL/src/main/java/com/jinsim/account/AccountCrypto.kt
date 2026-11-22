package com.jinsim.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本地敏感数据加密。密钥由 Android Keystore 生成并保管，导出不了；
 * 落盘内容一律是 AES/GCM 密文的 Base64，禁止明文持久化。
 */
object AccountCrypto {

    private const val KEY_ALIAS = "jsl_account_token_key_v2"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // AndroidKeyStore 默认要求自己随机出 IV；我们手动传 IV，必须关掉这个限制，
                // 否则 init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec) 会抛
                // "Caller-provided IV not permitted"
                .setRandomizedEncryptionRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + body.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(body, 0, packed, iv.size, body.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        val packed = Base64.decode(cipherText, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "bad cipher blob" }
        val iv = packed.copyOfRange(0, IV_BYTES)
        val body = packed.copyOfRange(IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    /** 粗判：JWT / 微软 token 都有固定前缀或足够长，用于阻止明文误存 */
    fun looksLikePlainToken(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.startsWith("ey") || value.startsWith("M.C") || value.length > 200
    }
}
