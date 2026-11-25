package com.xialia.tactkit.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.xialia.tactkit.MainActivity

private data class WidgetShortcutSpec(
  val toolId: String,
  val title: String,
  val tag: String,
)

class QuickShortcutsWidget : GlanceAppWidget() {

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      GlanceTheme {
        QuickShortcutsContent(context)
      }
    }
  }

  @Composable
  private fun QuickShortcutsContent(context: Context) {
    val shortcuts =
      listOf(
        WidgetShortcutSpec("doc_scanner", "文档扫描", "拍纸张转PDF"),
        WidgetShortcutSpec("pdf_toolkit", "PDF 工具", "阅读与合并"),
        WidgetShortcutSpec("quick_note", "快速便签", "Markdown记录"),
        WidgetShortcutSpec("qr_studio", "二维码", "生成与扫码"),
      )

    Column(
      modifier =
        GlanceModifier.fillMaxSize()
          .cornerRadius(24.dp)
          .background(GlanceTheme.colors.widgetBackground)
          .padding(14.dp),
    ) {
      Text(
        text = "简工坊 · 快捷入口",
        style =
          TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
          ),
      )
      Spacer(modifier = GlanceModifier.height(8.dp))

      Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        ShortcutTile(context, shortcuts[0], GlanceModifier.defaultWeight().fillMaxSize())
        Spacer(modifier = GlanceModifier.width(8.dp))
        ShortcutTile(context, shortcuts[1], GlanceModifier.defaultWeight().fillMaxSize())
      }
      Spacer(modifier = GlanceModifier.height(8.dp))
      Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        ShortcutTile(context, shortcuts[2], GlanceModifier.defaultWeight().fillMaxSize())
        Spacer(modifier = GlanceModifier.width(8.dp))
        ShortcutTile(context, shortcuts[3], GlanceModifier.defaultWeight().fillMaxSize())
      }
    }
  }

  @Composable
  private fun ShortcutTile(
    context: Context,
    spec: WidgetShortcutSpec,
    modifier: GlanceModifier,
  ) {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_TOOL_ID, spec.toolId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    Column(
      verticalAlignment = Alignment.CenterVertically,
      horizontalAlignment = Alignment.Start,
      modifier =
        modifier
          .cornerRadius(16.dp)
          .background(GlanceTheme.colors.secondaryContainer)
          .clickable(actionStartActivity(intent))
          .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Text(
        text = spec.title,
        style =
          TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
          ),
        maxLines = 1,
      )
      Text(
        text = spec.tag,
        style =
          TextStyle(
            color = GlanceTheme.colors.onSecondaryContainer,
            fontSize = 10.sp,
          ),
        maxLines = 1,
      )
    }
  }
}

class QuickShortcutsWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = QuickShortcutsWidget()
}
