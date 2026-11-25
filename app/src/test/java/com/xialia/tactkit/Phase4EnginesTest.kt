package com.xialia.tactkit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.domain.ToolCatalog
import com.xialia.tactkit.engine.DailyUtilityEngine
import com.xialia.tactkit.engine.DevToolkitEngine
import com.xialia.tactkit.engine.ElectronicsEngine
import com.xialia.tactkit.engine.HashAlgorithm
import com.xialia.tactkit.engine.HashAndCryptoEngine
import com.xialia.tactkit.engine.LanShareServer
import com.xialia.tactkit.engine.LanSharedOutFile
import com.xialia.tactkit.engine.QrCodeEngine
import com.xialia.tactkit.engine.ResistorBandColor
import com.xialia.tactkit.engine.UnitCategory
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase4EnginesTest {

  @Test
  fun `all tools in ToolCatalog are wired and available`() {
    assertTrue(ToolCatalog.allTools.isNotEmpty())
    assertTrue(ToolCatalog.allTools.all { it.isAvailable })
    assertNotNull(ToolCatalog.findById("hash_workshop"))
    assertNotNull(ToolCatalog.findById("crypto_vault"))
    assertNotNull(ToolCatalog.findById("lan_share"))
    assertNotNull(ToolCatalog.findById("qr_studio"))
    assertNotNull(ToolCatalog.findById("metal_detector"))
  }

  @Test
  fun `HashAndCryptoEngine computes all 6 hash algorithms and compares accurately`() {
    val sha256 = HashAndCryptoEngine.hashText("hello", HashAlgorithm.SHA_256)
    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha256)

    val all = HashAndCryptoEngine.hashAllAlgorithms { ByteArrayInputStream("hello".toByteArray()) }
    assertEquals(6, all.size)
    assertEquals("5d41402abc4b2a76b9719d911017c592", all[HashAlgorithm.MD5])
    assertEquals(sha256, all[HashAlgorithm.SHA_256])

    val compareMatch = HashAndCryptoEngine.compareHashes("  $sha256 ", sha256.uppercase())
    assertTrue(compareMatch.matches)

    val compareMismatch = HashAndCryptoEngine.compareHashes(sha256, "deadbeef")
    assertFalse(compareMismatch.matches)
  }

  @Test
  fun `HashAndCryptoEngine AES-256-GCM round-trips and rejects wrong password`() {
    val secretBytes = "TactKit Local-First Secret Payload 12345!".toByteArray(Charsets.UTF_8)
    val pass = "StrongPass#2026".toCharArray()
    val encrypted = HashAndCryptoEngine.encryptBytesWithPassword(secretBytes, pass)
    val decrypted = HashAndCryptoEngine.decryptBytesWithPassword(encrypted, "StrongPass#2026".toCharArray())
    assertArrayEquals(secretBytes, decrypted)

    val cipherNote = HashAndCryptoEngine.encryptTextWithPassword("私钥备忘", "pass123".toCharArray())
    assertTrue(cipherNote.startsWith("TACTKIT_AES1:"))
    assertEquals("私钥备忘", HashAndCryptoEngine.decryptTextWithPassword(cipherNote, "pass123".toCharArray()))

    try {
      HashAndCryptoEngine.decryptBytesWithPassword(encrypted, "WrongPass".toCharArray())
      fail("Expected AEADBadTagException on wrong password")
    } catch (_: AEADBadTagException) {
      // Expected
    }
  }

  @Test
  fun `DevToolkitEngine handles Base64, URL tracking removal, JWT, Regex, Radix, and CIDR`() {
    val b64 = DevToolkitEngine.encodeBase64("简工坊 TactKit", urlSafe = true)
    assertEquals("简工坊 TactKit", DevToolkitEngine.decodeBase64(b64, urlSafe = true))

    val cleaned =
      DevToolkitEngine.stripTrackingParams(
        "https://example.com/post?id=42&utm_source=wechat&fbclid=xyz&spm=1.2#top"
      )
    assertEquals("https://example.com/post?id=42#top", cleaned.cleanedUrl)
    assertEquals(listOf("utm_source", "fbclid", "spm"), cleaned.removedParams)

    val hdr = DevToolkitEngine.encodeBase64("""{"alg":"HS256","typ":"JWT"}""", urlSafe = true)
    val pld = DevToolkitEngine.encodeBase64("""{"sub":"xialia","role":"admin"}""", urlSafe = true)
    val jwt = DevToolkitEngine.decodeJwt("$hdr.$pld.sig")
    assertTrue(jwt.payloadPretty.contains("\"sub\": \"xialia\""))

    val regex = DevToolkitEngine.testRegex("""(\d+)-(\w+)""", "item 12-alpha and 34-beta")
    assertTrue(regex.isValid)
    assertEquals(2, regex.matches.size)
    assertEquals(listOf("12", "alpha"), regex.matches[0].groups)

    val radix = DevToolkitEngine.convertRadix("0xFF", 16)
    assertEquals("255", radix.decimal)
    assertEquals("11111111", radix.binary)

    val cidr = DevToolkitEngine.calculateIpv4Cidr("192.168.1.42/24")
    assertEquals("192.168.1.0", cidr.networkAddress)
    assertEquals("192.168.1.255", cidr.broadcastAddress)
    assertEquals("255.255.255.0", cidr.netmask)
    assertEquals(254L, cidr.usableHosts)
  }

  @Test
  fun `ElectronicsEngine calculates 4-band, 5-band resistors and voltage divider`() {
    val r4 =
      ElectronicsEngine.calculate4BandResistor(
        ResistorBandColor.BROWN,
        ResistorBandColor.BLACK,
        ResistorBandColor.RED,
        ResistorBandColor.GOLD,
      )
    assertEquals("1 kΩ", r4.formattedResistance)
    assertEquals(5.0, r4.tolerancePercent, 0.001)

    val r5 =
      ElectronicsEngine.calculate5BandResistor(
        ResistorBandColor.RED,
        ResistorBandColor.VIOLET,
        ResistorBandColor.BLACK,
        ResistorBandColor.BROWN,
        ResistorBandColor.BROWN,
      )
    assertEquals("2.7 kΩ", r5.formattedResistance)
    assertEquals(1.0, r5.tolerancePercent, 0.001)

    val div = ElectronicsEngine.calculateVoltageDivider(vIn = 5.0, r1Ohms = 10_000.0, r2Ohms = 10_000.0)
    assertEquals(2.5, div.vOut, 0.0001)
    assertEquals(0.25, div.currentMa, 0.0001)
  }

  @Test
  fun `DailyUtilityEngine converts units and splits AA bills accurately`() {
    assertEquals(1.0, DailyUtilityEngine.convert(UnitCategory.LENGTH, 100.0, "cm", "m"), 0.0001)
    assertEquals(32.0, DailyUtilityEngine.convert(UnitCategory.TEMPERATURE, 0.0, "c", "f"), 0.0001)
    assertEquals(1024.0, DailyUtilityEngine.convert(UnitCategory.STORAGE, 1.0, "gib", "mib"), 0.0001)

    val split = DailyUtilityEngine.splitBill(subtotal = 100.0, people = 3)
    assertEquals(33.33, split.perPersonBase, 0.001)
    assertEquals(1, split.remainderCents)
  }

  @Test
  fun `QrCodeEngine encodes and decodes QR bitmap offline`() {
    val payload = "https://github.com/xialiacheng/TactKit"
    val bmp = QrCodeEngine.generateQrBitmap(payload, sizePx = 400)
    assertEquals(400, bmp.width)
    val decoded = QrCodeEngine.decodeBitmap(bmp)
    assertEquals(payload, decoded)
  }

  @Test
  fun `LanShareServer serves downloads and accepts multipart uploads cleanly`() {
    val uploadDir = File.createTempFile("lan_up_", "").apply {
      delete()
      mkdirs()
      deleteOnExit()
    }
    val stagedFile = File.createTempFile("lan_out_", ".txt").apply {
      writeText("Hello LAN Share!")
      deleteOnExit()
    }
    val latch = CountDownLatch(1)
    var receivedBytes: ByteArray? = null
    val server =
      LanShareServer(
        uploadDir = uploadDir,
        onFileReceived = { rec ->
          receivedBytes = rec.savedFile.readBytes()
          latch.countDown()
        },
      )
    val shared = LanSharedOutFile(id = "test1", displayName = "hello.txt", byteSize = stagedFile.length(), stagedFile = stagedFile)
    server.sharedOutFiles.add(shared)

    val port = server.start(0)
    assertTrue(port > 0)
    try {
      // GET /
      val homeConn = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
      assertEquals(200, homeConn.responseCode)
      val html = homeConn.inputStream.bufferedReader().readText()
      assertTrue(html.contains("hello.txt"))

      // GET /download/test1
      val dlConn = URL("http://127.0.0.1:$port/download/test1").openConnection() as HttpURLConnection
      assertEquals(200, dlConn.responseCode)
      assertEquals("Hello LAN Share!", dlConn.inputStream.bufferedReader().readText())

      // POST /upload
      val boundary = "----TactKitBoundary123"
      val upConn =
        (URL("http://127.0.0.1:$port/upload").openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          doOutput = true
          setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
      val payloadText = "Uploaded Content 2026"
      val body =
        ("--$boundary\r\n" +
          "Content-Disposition: form-data; name=\"file\"; filename=\"note.txt\"\r\n" +
          "Content-Type: text/plain\r\n\r\n" +
          payloadText +
          "\r\n--$boundary--\r\n").toByteArray(Charsets.UTF_8)
      upConn.outputStream.use { it.write(body) }
      assertEquals(200, upConn.responseCode)
      assertTrue(latch.await(3, TimeUnit.SECONDS))
      assertEquals(payloadText, receivedBytes?.toString(Charsets.UTF_8))
    } finally {
      server.stop()
      uploadDir.deleteRecursively()
    }
  }

  @Test
  fun `SettingsRepository persists counter value, step, and label`() = runTest {
    val tempFile = File.createTempFile("tactkit_counter_", ".preferences_pb").apply { deleteOnExit() }
    val dataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
    val repo = SettingsRepository(dataStore)

    repo.setCounterStep(5)
    repo.setCounterLabel("俯卧撑")
    repo.stepCounter(1)
    repo.stepCounter(1)
    repo.stepCounter(-1)

    val prefs = repo.preferencesFlow.first()
    assertEquals(5L, prefs.counterValue)
    assertEquals(5, prefs.counterStep)
    assertEquals("俯卧撑", prefs.counterLabel)

    repo.setCounterValue(0L)
    assertEquals(0L, repo.preferencesFlow.first().counterValue)
  }
}
