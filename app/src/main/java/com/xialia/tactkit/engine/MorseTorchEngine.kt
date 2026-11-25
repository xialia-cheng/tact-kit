package com.xialia.tactkit.engine

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.delay

object MorseTorchEngine {

  private val MORSE_MAP =
    mapOf(
      'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
      'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
      'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
      'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
      'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
      'Z' to "--..",
      '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
      '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
    )

  fun encodeTextToMorse(text: String): String {
    val clean = text.trim().uppercase()
    if (clean.isEmpty()) return ""
    return clean.split(Regex("\\s+")).joinToString(" / ") { word ->
      word.mapNotNull { ch -> MORSE_MAP[ch] }.joinToString(" ")
    }
  }

  fun findFlashCameraId(context: Context): String? {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
    return runCatching {
      cm.cameraIdList.firstOrNull { id ->
        val chars = cm.getCameraCharacteristics(id)
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
        hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
      } ?: cm.cameraIdList.firstOrNull { id ->
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
      }
    }.getOrNull()
  }

  fun setTorch(context: Context, enabled: Boolean): Boolean {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
    val id = findFlashCameraId(context) ?: return false
    return runCatching {
      cm.setTorchMode(id, enabled)
      true
    }.getOrDefault(false)
  }

  suspend fun transmitMorsePattern(
    context: Context,
    morseCode: String,
    unitMs: Long = 160L,
    repeatLoop: Boolean = false,
    onStep: (active: Boolean) -> Unit = {},
  ) {
    try {
      do {
        var i = 0
        while (i < morseCode.length) {
          when (val ch = morseCode[i]) {
            '.' -> {
              setTorch(context, true)
              onStep(true)
              delay(unitMs)
              setTorch(context, false)
              onStep(false)
              delay(unitMs)
            }
            '-' -> {
              setTorch(context, true)
              onStep(true)
              delay(unitMs * 3)
              setTorch(context, false)
              onStep(false)
              delay(unitMs)
            }
            '/' -> delay(unitMs * 4)
            ' ' -> delay(unitMs * 2)
            else -> Unit
          }
          i++
        }
        if (repeatLoop) {
          delay(unitMs * 6)
        }
      } while (repeatLoop)
    } finally {
      setTorch(context, false)
      onStep(false)
    }
  }
}
