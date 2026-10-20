package com.spendlens.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the database key.
 *
 * A random 256-bit key is created on first run. It is stored only in wrapped form: encrypted with an
 * AES-256-GCM key that lives inside Android Keystore (in StrongBox secure hardware when the phone has it)
 * and can never be exported. Destroying the Keystore key makes any leftover database file unreadable.
 */
class KeyManager(
    private val context: Context,
    private val alias: String = "spendlens_db_wrap_v1",
    private val fileName: String = "db_key.bin",
) {
    private val file: File get() = File(context.noBackupFilesDir, fileName)

    /** SQLCipher raw-key form x'<64 hex>' skips the slow passphrase-stretching step on every open. */
    fun sqlcipherPassphrase(): ByteArray {
        val raw = rawKey()
        val hex = raw.joinToString("") { "%02x".format(it) }
        raw.fill(0)
        return "x'$hex'".toByteArray(Charsets.US_ASCII)
    }

    @Synchronized
    fun rawKey(): ByteArray {
        val f = file
        if (f.exists()) return unwrap(f.readBytes())
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(raw)
        val tmp = File(f.parentFile, "$fileName.tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(f)) { f.writeBytes(wrapped); tmp.delete() }
        return raw
    }

    /** Where the wrapping key lives, in words, for the Settings screen and device tests. */
    fun protectionLevel(): String = runCatching {
        val key = wrappingKey()
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox secure chip"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "Secure hardware (TEE)"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software keystore"
                else -> "Hardware-backed keystore"
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "Secure hardware" else "Software keystore"
        }
    }.getOrDefault("Android Keystore")

    @Synchronized
    fun destroy() {
        file.delete()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrappingKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val wantStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (wantStrongBox) {
            try {
                return generate(strongBox = true)
            } catch (e: Exception) {
                // StrongBox unavailable or it rejects AES-256: fall back to the regular hardware-backed Keystore.
                runCatching { ks.deleteEntry(alias) }
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(builder.build())
        return gen.generateKey()
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(raw)
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + ct
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0] == FORMAT_VERSION) { "Unknown key file format" }
        val ivLen = blob[1].toInt()
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val ct = blob.copyOfRange(2 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
    }
}
