package com.xialia.tactkit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItem(
  val id: String,
  val text: String,
  val checked: Boolean = false,
)

enum class WorkbenchCardType {
  CHECKLIST,
  NOTE,
  RECENT_FILE,
}

@Entity(tableName = "workbench_cards")
data class WorkbenchCardEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val type: String,
  val title: String,
  val content: String,
  val fileUri: String? = null,
  val fileName: String? = null,
  val fileSizeBytes: Long? = null,
  val mimeType: String? = null,
  val sha256: String? = null,
  val pinned: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

sealed interface WorkbenchCard {
  val id: Long
  val title: String
  val pinned: Boolean
  val createdAt: Long
  val updatedAt: Long

  data class Checklist(
    override val id: Long,
    override val title: String,
    val items: List<ChecklistItem>,
    override val pinned: Boolean,
    override val createdAt: Long,
    override val updatedAt: Long,
  ) : WorkbenchCard

  data class Note(
    override val id: Long,
    override val title: String,
    val markdownContent: String,
    override val pinned: Boolean,
    override val createdAt: Long,
    override val updatedAt: Long,
  ) : WorkbenchCard

  data class RecentFile(
    override val id: Long,
    override val title: String,
    val fileUri: String?,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val sha256: String?,
    val previewExcerpt: String,
    override val pinned: Boolean,
    override val createdAt: Long,
    override val updatedAt: Long,
  ) : WorkbenchCard
}
