package com.xialia.tactkit.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Tactile haptic feedback wrapper for TactKit (简工坊).
 * Uses CLOCK_TICK for light tactile snaps and CONFIRM (with API < 30 fallback) for completions.
 */
class AppHaptics(private val view: View) {

  fun tick() {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
}

@Composable
fun rememberAppHaptics(): AppHaptics {
  val view = LocalView.current
  return remember(view) { AppHaptics(view) }
}
