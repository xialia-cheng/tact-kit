package com.xialia.tactkit

import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xialia.tactkit.data.datastore.AppThemeStyle
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.ui.components.GeminiSparkleLoader
import com.xialia.tactkit.ui.components.LiquidGlassToolBallOverlay
import com.xialia.tactkit.ui.components.TactKitAiBusyBanner
import com.xialia.tactkit.ui.components.TactKitBusyStatusBar
import com.xialia.tactkit.ui.components.TactKitMorphLoadingIndicator
import com.xialia.tactkit.ui.settings.HelpTipsScreen
import com.xialia.tactkit.ui.settings.ThemeGalleryScreen
import com.xialia.tactkit.ui.theme.TactKitTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashAuditRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun testMainActivityLaunchAndAll33Routes() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    shadowOf(Looper.getMainLooper()).idle()

    val crashFile = File(activity.filesDir, "last_crash.txt")
    assertFalse("Crash recorded at launch: ${crashFile.takeIf { it.exists() }?.readText()}", crashFile.exists())

    val onNewIntentMethod =
      MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply {
        isAccessible = true
      }
    val toolIds =
      listOf(
        "file_manager",
        "doc_scanner",
        "pdf_toolkit",
        "pdf_annotate",
        "image_privacy",
        "image_long",
        "image_color_picker",
        "qr_studio",
        "lan_share",
        "net_diag",
        "dns_ssl",
        "wifi_channel",
        "decibel_meter",
        "light_lux",
        "physical_ruler",
        "morse_torch",
        "battery_hardware",
        "level_compass",
        "metal_detector",
        "led_clock",
        "docx_editor",
        "ai_hub",
        "media_extractor",
        "archive_box",
        "data_workshop",
        "hash_workshop",
        "crypto_vault",
        "codec_hash",
        "electronics_calc",
        "unit_calculator",
        "pomodoro_timer",
        "settings",
        "home_dashboard",
      )
    for (toolId in toolIds) {
      val intent =
        Intent(activity, MainActivity::class.java).apply {
          putExtra(MainActivity.EXTRA_OPEN_TOOL_ID, toolId)
          addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      onNewIntentMethod.invoke(activity, intent)
      shadowOf(Looper.getMainLooper()).idle()
      activity.onBackPressedDispatcher.onBackPressed()
      shadowOf(Looper.getMainLooper()).idle()
    }

    assertFalse("Crash recorded after routes: ${crashFile.takeIf { it.exists() }?.readText()}", crashFile.exists())
    controller.pause().stop().destroy()
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun testAllThemesToolBallMorphIndicatorsAndCrop() {
    val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    val cropped = ImageEngine.cropNormalized(bmp, 0.96f, 0.96f, 1.0f, 1.0f)
    assertTrue(cropped.width in 1..100)
    assertTrue(cropped.height in 1..100)

    var activeStyle by mutableStateOf(AppThemeStyle.CLASSIC_DEFAULT)
    var showHelp by mutableStateOf(false)
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      TactKitTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false, appThemeStyle = activeStyle) {
        val backdrop = rememberLayerBackdrop()
        Box(modifier = Modifier.fillMaxSize()) {
          Column(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            TactKitMorphLoadingIndicator(size = 36.dp)
            GeminiSparkleLoader(size = 36.dp)
            TactKitBusyStatusBar(message = "测试 ${activeStyle.label}")
            TactKitAiBusyBanner(message = "AI 处理中")
            if (showHelp) {
              HelpTipsScreen(onLaunchTool = {}, onReplayOnboarding = {}, onNavigateBack = {})
            } else {
              ThemeGalleryScreen(
                preferences = UserPreferences(appThemeStyle = activeStyle),
                onThemeModeChange = {},
                onDynamicColorChange = {},
                onAppThemeStyleChange = {},
                onNavigateBack = {},
              )
            }
          }
          LiquidGlassToolBallOverlay(
            backdrop = backdrop,
            isPowerSaveMode = false,
            pinnedToolIds = listOf("doc_scanner", "pdf_toolkit"),
            onLaunchTool = {},
            onTogglePinTool = {},
          )
        }
      }
    }

    for (style in AppThemeStyle.entries) {
      activeStyle = style
      repeat(3) { composeRule.mainClock.advanceTimeBy(160) }
      composeRule.onNodeWithTag("liquid_glass_tool_ball").performClick()
      repeat(4) { composeRule.mainClock.advanceTimeBy(160) }
      composeRule.onNodeWithTag("close_tool_drawer_button").performClick()
      repeat(4) { composeRule.mainClock.advanceTimeBy(160) }
    }
    showHelp = true
    repeat(3) { composeRule.mainClock.advanceTimeBy(160) }
  }
}
