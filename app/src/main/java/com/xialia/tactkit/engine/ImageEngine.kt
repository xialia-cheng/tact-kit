package com.xialia.tactkit.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SampledPixelColor(
  val argb: Int,
  val hex: String,
  val r: Int,
  val g: Int,
  val b: Int,
)

object ImageEngine {

  private fun openUriStream(context: Context, uri: Uri): java.io.InputStream? {
    return if (uri.scheme == "file" && !uri.path.isNullOrEmpty()) {
      java.io.File(uri.path!!).takeIf { it.exists() }?.inputStream()
    } else {
      context.contentResolver.openInputStream(uri)
    }
  }

  suspend fun loadOrientedBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int = 2048,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val orientation =
        openUriStream(context, uri)?.use { stream ->
          runCatching {
            ExifInterface(stream).getAttributeInt(
              ExifInterface.TAG_ORIENTATION,
              ExifInterface.ORIENTATION_NORMAL,
            )
          }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      openUriStream(context, uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
      }
      var sample = 1
      while (
        bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension
      ) {
        sample *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sample }
      val decoded =
        openUriStream(context, uri)?.use {
          BitmapFactory.decodeStream(it, null, opts)
        } ?: return@withContext null

      val matrix = Matrix()
      when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return@withContext decoded
      }
      val rotated =
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
      if (rotated !== decoded) decoded.recycle()
      rotated
    }

  suspend fun readExifSummary(context: Context, uri: Uri): List<Pair<String, String>> =
    withContext(Dispatchers.IO) {
      val tags =
        listOf(
          ExifInterface.TAG_MAKE to "设备品牌",
          ExifInterface.TAG_MODEL to "设备型号",
          ExifInterface.TAG_DATETIME_ORIGINAL to "拍摄时间",
          ExifInterface.TAG_GPS_LATITUDE to "GPS 纬度",
          ExifInterface.TAG_GPS_LONGITUDE to "GPS 经度",
          ExifInterface.TAG_SOFTWARE to "处理软件",
        )
      openUriStream(context, uri)?.use { stream ->
        val exif = ExifInterface(stream)
        tags.mapNotNull { (tag, label) ->
          exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { label to it }
        }
      } ?: emptyList()
    }

  fun rotate90(source: Bitmap): Bitmap {
    val m = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
  }

  fun cropNormalized(
    source: Bitmap,
    leftNorm: Float,
    topNorm: Float,
    rightNorm: Float,
    bottomNorm: Float,
  ): Bitmap {
    val safeL = leftNorm.coerceIn(0f, 0.94f)
    val safeT = topNorm.coerceIn(0f, 0.94f)
    val safeR = rightNorm.coerceIn(safeL + 0.05f, 1f)
    val safeB = bottomNorm.coerceIn(safeT + 0.05f, 1f)
    val l = (safeL * source.width).toInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val t = (safeT * source.height).toInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val maxW = (source.width - l).coerceAtLeast(1)
    val maxH = (source.height - t).coerceAtLeast(1)
    val w = ((safeR * source.width).toInt() - l).coerceIn(minOf(16, maxW), maxW)
    val h = ((safeB * source.height).toInt() - t).coerceIn(minOf(16, maxH), maxH)
    return Bitmap.createBitmap(source, l, t, w, h)
  }

  fun applyMosaic(
    source: Bitmap,
    leftNorm: Float,
    topNorm: Float,
    rightNorm: Float,
    bottomNorm: Float,
    blockSizePx: Int = 24,
  ): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val l = (min(leftNorm, rightNorm).coerceIn(0f, 1f) * out.width).toInt()
    val t = (min(topNorm, bottomNorm).coerceIn(0f, 1f) * out.height).toInt()
    val r = (max(leftNorm, rightNorm).coerceIn(0f, 1f) * out.width).toInt()
    val b = (max(topNorm, bottomNorm).coerceIn(0f, 1f) * out.height).toInt()
    if (r - l < 4 || b - t < 4) return out

    val block = blockSizePx.coerceIn(8, 72)
    val canvas = Canvas(out)
    val paint = Paint()
    var y = t
    while (y < b) {
      var x = l
      val bh = min(block, b - y)
      while (x < r) {
        val bw = min(block, r - x)
        val cx = (x + bw / 2).coerceIn(0, out.width - 1)
        val cy = (y + bh / 2).coerceIn(0, out.height - 1)
        paint.color = out.getPixel(cx, cy)
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + bw).toFloat(), (y + bh).toFloat(), paint)
        x += block
      }
      y += block
    }
    return out
  }

  fun applyTextWatermark(
    source: Bitmap,
    watermarkText: String,
    alpha: Int = 72,
  ): Bitmap {
    val text = watermarkText.trim()
    if (text.isEmpty()) return source.copy(Bitmap.Config.ARGB_8888, true)
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val textSize = (min(out.width, out.height) * 0.045f).coerceIn(22f, 64f)
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(alpha.coerceIn(24, 180), 255, 255, 255)
        this.textSize = textSize
        setShadowLayer(4f, 1f, 1f, Color.argb(alpha.coerceIn(24, 150), 0, 0, 0))
      }

    val textWidth = paint.measureText(text).coerceAtLeast(80f)
    val stepX = (textWidth * 1.7f).toInt()
    val stepY = (textSize * 3.8f).toInt()

    canvas.save()
    canvas.rotate(-25f, out.width / 2f, out.height / 2f)
    var y = -out.height / 2
    while (y < out.height * 1.5f) {
      var x = -out.width / 2
      while (x < out.width * 1.5f) {
        canvas.drawText(text, x.toFloat(), y.toFloat(), paint)
        x += stepX
      }
      y += stepY
    }
    canvas.restore()
    return out
  }

  fun stitchVertical(bitmaps: List<Bitmap>): Bitmap? {
    if (bitmaps.isEmpty()) return null
    val targetW = bitmaps.maxOf { it.width }.coerceIn(320, 1440)
    val scaled =
      bitmaps.map { bmp ->
        if (bmp.width == targetW) {
          bmp
        } else {
          val h = (bmp.height * (targetW.toFloat() / bmp.width.coerceAtLeast(1))).toInt().coerceAtLeast(1)
          Bitmap.createScaledBitmap(bmp, targetW, h, true)
        }
      }
    val totalH = scaled.sumOf { it.height }.coerceAtMost(8192)
    val out = Bitmap.createBitmap(targetW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(Color.WHITE)
    var offsetY = 0f
    for (bmp in scaled) {
      if (offsetY >= totalH) break
      canvas.drawBitmap(bmp, 0f, offsetY, null)
      offsetY += bmp.height
    }
    return out
  }

  fun samplePixelColor(bitmap: Bitmap, normX: Float, normY: Float): SampledPixelColor {
    val px = (normX.coerceIn(0f, 1f) * (bitmap.width - 1)).toInt().coerceIn(0, bitmap.width - 1)
    val py = (normY.coerceIn(0f, 1f) * (bitmap.height - 1)).toInt().coerceIn(0, bitmap.height - 1)
    val argb = bitmap.getPixel(px, py)
    val r = Color.red(argb)
    val g = Color.green(argb)
    val b = Color.blue(argb)
    val hex = String.format("#%02X%02X%02X", r, g, b)
    return SampledPixelColor(argb = argb, hex = hex, r = r, g = g, b = b)
  }

  fun compressToTargetKbBytes(source: Bitmap, targetKb: Int): ByteArray {
    val maxBytes = targetKb.coerceAtLeast(15) * 1024
    var current = source
    val baos = ByteArrayOutputStream()

    repeat(3) {
      var low = 12
      var high = 95
      var bestBytes: ByteArray? = null
      while (low <= high) {
        val mid = (low + high) / 2
        baos.reset()
        current.compress(Bitmap.CompressFormat.JPEG, mid, baos)
        val data = baos.toByteArray()
        if (data.size <= maxBytes) {
          bestBytes = data
          low = mid + 1
        } else {
          high = mid - 1
        }
      }
      if (bestBytes != null) return bestBytes

      val nextW = (current.width * 0.72f).toInt().coerceAtLeast(160)
      val nextH = (current.height * 0.72f).toInt().coerceAtLeast(160)
      current = Bitmap.createScaledBitmap(current, nextW, nextH, true)
    }

    baos.reset()
    current.compress(Bitmap.CompressFormat.JPEG, 25, baos)
    return baos.toByteArray()
  }

  suspend fun saveCleanBitmap(
    context: Context,
    bitmap: Bitmap,
    outputUri: Uri,
    targetKb: Int? = null,
  ) =
    withContext(Dispatchers.IO) {
      val bytes =
        if (targetKb != null) {
          compressToTargetKbBytes(bitmap, targetKb)
        } else {
          ByteArrayOutputStream().use { baos ->
            // Encoding raw pixels drops all EXIF GPS, camera, and software headers cleanly.
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            baos.toByteArray()
          }
        }
      context.contentResolver.openOutputStream(outputUri)?.use { out ->
        out.write(bytes)
      } ?: error("无法保存处理后的图片")
    }
}
