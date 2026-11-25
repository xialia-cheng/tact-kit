package com.xialia.tactkit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.xialia.tactkit.R
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.rememberAppHaptics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkbenchCardView(
  card: WorkbenchCard,
  onToggleChecklistItem: (Long, String) -> Unit,
  onAddChecklistItem: (Long, String) -> Unit,
  onDeleteChecklistItem: (Long, String) -> Unit,
  onEditNote: (WorkbenchCard.Note) -> Unit,
  onOpenFileQuickLook: (WorkbenchCard.RecentFile) -> Unit,
  onTogglePin: (Long) -> Unit,
  onDeleteCard: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptics = rememberAppHaptics()

  Surface(
    shape = ContinuousRoundedRectangle(22.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = if (card.pinned) 4.dp else 1.dp,
    border =
      BorderStroke(
        width = if (card.pinned) 1.5.dp else 1.dp,
        color =
          if (card.pinned) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
          } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
          },
      ),
    modifier = modifier.fillMaxWidth().testTag("workbench_card_${card.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Card Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        val (typeLabel, typeIcon, typeTint) =
          when (card) {
            is WorkbenchCard.Checklist ->
              Triple("清单", Icons.Rounded.Checklist, MaterialTheme.colorScheme.primary)
            is WorkbenchCard.Note ->
              Triple("Markdown / 代码", Icons.Rounded.EditNote, MaterialTheme.colorScheme.secondary)
            is WorkbenchCard.RecentFile ->
              Triple("本地文件", fileIconForMime(card.mimeType), MaterialTheme.colorScheme.tertiary)
          }

        Surface(
          color = typeTint.copy(alpha = 0.14f),
          shape = ContinuousCapsule(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = typeIcon,
              contentDescription = null,
              tint = typeTint,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = typeLabel,
              style = MaterialTheme.typography.labelSmall,
              color = typeTint,
            )
          }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
          text = formatTimestamp(card.updatedAt),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IconButton(
          onClick = {
            haptics.tick()
            onTogglePin(card.id)
          },
          modifier = Modifier.size(40.dp).testTag("card_pin_${card.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = stringResource(R.string.cd_pin),
            tint =
              if (card.pinned) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
              },
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = {
            haptics.tick()
            onDeleteCard(card.id)
          },
          modifier = Modifier.size(40.dp).testTag("card_delete_${card.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = card.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(10.dp))

      when (card) {
        is WorkbenchCard.Checklist -> {
          ChecklistCardBody(
            card = card,
            onToggleItem = { itemId, currentlyChecked ->
              if (currentlyChecked) haptics.tick() else haptics.confirm()
              onToggleChecklistItem(card.id, itemId)
            },
            onAddItem = { text ->
              haptics.confirm()
              onAddChecklistItem(card.id, text)
            },
            onDeleteItem = { itemId ->
              haptics.tick()
              onDeleteChecklistItem(card.id, itemId)
            },
          )
        }

        is WorkbenchCard.Note -> {
          Surface(
            onClick = {
              haptics.tick()
              onEditNote(card)
            },
            shape = ContinuousRoundedRectangle(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
            modifier = Modifier.fillMaxWidth().testTag("note_preview_${card.id}"),
          ) {
            Text(
              text = card.markdownContent.ifBlank { "点击编辑便签内容..." },
              style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TactKitMono),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 6,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        is WorkbenchCard.RecentFile -> {
          RecentFileCardBody(
            card = card,
            onOpenQuickLook = {
              haptics.tick()
              onOpenFileQuickLook(card)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun ChecklistCardBody(
  card: WorkbenchCard.Checklist,
  onToggleItem: (String, Boolean) -> Unit,
  onAddItem: (String) -> Unit,
  onDeleteItem: (String) -> Unit,
) {
  var showAddField by remember { mutableStateOf(false) }
  var newItemText by remember { mutableStateOf("") }

  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    card.items.forEach { item ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onToggleItem(item.id, item.checked) }
            .padding(vertical = 2.dp)
            .testTag("checklist_item_${item.id}"),
      ) {
        Checkbox(
          checked = item.checked,
          onCheckedChange = { onToggleItem(item.id, item.checked) },
        )
        Text(
          text = item.text,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
            ),
          color =
            if (item.checked) {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = { onDeleteItem(item.id) },
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cd_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }

    if (showAddField) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
      ) {
        OutlinedTextField(
          value = newItemText,
          onValueChange = { newItemText = it },
          placeholder = { Text("输入新待办条目") },
          singleLine = true,
          shape = ContinuousRoundedRectangle(14.dp),
          modifier = Modifier.weight(1f).testTag("checklist_add_input_${card.id}"),
        )
        TextButton(
          onClick = {
            if (newItemText.isNotBlank()) {
              onAddItem(newItemText)
              newItemText = ""
              showAddField = false
            }
          },
          modifier = Modifier.testTag("checklist_add_confirm_${card.id}"),
        ) {
          Text("添加")
        }
      }
    } else {
      TextButton(
        onClick = { showAddField = true },
        modifier = Modifier.testTag("checklist_show_add_${card.id}"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("新增条目")
      }
    }
  }
}

@Composable
private fun RecentFileCardBody(
  card: WorkbenchCard.RecentFile,
  onOpenQuickLook: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = ContinuousRoundedRectangle(10.dp),
      ) {
        Text(
          text = FileInspectionHelper.formatBytes(card.fileSizeBytes),
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = ContinuousRoundedRectangle(10.dp),
      ) {
        Text(
          text = card.mimeType,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }

    card.sha256?.takeIf { it.isNotBlank() }?.let { digest ->
      Text(
        text = "SHA-256: ${digest.take(16)}…${digest.takeLast(8)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (card.previewExcerpt.isNotBlank()) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = ContinuousRoundedRectangle(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = card.previewExcerpt,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = TactKitMono),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(10.dp),
        )
      }
    }

    if (card.fileUri != null) {
      TextButton(
        onClick = onOpenQuickLook,
        modifier = Modifier.testTag("open_quick_look_${card.id}"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("在 Quick Look 中预览")
      }
    }
  }
}

private fun fileIconForMime(mimeType: String) =
  when {
    mimeType.startsWith("image/") -> Icons.Rounded.Image
    mimeType == "application/pdf" -> Icons.Rounded.PictureAsPdf
    mimeType.startsWith("text/") -> Icons.Rounded.Description
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
  }

private fun formatTimestamp(timestamp: Long): String {
  val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
  return formatter.format(Date(timestamp))
}
