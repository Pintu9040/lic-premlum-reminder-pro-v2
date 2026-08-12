package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePreferences {
    private const val PREF_FILE_NAME = "lic_secure_prefs"
    private const val KEY_ALIAS = "lic_app_secure_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val TAG = "SecurePreferences"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }

        // Fallback: delete corrupt alias and regenerate
        keyStore.deleteEntry(KEY_ALIAS)
        generateKey()
        val newEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return newEntry.secretKey
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
    }

    fun saveSecureToken(context: Context, key: String, value: String) {
        try {
            if (value.isEmpty()) {
                getSharedPreferences(context).edit().remove(key).apply()
                return
            }
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            val base64Encoded = Base64.encodeToString(combined, Base64.DEFAULT)
            getSharedPreferences(context).edit().putString(key, base64Encoded).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving secure token: ${e.localizedMessage}")
            getSharedPreferences(context).edit().putString(key, value).apply()
        }
    }

    fun getSecureToken(context: Context, key: String, defaultValue: String = ""): String {
        return try {
            val storedBase64 = getSharedPreferences(context).getString(key, null) ?: return defaultValue
            val combined = Base64.decode(storedBase64, Base64.DEFAULT)

            if (combined.size <= 12) return storedBase64

            val iv = ByteArray(12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Throwable) {
            Log.w(TAG, "Notice reading secure token: ${e.localizedMessage}")
            getSharedPreferences(context).getString(key, defaultValue) ?: defaultValue
        }
    }

    fun clearSecureTokens(context: Context) {
        try {
            getSharedPreferences(context).edit().clear().apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing secure tokens: ${e.localizedMessage}")
        }
    }
}
