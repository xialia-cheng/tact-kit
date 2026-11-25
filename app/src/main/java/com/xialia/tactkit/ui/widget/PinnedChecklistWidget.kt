package com.xialia.tactkit.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.xialia.tactkit.MainActivity
import com.xialia.tactkit.data.TactKitDatabase
import com.xialia.tactkit.data.model.ChecklistItem
import com.xialia.tactkit.data.repository.WorkbenchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PinnedChecklistWidget : GlanceAppWidget() {

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val checklistData =
      withContext(Dispatchers.IO) {
        val dao = TactKitDatabase.getInstance(context.applicationContext).workbenchCardDao()
        val entity = dao.getTopChecklist()
        if (entity == null) {
          null
        } else {
          val items =
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<List<ChecklistItem>>(entity.content) }
              .getOrDefault(emptyList())
          Triple(entity.id, entity.title, items)
        }
      }

    provideContent {
      GlanceTheme {
        PinnedChecklistContent(
          context = context,
          cardId = checklistData?.first,
          title = checklistData?.second,
          items = checklistData?.third.orEmpty(),
        )
      }
    }
  }

  @Composable
  private fun PinnedChecklistContent(
    context: Context,
    cardId: Long?,
    title: String?,
    items: List<ChecklistItem>,
  ) {
    val launchMainIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }

    Column(
      modifier =
        GlanceModifier.fillMaxSize()
          .cornerRadius(24.dp)
          .background(GlanceTheme.colors.widgetBackground)
          .padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(launchMainIntent)),
      ) {
        Text(
          text = title ?: "简工坊 · 置顶清单",
          style =
            TextStyle(
              color = GlanceTheme.colors.onSurface,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            ),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight(),
        )
        Text(
          text = if (items.isEmpty()) "新建" else "${items.count { it.checked }}/${items.size}",
          style =
            TextStyle(
              color = GlanceTheme.colors.primary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
            ),
        )
      }

      Spacer(modifier = GlanceModifier.height(8.dp))

      if (cardId == null || items.isEmpty()) {
        val createIntent =
          Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TOOL_ID, "quick_checklist")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
        Column(
          verticalAlignment = Alignment.CenterVertically,
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier =
            GlanceModifier.fillMaxSize()
              .cornerRadius(16.dp)
              .background(GlanceTheme.colors.surfaceVariant)
              .clickable(actionStartActivity(createIntent))
              .padding(10.dp),
        ) {
          Text(
            text = "暂无置顶待办清单",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
          )
          Spacer(modifier = GlanceModifier.height(4.dp))
          Text(
            text = "点击创建第一份清单",
            style =
              TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
              ),
          )
        }
      } else {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
          items.take(5).forEach { item ->
            CheckBox(
              checked = item.checked,
              onCheckedChange =
                actionRunCallback<ToggleChecklistItemAction>(
                  actionParametersOf(
                    KEY_CARD_ID to cardId,
                    KEY_ITEM_ID to item.id,
                  )
                ),
              text = item.text,
              style =
                TextStyle(
                  color =
                    if (item.checked) GlanceTheme.colors.onSurfaceVariant
                    else GlanceTheme.colors.onSurface,
                  fontSize = 13.sp,
                ),
              maxLines = 1,
              modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            )
          }
        }
      }
    }
  }

  companion object {
    val KEY_CARD_ID = ActionParameters.Key<Long>("card_id")
    val KEY_ITEM_ID = ActionParameters.Key<String>("item_id")

    suspend fun notifyChanged(context: Context) {
      runCatching { PinnedChecklistWidget().updateAll(context.applicationContext) }
    }
  }
}

class ToggleChecklistItemAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val cardId = parameters[PinnedChecklistWidget.KEY_CARD_ID] ?: return
    val itemId = parameters[PinnedChecklistWidget.KEY_ITEM_ID] ?: return
    withContext(Dispatchers.IO) {
      val dao = TactKitDatabase.getInstance(context.applicationContext).workbenchCardDao()
      val repo = WorkbenchRepository(dao, context.applicationContext)
      repo.toggleChecklistItem(cardId, itemId)
    }
    PinnedChecklistWidget().update(context, glanceId)
  }
}

class PinnedChecklistWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = PinnedChecklistWidget()
}
