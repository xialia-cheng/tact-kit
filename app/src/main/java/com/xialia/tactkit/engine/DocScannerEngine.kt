package com.xialia.tactkit.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ScanFilter(val label: String) {
  ORIGINAL("原图"),
  ENHANCED("文档增强"),
  BW_SCAN("黑白扫描"),
}

data class NormalizedQuad(
  val topLeft: Pair<Float, Float> = 0.08f to 0.08f,
  val topRight: Pair<Float, Float> = 0.92f to 0.08f,
  val bottomRight: Pair<Float, Float> = 0.92f to 0.92f,
  val bottomLeft: Pair<Float, Float> = 0.08f to 0.92f,
) {
  fun corners(): List<Pair<Float, Float>> = listOf(topLeft, topRight, bottomRight, bottomLeft)

  fun withUpdatedCorner(index: Int, x: Float, y: Float): NormalizedQuad {
    val cx = x.coerceIn(0.01f, 0.99f)
    val cy = y.coerceIn(0.01f, 0.99f)
    return when (index) {
      0 -> copy(topLeft = cx to cy)
      1 -> copy(topRight = cx to cy)
      2 -> copy(bottomRight = cx to cy)
      else -> copy(bottomLeft = cx to cy)
    }
  }
}

object DocScannerEngine {

  /**
   * Computes output dimensions for a warped quad and performs perspective rectification using
   * Android's native [Matrix.setPolyToPoly] + optional document contrast/BW filter.
   */
  fun warpPerspectiveAndFilter(
    source: Bitmap,
    quad: NormalizedQuad,
    filter: ScanFilter,
  ): Bitmap {
    val w = source.width.toFloat()
    val h = source.height.toFloat()

    val tlX = quad.topLeft.first * w
    val tlY = quad.topLeft.second * h
    val trX = quad.topRight.first * w
    val trY = quad.topRight.second * h
    val brX = quad.bottomRight.first * w
    val brY = quad.bottomRight.second * h
    val blX = quad.bottomLeft.first * w
    val blY = quad.bottomLeft.second * h

    val topW = hypot((trX - tlX).toDouble(), (trY - tlY).toDouble())
    val bottomW = hypot((brX - blX).toDouble(), (brY - blY).toDouble())
    val leftH = hypot((blX - tlX).toDouble(), (blY - tlY).toDouble())
    val rightH = hypot((brX - trX).toDouble(), (brY - trY).toDouble())

    val dstW = maxOf(topW, bottomW).roundToInt().coerceIn(240, 2200)
    val dstH = maxOf(leftH, rightH).roundToInt().coerceIn(240, 3000)

    val srcPoly = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
    val dstPoly = floatArrayOf(
      0f, 0f,
      dstW.toFloat(), 0f,
      dstW.toFloat(), dstH.toFloat(),
      0f, dstH.toFloat(),
    )

    val matrix = Matrix()
    val mapped = matrix.setPolyToPoly(srcPoly, 0, dstPoly, 0, 4)

    val warped = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(warped)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    applyScanFilter(paint, filter)
    if (mapped) {
      canvas.drawBitmap(source, matrix, paint)
    } else {
      canvas.drawBitmap(source, null, Rect(0, 0, dstW, dstH), paint)
    }
    return warped
  }

  private fun applyScanFilter(paint: Paint, filter: ScanFilter) {
    when (filter) {
      ScanFilter.ORIGINAL -> Unit
      ScanFilter.ENHANCED -> {
        val contrast = 1.38f
        val brightness = -28f
        val cm = ColorMatrix(
          floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
          )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
      }
      ScanFilter.BW_SCAN -> {
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 2.6f
        val translate = -180f
        val boost = ColorMatrix(
          floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
          )
        )
        gray.postConcat(boost)
        paint.colorFilter = ColorMatrixColorFilter(gray)
      }
    }
  }

  suspend fun buildMultiPagePdfToFile(
    pages: List<Bitmap>,
    destFile: File,
  ): File =
    withContext(Dispatchers.IO) {
      require(pages.isNotEmpty()) { "请至少拍摄或添加一页文档" }
      destFile.parentFile?.mkdirs()
      try {
        val document = PdfDocument()
        try {
          pages.forEachIndexed { idx, bmp ->
            val pageW = 1240
            val pageH = (pageW * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))).roundToInt()
              .coerceIn(400, 3200)
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bmp, null, Rect(0, 0, pageW, pageH), null)
            document.finishPage(page)
          }
          FileOutputStream(destFile).use { out -> document.writeTo(out) }
          destFile
        } finally {
          runCatching { document.close() }
        }
      } catch (_: IllegalStateException) {
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
          pages.forEach { bmp ->
            val pageW = 595f
            val pageH = pageW * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(
              com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pageW, pageH)
            )
            doc.addPage(page)
            val pdImg = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, bmp, 0.90f)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
              cs.drawImage(pdImg, 0f, 0f, pageW, pageH)
            }
          }
          FileOutputStream(destFile).use { out -> doc.save(out) }
        }
        destFile
      }
    }

  suspend fun copyFileToSafUri(context: Context, srcFile: File, outUri: Uri) =
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(outUri)?.use { out ->
        srcFile.inputStream().use { input -> input.copyTo(out) }
      } ?: error("无法写入目标 PDF 文件")
    }
}
