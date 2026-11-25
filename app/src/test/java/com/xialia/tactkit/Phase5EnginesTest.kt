package com.xialia.tactkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.xialia.tactkit.domain.ToolCatalog
import com.xialia.tactkit.domain.ToolCategory
import com.xialia.tactkit.engine.DocScannerEngine
import com.xialia.tactkit.engine.LanReceivedText
import com.xialia.tactkit.engine.LanShareServer
import com.xialia.tactkit.engine.LanSharedOutFile
import com.xialia.tactkit.engine.MorseTorchEngine
import com.xialia.tactkit.engine.NetworkDiagEngine
import com.xialia.tactkit.engine.NormalizedQuad
import com.xialia.tactkit.engine.PdfEngine
import com.xialia.tactkit.engine.PdfPrepareResult
import com.xialia.tactkit.engine.ScanFilter
import com.xialia.tactkit.engine.SystemHardwareEngine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase5EnginesTest {

  @Test
  fun `ToolCatalog has 5 categories, unique IDs, and concise subtitles`() {
    assertEquals(5, ToolCategory.entries.size)
    val ids = ToolCatalog.allTools.map { it.id }
    assertEquals(ids.size, ids.distinct().size)
    assertTrue(ToolCatalog.allTools.all { it.isAvailable })
    ToolCatalog.allTools.forEach { tool ->
      assertTrue(
        "Subtitle '${tool.subtitle}' for ${tool.id} exceeds 12 chars (${tool.subtitle.length})",
        tool.subtitle.length <= 12,
      )
    }
    listOf("doc_scanner", "decibel_meter", "light_lux", "physical_ruler", "morse_torch", "battery_hardware", "net_diag", "dns_ssl", "wifi_channel", "led_clock", "image_long")
      .forEach { id ->
        assertNotNull("Missing tool $id", ToolCatalog.findById(id))
      }
  }

  @Test
  fun `DocScannerEngine warps perspective and builds multi-page PDF`() = runTest {
    val src = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
    val quad = NormalizedQuad(
      topLeft = 0.1f to 0.1f,
      topRight = 0.9f to 0.12f,
      bottomRight = 0.88f to 0.9f,
      bottomLeft = 0.12f to 0.88f,
    )
    val warped = DocScannerEngine.warpPerspectiveAndFilter(src, quad, ScanFilter.ENHANCED)
    assertTrue(warped.width >= 240)
    assertTrue(warped.height >= 240)

    val outPdf = File.createTempFile("scan_multi_", ".pdf").apply { deleteOnExit() }
    DocScannerEngine.buildMultiPagePdfToFile(listOf(warped, warped), outPdf)
    assertTrue(outPdf.exists() && outPdf.length() > 200L)
  }

  @Test
  fun `PdfEngine unlocks password-protected PDF to temp file without modifying original`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    PDFBoxResourceLoader.init(context)

    val encryptedFile = File.createTempFile("locked_src_", ".pdf").apply { deleteOnExit() }
    PDDocument().use { doc ->
      doc.addPage(PDPage())
      val policy = StandardProtectionPolicy("owner123", "user123", AccessPermission()).apply {
        encryptionKeyLength = 128
      }
      doc.protect(policy)
      doc.save(encryptedFile)
    }
    val originalBytesBefore = encryptedFile.readBytes()
    val uri = Uri.fromFile(encryptedFile)

    val promptRes = PdfEngine.prepareReadablePdf(context, uri, password = null)
    assertTrue("Expected PasswordRequired, got $promptRes", promptRes is PdfPrepareResult.PasswordRequired)

    val wrongRes = PdfEngine.prepareReadablePdf(context, uri, password = "badpassword")
    assertTrue(wrongRes is PdfPrepareResult.WrongPassword)

    val okRes = PdfEngine.prepareReadablePdf(context, uri, password = "user123")
    assertTrue("Expected Ready, got $okRes", okRes is PdfPrepareResult.Ready)
    val ready = okRes as PdfPrepareResult.Ready
    val unlockedFile = ready.tempFile
    assertNotNull(unlockedFile)
    assertTrue(unlockedFile!!.exists())
    assertEquals(1, ready.pageCount)
    // Verify temp file is decrypted while original file remains untouched
    PDDocument.load(unlockedFile).use { unlocked ->
      assertEquals(1, unlocked.numberOfPages)
    }
    assertArrayEquals(originalBytesBefore, encryptedFile.readBytes())
    unlockedFile.delete()
  }

  @Test
  fun `LanShareServer supports clipboard text POST and 128KB file roundtrip`() {
    val uploadDir = File.createTempFile("lan5_up_", "").apply {
      delete()
      mkdirs()
      deleteOnExit()
    }
    val payloadBytes = ByteArray(140 * 1024) { idx -> (idx % 251).toByte() }
    val outFile = File.createTempFile("lan5_big_", ".bin").apply {
      writeBytes(payloadBytes)
      deleteOnExit()
    }

    val textLatch = CountDownLatch(1)
    var receivedText: LanReceivedText? = null
    val server =
      LanShareServer(
        uploadDir = uploadDir,
        onFileReceived = {},
        onTextReceived = { txt ->
          receivedText = txt
          textLatch.countDown()
        },
      )
    server.sharedOutFiles.add(
      LanSharedOutFile(id = "big1", displayName = "big.bin", byteSize = outFile.length(), stagedFile = outFile)
    )

    val port = server.start(0)
    assertTrue(port > 0)
    try {
      // 140KB download integrity check
      val dlConn = URL("http://127.0.0.1:$port/download/big1").openConnection() as HttpURLConnection
      assertEquals(200, dlConn.responseCode)
      val downloaded = dlConn.inputStream.use { it.readBytes() }
      assertArrayEquals(payloadBytes, downloaded)

      // POST /clipboard
      val clipConn =
        (URL("http://127.0.0.1:$port/clipboard").openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          doOutput = true
          setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
      val formBody = "text=" + URLEncoder.encode("简工坊 LAN 剪贴板测试 #1", "UTF-8")
      clipConn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
      assertEquals(200, clipConn.responseCode)
      assertTrue(textLatch.await(3, TimeUnit.SECONDS))
      assertEquals("简工坊 LAN 剪贴板测试 #1", receivedText?.content)
    } finally {
      server.stop()
      uploadDir.deleteRecursively()
    }
  }

  @Test
  fun `MorseTorchEngine, NetworkDiagEngine, and SystemHardwareEngine compute accurately`() {
    assertEquals("... --- ...", MorseTorchEngine.encodeTextToMorse("sos"))
    assertEquals(".... .. / ... --- ...", MorseTorchEngine.encodeTextToMorse("HI SOS"))

    assertEquals("2.4 GHz" to 1, NetworkDiagEngine.freqMhzToChannel(2412))
    assertEquals("2.4 GHz" to 6, NetworkDiagEngine.freqMhzToChannel(2437))
    assertEquals("2.4 GHz" to 14, NetworkDiagEngine.freqMhzToChannel(2484))
    assertEquals("5 GHz" to 36, NetworkDiagEngine.freqMhzToChannel(5180))
    assertEquals("5 GHz" to 149, NetworkDiagEngine.freqMhzToChannel(5745))

    val (maFromUa, wattsUa) = SystemHardwareEngine.estimateWatts(2_000_000L, 4200)
    assertEquals(2000f, maFromUa, 0.1f)
    assertEquals(8.4f, wattsUa, 0.05f)

    val (maFromMa, wattsMa) = SystemHardwareEngine.estimateWatts(1500L, 4000)
    assertEquals(1500f, maFromMa, 0.1f)
    assertEquals(6.0f, wattsMa, 0.05f)
  }
}
