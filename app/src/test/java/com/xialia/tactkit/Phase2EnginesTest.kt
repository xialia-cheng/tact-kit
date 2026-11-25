package com.xialia.tactkit

import android.graphics.Bitmap
import android.graphics.Color
import com.xialia.tactkit.data.repository.AiHubRepository
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.engine.MediaEngine
import com.xialia.tactkit.engine.VoiceMemoManager
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase2EnginesTest {

  @Test
  fun `parseChecklistDraft parses fenced JSON, raw JSON, and plain bullets`() {
    val fenced =
      """
      ```json
      {"title": "发版准备", "items": ["更新版本号", "运行单测", "校验 APK"]}
      ```
      """.trimIndent()
    val draft1 = AiHubRepository.parseChecklistDraft(fenced)
    assertEquals("发版准备", draft1.title)
    assertEquals(listOf("更新版本号", "运行单测", "校验 APK"), draft1.items)

    val raw = """{"title": "购物清单", "items": ["咖啡豆", "燕麦奶"]}"""
    val draft2 = AiHubRepository.parseChecklistDraft(raw)
    assertEquals("购物清单", draft2.title)
    assertEquals(2, draft2.items.size)

    val bullets = "- 检查 PDF\n* 清理 EXIF\n[ ] 备份配置"
    val draft3 = AiHubRepository.parseChecklistDraft(bullets)
    assertEquals(listOf("检查 PDF", "清理 EXIF", "备份配置"), draft3.items)
  }

  @Test
  fun `ImageEngine transforms, samples pixel color, stitches, and compresses to target KB`() {
    val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.rgb(0x33, 0x66, 0x99))

    val rotated = ImageEngine.rotate90(bmp)
    assertEquals(200, rotated.width)
    assertEquals(400, rotated.height)

    val cropped = ImageEngine.cropNormalized(bmp, 0.1f, 0.1f, 0.6f, 0.6f)
    assertEquals(200, cropped.width)
    assertEquals(100, cropped.height)

    val sampled = ImageEngine.samplePixelColor(bmp, 0.5f, 0.5f)
    assertEquals("#336699", sampled.hex)
    assertEquals(0x33, sampled.r)
    assertEquals(0x66, sampled.g)
    assertEquals(0x99, sampled.b)

    val mosaic = ImageEngine.applyMosaic(bmp, 0.2f, 0.2f, 0.8f, 0.8f, blockSizePx = 16)
    assertEquals(400, mosaic.width)

    val watermarked = ImageEngine.applyTextWatermark(bmp, "TACTKIT")
    assertEquals(400, watermarked.width)

    val stitched = ImageEngine.stitchVertical(listOf(bmp, rotated))
    assertNotNull(stitched)
    assertTrue(stitched!!.height > bmp.height)

    val compressed = ImageEngine.compressToTargetKbBytes(bmp, targetKb = 25)
    assertTrue(compressed.isNotEmpty())
    assertTrue(compressed.size <= 25 * 1024)
  }

  @Test
  fun `VoiceMemoManager formats mm_ss duration accurately`() {
    assertEquals("00:00", VoiceMemoManager.formatDuration(0L))
    assertEquals("00:09", VoiceMemoManager.formatDuration(9_400L))
    assertEquals("01:05", VoiceMemoManager.formatDuration(65_000L))
    assertEquals("12:30", VoiceMemoManager.formatDuration(750_000L))
  }

  @Test
  fun `CommonMark parses GFM tables and task list items`() {
    val parser =
      Parser.builder()
        .extensions(listOf(TablesExtension.create(), TaskListItemsExtension.create()))
        .build()

    val md =
      """
      # TactKit
      - [x] PDF 批注
      - [ ] EXIF 剥离

      | 模块 | 状态 |
      | :--- | :--- |
      | Markdown | 完成 |
      """.trimIndent()

    val doc = parser.parse(md)
    var sawTable = false
    var sawTaskMarker = false

    doc.accept(
      object : AbstractVisitor() {
        override fun visit(customBlock: CustomBlock) {
          if (customBlock is TableBlock) sawTable = true
          super.visit(customBlock)
        }

        override fun visit(customNode: CustomNode) {
          if (customNode is TaskListItemMarker && customNode.isChecked) sawTaskMarker = true
          super.visit(customNode)
        }
      }
    )

    assertTrue(sawTable)
    assertTrue(sawTaskMarker)
  }

  @Test
  fun `AppUpdateRepository compares semantic version tags accurately`() {
    assertTrue(AppUpdateRepository.isNewerVersion("0.2.0", "v0.2.1"))
    assertTrue(AppUpdateRepository.isNewerVersion("0.2.0", "v1.0.0"))
    assertTrue(AppUpdateRepository.isNewerVersion("0.2", "0.2.1"))
    assertFalse(AppUpdateRepository.isNewerVersion("0.2.0", "v0.2.0"))
    assertFalse(AppUpdateRepository.isNewerVersion("0.2.0", "v0.1.9"))
  }

  @Test
  fun `MediaEngine formats frame millisecond timestamps accurately`() {
    assertEquals("00:00.000", MediaEngine.formatFrameTimestampMs(0L))
    assertEquals("00:05.042", MediaEngine.formatFrameTimestampMs(5_042L))
    assertEquals("02:03.456", MediaEngine.formatFrameTimestampMs(123_456L))
  }
}
