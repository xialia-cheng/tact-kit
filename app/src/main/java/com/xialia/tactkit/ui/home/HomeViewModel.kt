package com.xialia.tactkit.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xialia.tactkit.data.TactKitDatabase
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.data.repository.WorkbenchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CardFilter(val label: String) {
  ALL("全部"),
  CHECKLIST("清单"),
  NOTE("便签"),
  RECENT_FILE("文件"),
}

data class HomeUiState(
  val cards: List<WorkbenchCard> = emptyList(),
  val searchQuery: String = "",
  val activeFilter: CardFilter = CardFilter.ALL,
  val preferences: UserPreferences = UserPreferences(),
  val isImportingFile: Boolean = false,
  val statusMessage: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

  private val database = TactKitDatabase.getInstance(application)
  private val workbenchRepository =
    WorkbenchRepository(database.workbenchCardDao(), application.applicationContext)
  private val settingsRepository = SettingsRepository(application.applicationContext)

  private val searchQuery = MutableStateFlow("")
  private val activeFilter = MutableStateFlow(CardFilter.ALL)
  private val isImportingFile = MutableStateFlow(false)
  private val statusMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<HomeUiState> =
    combine(
        workbenchRepository.observeCards(),
        searchQuery,
        activeFilter,
        settingsRepository.preferencesFlow,
        isImportingFile,
        statusMessage,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allCards = values[0] as List<WorkbenchCard>
        val query = values[1] as String
        val filter = values[2] as CardFilter
        val prefs = values[3] as UserPreferences
        val importing = values[4] as Boolean
        val message = values[5] as String?

        val normalizedQuery = query.trim().lowercase()
        val filtered =
          allCards.filter { card ->
            val matchesFilter =
              when (filter) {
                CardFilter.ALL -> true
                CardFilter.CHECKLIST -> card is WorkbenchCard.Checklist
                CardFilter.NOTE -> card is WorkbenchCard.Note
                CardFilter.RECENT_FILE -> card is WorkbenchCard.RecentFile
              }
            val matchesQuery =
              normalizedQuery.isEmpty() ||
                card.title.lowercase().contains(normalizedQuery) ||
                when (card) {
                  is WorkbenchCard.Checklist ->
                    card.items.any { it.text.lowercase().contains(normalizedQuery) }
                  is WorkbenchCard.Note ->
                    card.markdownContent.lowercase().contains(normalizedQuery)
                  is WorkbenchCard.RecentFile ->
                    card.fileName.lowercase().contains(normalizedQuery) ||
                      card.mimeType.lowercase().contains(normalizedQuery)
                }
            matchesFilter && matchesQuery
          }

        HomeUiState(
          cards = filtered,
          searchQuery = query,
          activeFilter = filter,
          preferences = prefs,
          isImportingFile = importing,
          statusMessage = message,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
      )

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun setActiveFilter(filter: CardFilter) {
    activeFilter.value = filter
  }

  fun clearStatusMessage() {
    statusMessage.value = null
  }

  fun createChecklist(title: String, items: List<String>) {
    viewModelScope.launch { workbenchRepository.createChecklist(title, items) }
  }

  fun toggleChecklistItem(cardId: Long, itemId: String) {
    viewModelScope.launch { workbenchRepository.toggleChecklistItem(cardId, itemId) }
  }

  fun addChecklistItem(cardId: Long, text: String) {
    viewModelScope.launch { workbenchRepository.addChecklistItem(cardId, text) }
  }

  fun deleteChecklistItem(cardId: Long, itemId: String) {
    viewModelScope.launch { workbenchRepository.deleteChecklistItem(cardId, itemId) }
  }

  fun createNote(title: String, content: String) {
    viewModelScope.launch { workbenchRepository.createNote(title, content) }
  }

  fun updateNote(cardId: Long, title: String, content: String) {
    viewModelScope.launch { workbenchRepository.updateNote(cardId, title, content) }
  }

  fun togglePinCard(cardId: Long) {
    viewModelScope.launch { workbenchRepository.togglePinCard(cardId) }
  }

  fun deleteCard(cardId: Long) {
    viewModelScope.launch { workbenchRepository.deleteCard(cardId) }
  }

  fun importLocalFile(uri: Uri) {
    viewModelScope.launch {
      isImportingFile.value = true
      try {
        workbenchRepository.importLocalFile(uri)
        statusMessage.value = "已导入本地文件并计算 SHA-256"
      } catch (e: Exception) {
        statusMessage.value = "导入文件失败: ${e.message ?: "未知错误"}"
      } finally {
        isImportingFile.value = false
      }
    }
  }

  fun togglePinnedTool(toolId: String) {
    viewModelScope.launch { settingsRepository.togglePinnedTool(toolId) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsRepository.setThemeMode(mode) }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
  }

  fun setLiquidGlassPowerSave(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setLiquidGlassPowerSave(enabled) }
  }
}
