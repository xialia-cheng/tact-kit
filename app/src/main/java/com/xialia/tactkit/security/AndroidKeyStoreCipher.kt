package com.xialia.tactkit.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AndroidKeyStore AES/GCM/NoPadding cipher for protecting BYOK AI API keys.
 * Strictly refuses plaintext or raw Base64 fallback.
 */
object AndroidKeyStoreCipher {

  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "tactkit_byok_ai_master_key_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH_BITS = 128

  @Synchronized
  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  fun encrypt(plaintext: String): String {
    if (plaintext.isBlank()) return ""
    val secretKey = getOrCreateSecretKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
    val cipherB64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    return "$ivB64:$cipherB64"
  }

  fun decrypt(payload: String): String {
    if (payload.isBlank()) return ""
    val parts = payload.split(":")
    if (parts.size != 2) {
      throw SecurityException("Invalid encrypted API key payload format")
    }
    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
    val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
    val secretKey = getOrCreateSecretKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
    return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
  }
}
