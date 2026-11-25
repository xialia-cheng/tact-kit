package com.xialia.tactkit.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tactile haptic & sound feedback wrapper for TactKit (简工坊).
 * Uses CLOCK_TICK for light tactile snaps and CONFIRM (with API < 30 fallback) for completions.
 */
class AppHaptics(private val view: View) {

  fun tick() {
    tick(view)
  }

  fun click() {
    click(view)
  }

  fun confirm() {
    val constant =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
      } else {
        HapticFeedbackConstants.CONTEXT_CLICK
      }
    view.performHapticFeedback(constant)
  }

  fun reject() {
    val constant =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
      } else {
        HapticFeedbackConstants.LONG_PRESS
      }
    view.performHapticFeedback(constant)
  }

  companion object {
    fun tick(view: View) {
      view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun click(view: View) {
      view.playSoundEffect(SoundEffectConstants.CLICK)
      view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
  }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
  val view = LocalView.current
  return remember(view) { AppHaptics(view) }
}
