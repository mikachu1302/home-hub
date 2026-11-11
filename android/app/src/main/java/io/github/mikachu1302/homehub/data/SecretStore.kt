package io.github.mikachu1302.homehub.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.mikachu1302.homehub.core.BridgeLogin
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credentials (AGENTS.md: never in plain preferences): encrypted with an AES key that lives in the Android Keystore
 * and never leaves it. Only the ciphertext is stored, in its own preferences file (backup is disabled for the app).
 * If the key is gone (app data cleared, a new phone), the stored values cannot be read and count as not set.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("home-hub-secrets", Context.MODE_PRIVATE)

    var bridgeLogin: BridgeLogin?
        get() = read(BRIDGE)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { BridgeLogin(it.optString("user"), it.optString("password")) }
            ?.takeIf { it.user.isNotEmpty() }
        set(value) = write(BRIDGE, value?.let { JSONObject().put("user", it.user).put("password", it.password).toString() })

    private fun read(name: String): String? = runCatching {
        val data = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
        String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE))
    }.getOrNull()

    private fun write(name: String, value: String?) {
        if (value == null) {
            prefs.edit { remove(name) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit { putString(name, Base64.encodeToString(data, Base64.NO_WRAP)) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "home-hub-secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val BRIDGE = "ir_bridge_login"
    }
}
