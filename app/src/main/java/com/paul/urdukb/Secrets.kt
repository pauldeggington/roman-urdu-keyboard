package com.paul.urdukb

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the two secrets the person pastes (Hugging Face token, GIPHY key) encrypted with a key that lives in the
 * Android Keystore, so they are not readable in the app's saved settings or in a backup. If the Keystore is not
 * usable on some phone, the value is stored as it was before rather than being lost.
 */
object Secrets {
    private const val ALIAS = "urdukb_secrets_v1"
    private const val PREFIX = "enc1:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Text to save. Empty stays empty. */
    fun seal(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            PREFIX + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        } catch (_: Throwable) {
            plain
        }
    }

    private val opened = HashMap<String, String>()

    /** The secret from what was saved: decrypts, or returns old plain values as they are. Empty when unreadable. */
    @Synchronized
    fun open(saved: String): String {
        if (!saved.startsWith(PREFIX)) return saved
        opened[saved]?.let { return it }   // asking the Keystore each time would be slow on the screen's thread
        return try {
            val raw = Base64.decode(saved.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12)).also { if (opened.size > 8) opened.clear(); opened[saved] = it }
        } catch (_: Throwable) {
            ""
        }
    }
}
