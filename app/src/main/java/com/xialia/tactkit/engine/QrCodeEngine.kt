package com.xialia.tactkit.engine

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeEngine {

  fun generateQrBitmap(
    content: String,
    sizePx: Int = 720,
    darkColor: Int = 0xFF111318.toInt(),
    lightColor: Int = 0xFFFFFFFF.toInt(),
  ): Bitmap {
    require(content.isNotBlank()) { "二维码内容不能为空" }
    val hints =
      mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
      )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
      val rowOffset = y * w
      for (x in 0 until w) {
        pixels[rowOffset + x] = if (matrix[x, y]) darkColor else lightColor
      }
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, w, 0, 0, w, h)
    }
  }

  fun decodeBitmap(bitmap: Bitmap): String? {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val source = RGBLuminanceSource(w, h, pixels)
    return decodeLuminanceSource(source) ?: decodeLuminanceSource(source.invert())
  }

  fun decodeYuvPlane(
    yPlane: ByteArray,
    width: Int,
    height: Int,
    rotationDegrees: Int = 0,
  ): String? {
    val (rotatedData, rotW, rotH) =
      when (rotationDegrees) {
        90 -> rotateY90(yPlane, width, height) toPair (height to width)
        180 -> yPlane.reversedArray() toPair (width to height)
        270 -> rotateY270(yPlane, width, height) toPair (height to width)
        else -> yPlane toPair (width to height)
      }
    val source = PlanarYUVLuminanceSource(rotatedData, rotW, rotH, 0, 0, rotW, rotH, false)
    return decodeLuminanceSource(source) ?: decodeLuminanceSource(source.invert())
  }

  private fun decodeLuminanceSource(source: com.google.zxing.LuminanceSource): String? {
    val reader =
      MultiFormatReader().apply {
        setHints(
          mapOf(
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.TRY_HARDER to true,
          )
        )
      }
    return runCatching {
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
      reader.decodeWithState(binaryBitmap).text
    }.getOrNull()
  }

  private fun rotateY90(data: ByteArray, width: Int, height: Int): ByteArray {
    val out = ByteArray(width * height)
    var idx = 0
    for (x in 0 until width) {
      for (y in height - 1 downTo 0) {
        out[idx++] = data[y * width + x]
      }
    }
    return out
  }

  private fun rotateY270(data: ByteArray, width: Int, height: Int): ByteArray {
    val out = ByteArray(width * height)
    var idx = 0
    for (x in width - 1 downTo 0) {
      for (y in 0 until height) {
        out[idx++] = data[y * width + x]
      }
    }
    return out
  }

  private infix fun ByteArray.toPair(dims: Pair<Int, Int>): Triple<ByteArray, Int, Int> =
    Triple(this, dims.first, dims.second)
}
