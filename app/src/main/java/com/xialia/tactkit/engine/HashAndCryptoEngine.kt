package com.xialia.tactkit.engine

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class HashAlgorithm(val jcaName: String, val label: String, val bitLength: Int) {
  MD5("MD5", "MD5", 128),
  SHA_1("SHA-1", "SHA-1", 160),
  SHA_224("SHA-224", "SHA-224", 224),
  SHA_256("SHA-256", "SHA-256", 256),
  SHA_384("SHA-384", "SHA-384", 384),
  SHA_512("SHA-512", "SHA-512", 512),
}

data class FileHashResult(
  val fileName: String,
  val byteSize: Long,
  val algorithm: HashAlgorithm,
  val digestHex: String,
)

data class HashCompareResult(
  val leftNormalized: String,
  val rightNormalized: String,
  val matches: Boolean,
)

object HashAndCryptoEngine {

  private const val MAGIC_HEADER = "TKAES01"
  private const val SALT_LEN = 16
  private const val IV_LEN = 12
  private const val GCM_TAG_BITS = 128
  private const val PBKDF2_ITERATIONS = 120_000
  private const val KEY_BITS = 256

  fun hashText(text: String, algorithm: HashAlgorithm): String {
    val digest = MessageDigest.getInstance(algorithm.jcaName)
    val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.toHexString()
  }

  fun hashStream(input: InputStream, algorithm: HashAlgorithm): Pair<String, Long> {
    val digest = MessageDigest.getInstance(algorithm.jcaName)
    val buffer = ByteArray(8 * 1024)
    var totalBytes = 0L
    var read = input.read(buffer)
    while (read > 0) {
      digest.update(buffer, 0, read)
      totalBytes += read
      read = input.read(buffer)
    }
    return digest.digest().toHexString() to totalBytes
  }

  fun hashUri(context: Context, uri: Uri, displayName: String, algorithm: HashAlgorithm): FileHashResult {
    context.contentResolver.openInputStream(uri)?.use { stream ->
      val (hex, size) = hashStream(stream, algorithm)
      return FileHashResult(
        fileName = displayName,
        byteSize = size,
        algorithm = algorithm,
        digestHex = hex,
      )
    } ?: throw IllegalStateException("无法读取所选文件")
  }

  fun hashAllAlgorithms(inputProvider: () -> InputStream): Map<HashAlgorithm, String> {
    val digests = HashAlgorithm.entries.associateWith { MessageDigest.getInstance(it.jcaName) }
    inputProvider().use { stream ->
      val buffer = ByteArray(8 * 1024)
      var read = stream.read(buffer)
      while (read > 0) {
        digests.values.forEach { it.update(buffer, 0, read) }
        read = stream.read(buffer)
      }
    }
    return digests.mapValues { (_, md) -> md.digest().toHexString() }
  }

  fun compareHashes(left: String, right: String): HashCompareResult {
    val normLeft = left.trim().lowercase().replace(Regex("\\s+"), "")
    val normRight = right.trim().lowercase().replace(Regex("\\s+"), "")
    return HashCompareResult(
      leftNormalized = normLeft,
      rightNormalized = normRight,
      matches = normLeft.isNotEmpty() && normLeft == normRight,
    )
  }

  fun formatBatchReport(results: List<FileHashResult>): String {
    if (results.isEmpty()) return ""
    return buildString {
      appendLine("# 简工坊 (TactKit) 批量校验报告")
      results.forEachIndexed { idx, item ->
        appendLine("${idx + 1}. ${item.fileName} (${item.byteSize} B)")
        appendLine("   ${item.algorithm.label}: ${item.digestHex}")
      }
    }.trimEnd()
  }

  fun encryptBytesWithPassword(plainBytes: ByteArray, password: CharArray): ByteArray {
    require(password.isNotEmpty()) { "口令不能为空" }
    val rng = SecureRandom()
    val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
    val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
    val keyBytes = deriveAesKey(password, salt)
    return try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
      val encrypted = cipher.doFinal(plainBytes)
      val out = ByteArrayOutputStream(MAGIC_HEADER.length + SALT_LEN + IV_LEN + encrypted.size)
      out.write(MAGIC_HEADER.toByteArray(Charsets.US_ASCII))
      out.write(salt)
      out.write(iv)
      out.write(encrypted)
      out.toByteArray()
    } finally {
      Arrays.fill(keyBytes, 0.toByte())
    }
  }

  fun decryptBytesWithPassword(payload: ByteArray, password: CharArray): ByteArray {
    require(password.isNotEmpty()) { "口令不能为空" }
    val headerBytes = MAGIC_HEADER.toByteArray(Charsets.US_ASCII)
    val minLen = headerBytes.size + SALT_LEN + IV_LEN + 16
    require(payload.size >= minLen) { "密文格式无效或已损坏" }
    val actualHeader = payload.copyOfRange(0, headerBytes.size)
    require(actualHeader.contentEquals(headerBytes)) { "不支持的加密文件格式" }

    var offset = headerBytes.size
    val salt = payload.copyOfRange(offset, offset + SALT_LEN)
    offset += SALT_LEN
    val iv = payload.copyOfRange(offset, offset + IV_LEN)
    offset += IV_LEN
    val cipherBytes = payload.copyOfRange(offset, payload.size)

    val keyBytes = deriveAesKey(password, salt)
    return try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.doFinal(cipherBytes)
    } finally {
      Arrays.fill(keyBytes, 0.toByte())
    }
  }

  fun encryptTextWithPassword(plainText: String, password: CharArray): String {
    val encrypted = encryptBytesWithPassword(plainText.toByteArray(Charsets.UTF_8), password)
    return "TACTKIT_AES1:" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
  }

  fun decryptTextWithPassword(cipherText: String, password: CharArray): String {
    val clean = cipherText.trim()
    val b64 = clean.removePrefix("TACTKIT_AES1:").trim()
    val payload = Base64.decode(b64, Base64.DEFAULT)
    val decrypted = decryptBytesWithPassword(payload, password)
    return String(decrypted, Charsets.UTF_8)
  }

  fun encryptUriToUri(context: Context, inputUri: Uri, outputUri: Uri, password: CharArray) {
    val rawBytes =
      context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
        ?: throw IllegalStateException("无法读取源文件")
    val encrypted = encryptBytesWithPassword(rawBytes, password)
    context.contentResolver.openOutputStream(outputUri)?.use { out ->
      out.write(encrypted)
      out.flush()
    } ?: throw IllegalStateException("无法写入加密目标文件")
  }

  fun decryptUriToUri(context: Context, inputUri: Uri, outputUri: Uri, password: CharArray) {
    val cipherBytes =
      context.contentResolver.openInputStream(inputUri)?.use { it.readBytes() }
        ?: throw IllegalStateException("无法读取密文文件")
    val plain = decryptBytesWithPassword(cipherBytes, password)
    context.contentResolver.openOutputStream(outputUri)?.use { out ->
      out.write(plain)
      out.flush()
    } ?: throw IllegalStateException("无法写入解密目标文件")
  }

  private fun deriveAesKey(password: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
    return try {
      val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      factory.generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun ByteArray.toHexString(): String {
    val hexChars = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (b in this) {
      val i = b.toInt() and 0xff
      sb.append(hexChars[i ushr 4])
      sb.append(hexChars[i and 0x0f])
    }
    return sb.toString()
  }
}
