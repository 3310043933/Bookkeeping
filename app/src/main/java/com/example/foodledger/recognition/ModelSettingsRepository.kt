package com.example.foodledger.recognition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ModelSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("model_settings", Context.MODE_PRIVATE)

    fun selectedProvider(): ModelProvider = runCatching {
        ModelProvider.valueOf(prefs.getString("selected_provider", ModelProvider.OPENAI.name)!!)
    }.getOrDefault(ModelProvider.OPENAI)

    fun select(provider: ModelProvider) {
        prefs.edit().putString("selected_provider", provider.name).apply()
    }

    fun hasKey(provider: ModelProvider): Boolean = getKey(provider).isNotBlank()

    fun getKey(provider: ModelProvider): String {
        val encoded = prefs.getString("key_${provider.name}", null) ?: return ""
        return runCatching { decrypt(encoded) }.getOrDefault("")
    }

    fun saveKey(provider: ModelProvider, value: String) {
        val editor = prefs.edit()
        if (value.isBlank()) editor.remove("key_${provider.name}")
        else editor.putString("key_${provider.name}", encrypt(value.trim()))
        editor.apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val combined = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
        return String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8)
    }

    private companion object { const val KEY_ALIAS = "food_ledger_model_keys" }
}
