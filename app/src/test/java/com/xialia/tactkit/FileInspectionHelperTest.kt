package com.xialia.tactkit

import com.xialia.tactkit.util.FileInspectionHelper
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileInspectionHelperTest {

  @Test
  fun `computeSha256 matches known digest for empty and multi-chunk streams`() {
    val emptyDigest = FileInspectionHelper.computeSha256(ByteArrayInputStream(ByteArray(0)))
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      emptyDigest,
    )

    val text = "hello tactkit"
    assertEquals(
      FileInspectionHelper.computeTextSha256(text),
      FileInspectionHelper.computeSha256(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))),
    )

    // 20 KB stream spans multiple 8 KB buffers
    val largePayload = ByteArray(20 * 1024) { (it % 251).toByte() }
    val digestA = FileInspectionHelper.computeSha256(ByteArrayInputStream(largePayload))
    val digestB = FileInspectionHelper.computeSha256(ByteArrayInputStream(largePayload))
    assertEquals(64, digestA.length)
    assertEquals(digestA, digestB)
  }

  @Test
  fun `formatBytes formats sizes cleanly`() {
    assertEquals("未知大小", FileInspectionHelper.formatBytes(-1))
    assertEquals("512 B", FileInspectionHelper.formatBytes(512))
    assertEquals("1.5 KB", FileInspectionHelper.formatBytes(1536))
    assertEquals("2.00 MB", FileInspectionHelper.formatBytes(2L * 1024 * 1024))
  }

  @Test
  fun `isTextReadable detects text mimes and code extensions`() {
    assertTrue(FileInspectionHelper.isTextReadable("text/markdown", "README.md"))
    assertTrue(FileInspectionHelper.isTextReadable("application/json", "config.json"))
    assertTrue(FileInspectionHelper.isTextReadable("application/octet-stream", "MainActivity.kt"))
    assertFalse(FileInspectionHelper.isTextReadable("application/pdf", "document.pdf"))
    assertFalse(FileInspectionHelper.isTextReadable("image/png", "photo.png"))
  }

  @Test
  fun `readTextPreview truncates cleanly beyond maxBytes`() {
    val payload = "A".repeat(128).toByteArray(Charsets.UTF_8)
    val (preview, truncated) =
      FileInspectionHelper.readTextPreview(ByteArrayInputStream(payload), maxBytes = 64)
    assertEquals(64, preview.length)
    assertTrue(truncated)

    val (fullPreview, notTruncated) =
      FileInspectionHelper.readTextPreview(ByteArrayInputStream(payload), maxBytes = 256)
    assertEquals(128, fullPreview.length)
    assertFalse(notTruncated)
  }
}
