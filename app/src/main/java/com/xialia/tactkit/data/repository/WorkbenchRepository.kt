package com.xialia.tactkit.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xialia.tactkit.data.dao.WorkbenchCardDao
import com.xialia.tactkit.data.model.ChecklistItem
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.data.model.WorkbenchCardEntity
import com.xialia.tactkit.data.model.WorkbenchCardType
import com.xialia.tactkit.util.FileInspectionHelper
import com.xialia.tactkit.util.QuickLookFileInspection
import com.xialia.tactkit.ui.widget.PinnedChecklistWidget
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkbenchRepository(
  private val dao: WorkbenchCardDao,
  private val appContext: Context? = null,
) {

  private val json = Json { ignoreUnknownKeys = true }

  fun observeCards(): Flow<List<WorkbenchCard>> {
    return dao.observeAllCards().map { entities -> entities.map { it.toDomain() } }
  }

  suspend fun createChecklist(title: String, itemTexts: List<String>): Long {
    val cleanTitle = title.trim().ifEmpty { "待办清单" }
    val items =
      itemTexts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { ChecklistItem(id = UUID.randomUUID().toString(), text = it, checked = false) }
    val now = System.currentTimeMillis()
    val inserted = dao.insertCard(
      WorkbenchCardEntity(
        type = WorkbenchCardType.CHECKLIST.name,
        title = cleanTitle,
        content = json.encodeToString(items),
        createdAt = now,
        updatedAt = now,
      )
    )
    appContext?.let { PinnedChecklistWidget.notifyChanged(it) }
    return inserted
  }

  suspend fun toggleChecklistItem(cardId: Long, itemId: String) {
    val entity = dao.getCardById(cardId) ?: return
    if (entity.type != WorkbenchCardType.CHECKLIST.name) return
    val items = decodeChecklist(entity.content)
    val updated =
      items.map { item -> if (item.id == itemId) item.copy(checked = !item.checked) else item }
    dao.updateCard(
      entity.copy(content = json.encodeToString(updated), updatedAt = System.currentTimeMillis())
    )
    appContext?.let { PinnedChecklistWidget.notifyChanged(it) }
  }

  suspend fun addChecklistItem(cardId: Long, text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    val entity = dao.getCardById(cardId) ?: return
    if (entity.type != WorkbenchCardType.CHECKLIST.name) return
    val items = decodeChecklist(entity.content) +
      ChecklistItem(id = UUID.randomUUID().toString(), text = trimmed, checked = false)
    dao.updateCard(
      entity.copy(content = json.encodeToString(items), updatedAt = System.currentTimeMillis())
    )
    appContext?.let { PinnedChecklistWidget.notifyChanged(it) }
  }

  suspend fun deleteChecklistItem(cardId: Long, itemId: String) {
    val entity = dao.getCardById(cardId) ?: return
    if (entity.type != WorkbenchCardType.CHECKLIST.name) return
    val items = decodeChecklist(entity.content).filterNot { it.id == itemId }
    dao.updateCard(
      entity.copy(content = json.encodeToString(items), updatedAt = System.currentTimeMillis())
    )
    appContext?.let { PinnedChecklistWidget.notifyChanged(it) }
  }

  suspend fun createNote(title: String, markdownContent: String): Long {
    val cleanTitle = title.trim().ifEmpty { "无标题便签" }
    val now = System.currentTimeMillis()
    return dao.insertCard(
      WorkbenchCardEntity(
        type = WorkbenchCardType.NOTE.name,
        title = cleanTitle,
        content = markdownContent,
        createdAt = now,
        updatedAt = now,
      )
    )
  }

  suspend fun updateNote(cardId: Long, title: String, markdownContent: String) {
    val entity = dao.getCardById(cardId) ?: return
    dao.updateCard(
      entity.copy(
        title = title.trim().ifEmpty { "无标题便签" },
        content = markdownContent,
        updatedAt = System.currentTimeMillis(),
      )
    )
  }

  suspend fun createVoiceMemo(
    title: String,
    audioFilePath: String,
    durationMs: Long,
    note: String = "",
  ): Long {
    val cleanTitle = title.trim().ifEmpty { "语音备忘录" }
    val now = System.currentTimeMillis()
    return dao.insertCard(
      WorkbenchCardEntity(
        type = WorkbenchCardType.VOICE_MEMO.name,
        title = cleanTitle,
        content = note,
        mimeType = "audio/mp4",
        audioDurationMs = durationMs,
        audioFilePath = audioFilePath,
        createdAt = now,
        updatedAt = now,
      )
    )
  }

  suspend fun importLocalFile(uri: Uri): Long {
    val context = requireNotNull(appContext) { "Context is required to inspect SAF Uri" }
    try {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
      // Some providers only grant temporary read access; metadata and SHA-256 are still persisted.
    }
    val inspection = FileInspectionHelper.inspectUri(context.contentResolver, uri)
    return saveInspectionAsCard(inspection)
  }

  suspend fun saveInspectionAsCard(inspection: QuickLookFileInspection): Long {
    val now = System.currentTimeMillis()
    val excerpt = inspection.textPreview?.take(320).orEmpty()
    return dao.insertCard(
      WorkbenchCardEntity(
        type = WorkbenchCardType.RECENT_FILE.name,
        title = inspection.fileName,
        content = excerpt,
        fileUri = inspection.uriString,
        fileName = inspection.fileName,
        fileSizeBytes = inspection.fileSizeBytes,
        mimeType = inspection.mimeType,
        sha256 = inspection.sha256,
        createdAt = now,
        updatedAt = now,
      )
    )
  }

  suspend fun registerGeneratedFileCard(
    file: File,
    uriString: String,
    mimeType: String,
    excerpt: String = "",
  ): Long {
    val now = System.currentTimeMillis()
    return dao.insertCard(
      WorkbenchCardEntity(
        type = WorkbenchCardType.RECENT_FILE.name,
        title = file.name,
        content = excerpt.take(320),
        fileUri = uriString,
        fileName = file.name,
        fileSizeBytes = file.length(),
        mimeType = mimeType,
        sha256 = null,
        createdAt = now,
        updatedAt = now,
      )
    )
  }

  suspend fun togglePinCard(cardId: Long) {
    val entity = dao.getCardById(cardId) ?: return
    dao.updateCard(entity.copy(pinned = !entity.pinned, updatedAt = System.currentTimeMillis()))
    if (entity.type == WorkbenchCardType.CHECKLIST.name) {
      appContext?.let { PinnedChecklistWidget.notifyChanged(it) }
    }
  }

  suspend fun deleteCard(cardId: Long) {
    val entity = dao.getCardById(cardId)
    if (entity?.audioFilePath != null) {
      runCatching { File(entity.audioFilePath).delete() }
    }
    dao.deleteCardById(cardId)
    if (entity?.type == WorkbenchCardType.CHECKLIST.name) {
      appContext?.let { PinnedChecklistWidget.notifyChanged(it) }
    }
  }

  private fun decodeChecklist(raw: String): List<ChecklistItem> {
    return try {
      json.decodeFromString<List<ChecklistItem>>(raw)
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun WorkbenchCardEntity.toDomain(): WorkbenchCard {
    return when (type) {
      WorkbenchCardType.CHECKLIST.name ->
        WorkbenchCard.Checklist(
          id = id,
          title = title,
          items = decodeChecklist(content),
          pinned = pinned,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      WorkbenchCardType.NOTE.name ->
        WorkbenchCard.Note(
          id = id,
          title = title,
          markdownContent = content,
          pinned = pinned,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      WorkbenchCardType.VOICE_MEMO.name ->
        WorkbenchCard.VoiceMemo(
          id = id,
          title = title,
          transcriptOrNote = content,
          audioFilePath = audioFilePath.orEmpty(),
          durationMs = audioDurationMs,
          pinned = pinned,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
      else ->
        WorkbenchCard.RecentFile(
          id = id,
          title = title,
          fileUri = fileUri,
          fileName = fileName ?: title,
          fileSizeBytes = fileSizeBytes ?: 0L,
          mimeType = mimeType ?: "application/octet-stream",
          sha256 = sha256,
          previewExcerpt = content,
          pinned = pinned,
          createdAt = createdAt,
          updatedAt = updatedAt,
        )
    }
  }
}
