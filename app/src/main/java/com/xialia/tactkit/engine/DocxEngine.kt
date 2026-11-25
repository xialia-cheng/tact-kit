package com.xialia.tactkit.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument

data class DocxParagraphItem(
  val index: Int,
  val text: String,
  val isHeading: Boolean,
  val styleName: String?,
  val isBold: Boolean,
)

data class DocxDocumentData(
  val title: String,
  val paragraphs: List<DocxParagraphItem>,
  val tableCount: Int,
  val fullText: String,
)

object DocxEngine {

  suspend fun readDocx(context: Context, uri: Uri): DocxDocumentData =
    withContext(Dispatchers.IO) {
      val name = resolveDisplayName(context, uri) ?: "document.docx"
      val bytes =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("无法读取选中的 .docx 文件")
      readDocxBytes(bytes, name)
    }

  fun readDocxBytes(bytes: ByteArray, title: String = "document.docx"): DocxDocumentData {
    ByteArrayInputStream(bytes).use { bis ->
      XWPFDocument(bis).use { doc ->
        val items =
          doc.paragraphs.mapIndexed { idx, p ->
            val style = p.style?.trim()
            val text = p.text.orEmpty()
            val isHeading =
              style != null &&
                (style.startsWith("Heading", ignoreCase = true) ||
                  style.startsWith("Title", ignoreCase = true) ||
                  style.contains("标题"))
            val isBold = p.runs.any { it.isBold }
            DocxParagraphItem(
              index = idx,
              text = text,
              isHeading = isHeading,
              styleName = style,
              isBold = isBold,
            )
          }

        val fullText = buildString {
          items.forEach { item ->
            if (item.text.isNotBlank()) {
              if (item.isHeading) {
                append("## ").append(item.text).append("\n\n")
              } else {
                append(item.text).append("\n\n")
              }
            }
          }
          if (doc.tables.isNotEmpty()) {
            append("---\n")
            doc.tables.forEachIndexed { tIdx, table ->
              append("\n[表格 ${tIdx + 1}]\n")
              table.rows.forEach { row ->
                val cells = row.tableCells.joinToString(" | ") { it.text.orEmpty().replace('\n', ' ').trim() }
                append("| ").append(cells).append(" |\n")
              }
            }
          }
        }.trim()

        return DocxDocumentData(
          title = title,
          paragraphs = items,
          tableCount = doc.tables.size,
          fullText = fullText,
        )
      }
    }
  }

  fun extractQuickLookText(inputStream: InputStream, maxChars: Int = 28_000): String {
    val bytes = inputStream.readBytes()
    val data = readDocxBytes(bytes, "QuickLook.docx")
    val preview = data.fullText
    return if (preview.length > maxChars) {
      preview.take(maxChars) + "\n\n...（正文过长，Quick Look 仅展示前 $maxChars 字符）"
    } else {
      preview.ifBlank { "（空 .docx 文档，无正文文本）" }
    }
  }

  suspend fun saveEditedDocx(
    context: Context,
    sourceUri: Uri?,
    outputUri: Uri,
    editedParagraphs: List<String>,
  ) =
    withContext(Dispatchers.IO) {
      val sourceBytes =
        if (sourceUri != null) {
          context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
        } else {
          null
        }
      val outBytes = applyParagraphEditsToBytes(sourceBytes, editedParagraphs)
      context.contentResolver.openOutputStream(outputUri, "wt")?.use { os ->
        os.write(outBytes)
        os.flush()
      } ?: error("无法写入目标 .docx 文件")
    }

  fun applyParagraphEditsToBytes(
    sourceBytes: ByteArray?,
    editedParagraphs: List<String>,
  ): ByteArray {
    val doc =
      if (sourceBytes != null && sourceBytes.isNotEmpty()) {
        XWPFDocument(ByteArrayInputStream(sourceBytes))
      } else {
        XWPFDocument()
      }

    doc.use { document ->
      val existingCount = document.paragraphs.size
      val targetTexts = editedParagraphs.ifEmpty { listOf("") }

      for (i in 0 until minOf(existingCount, targetTexts.size)) {
        val paragraph = document.paragraphs[i]
        val firstRunFontSize = paragraph.runs.firstOrNull()?.fontSizeAsDouble
        val firstRunBold = paragraph.runs.firstOrNull()?.isBold ?: false
        val firstRunColor = paragraph.runs.firstOrNull()?.color
        val firstRunFont = paragraph.runs.firstOrNull()?.fontFamily

        for (r in paragraph.runs.size - 1 downTo 0) {
          paragraph.removeRun(r)
        }
        val run = paragraph.createRun()
        run.setText(targetTexts[i])
        if (firstRunFontSize != null && firstRunFontSize > 0) {
          run.setFontSize(firstRunFontSize)
        }
        if (firstRunBold) run.isBold = true
        if (!firstRunColor.isNullOrBlank()) run.color = firstRunColor
        if (!firstRunFont.isNullOrBlank()) run.fontFamily = firstRunFont
      }

      if (targetTexts.size > existingCount) {
        for (i in existingCount until targetTexts.size) {
          val newP = document.createParagraph()
          newP.createRun().setText(targetTexts[i])
        }
      } else if (existingCount > targetTexts.size) {
        for (i in existingCount - 1 downTo targetTexts.size) {
          val p = document.paragraphs[i]
          val pos = document.getPosOfParagraph(p)
          if (pos >= 0) {
            document.removeBodyElement(pos)
          }
        }
      }

      val baos = ByteArrayOutputStream()
      document.write(baos)
      return baos.toByteArray()
    }
  }

  private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
      }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
  }
}
