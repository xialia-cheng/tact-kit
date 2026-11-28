package com.xialia.tactkit.ui.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xialia.tactkit.BuildConfig
import com.xialia.tactkit.data.TactKitDatabase
import com.xialia.tactkit.data.datastore.AppThemeStyle
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.data.repository.FileCacheRepository
import com.xialia.tactkit.data.repository.ReleaseInfo
import com.xialia.tactkit.data.repository.WorkbenchRepository
import com.xialia.tactkit.engine.AudioPlaybackState
import com.xialia.tactkit.engine.VoiceMemoManager
import com.xialia.tactkit.ui.components.OnboardingStep
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class OnboardingUiState(
  val showWelcomeDialog: Boolean = false,
  val activeStep: OnboardingStep? = null,
)

enum class CardFilter(val label: String) {
  ALL("全部"),
  CHECKLIST("清单"),
  NOTE("便签"),
  VOICE_MEMO("语音"),
  RECENT_FILE("文件"),
}

@Immutable
data class DocumentDashboardStats(
  val totalItemsCount: Int = 0,
  val thisWeekAddedCount: Int = 0,
  val pdfCount: Int = 0,
  val docTextCount: Int = 0,
  val imageCount: Int = 0,
  val mediaArchiveCount: Int = 0,
  val recentFiles: List<WorkbenchCard.RecentFile> = emptyList(),
)

@Immutable
data class HomeUiState(
  val cards: List<WorkbenchCard> = emptyList(),
  val totalChecklists: Int = 0,
  val totalNotes: Int = 0,
  val totalVoiceMemos: Int = 0,
  val totalFiles: Int = 0,
  val dashboardStats: DocumentDashboardStats = DocumentDashboardStats(),
  val searchQuery: String = "",
  val activeFilter: CardFilter = CardFilter.ALL,
  val preferences: UserPreferences = UserPreferences(),
  val isPreferencesLoaded: Boolean = false,
  val isImportingFile: Boolean = false,
  val statusMessage: String? = null,
  val availableUpdate: ReleaseInfo? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

  private val database = TactKitDatabase.getInstance(application)
  private val workbenchRepository =
    WorkbenchRepository(database.workbenchCardDao(), application.applicationContext)
  private val settingsRepository = SettingsRepository(application.applicationContext)
  private val appUpdateRepository = AppUpdateRepository()
  val voiceMemoManager = VoiceMemoManager(application.applicationContext)

  val audioPlaybackState: StateFlow<AudioPlaybackState> = voiceMemoManager.playbackState

  private val searchQuery = MutableStateFlow("")
  private val activeFilter = MutableStateFlow(CardFilter.ALL)
  private val isImportingFile = MutableStateFlow(false)
  private val statusMessage = MutableStateFlow<String?>(null)
  private val availableUpdate = MutableStateFlow<ReleaseInfo?>(null)

  val hasCompletedOnboarding: StateFlow<Boolean?> =
    settingsRepository.hasCompletedOnboardingFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = null,
    )

  private val onboardingHandledInSession = MutableStateFlow(false)
  private val showOnboardingWelcome = MutableStateFlow(false)
  private val activeOnboardingStep = MutableStateFlow<OnboardingStep?>(null)

  val onboardingUiState: StateFlow<OnboardingUiState> =
    combine(showOnboardingWelcome, activeOnboardingStep) { showWelcome, step ->
        OnboardingUiState(showWelcomeDialog = showWelcome, activeStep = step)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OnboardingUiState(),
      )

  init {
    viewModelScope.launch {
      hasCompletedOnboarding.collect { completed ->
        if (completed == false && !onboardingHandledInSession.value && activeOnboardingStep.value == null) {
          showOnboardingWelcome.value = true
        } else if (completed == true) {
          showOnboardingWelcome.value = false
        }
      }
    }
    viewModelScope.launch {
      val prefs = runCatching { settingsRepository.preferencesFlow.first() }.getOrNull()
      if (prefs?.autoCheckUpdates == true) {
        appUpdateRepository.fetchLatestRelease(BuildConfig.VERSION_NAME).onSuccess { info ->
          if (info.isUpdateAvailable) {
            availableUpdate.value = info
          }
        }
      }
    }
  }

  val uiState: StateFlow<HomeUiState> =
    combine(
        workbenchRepository.observeCards(),
        searchQuery,
        activeFilter,
        settingsRepository.preferencesFlow,
        isImportingFile,
        statusMessage,
        availableUpdate,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allCards = values[0] as List<WorkbenchCard>
        val query = values[1] as String
        val filter = values[2] as CardFilter
        val prefs = values[3] as UserPreferences
        val importing = values[4] as Boolean
        val message = values[5] as String?
        val updateInfo = values[6] as ReleaseInfo?

        val checklistCount = allCards.count { it is WorkbenchCard.Checklist }
        val noteCount = allCards.count { it is WorkbenchCard.Note }
        val voiceCount = allCards.count { it is WorkbenchCard.VoiceMemo }
        val fileCount = allCards.count { it is WorkbenchCard.RecentFile }

        val weekCutoff = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val recentFiles = allCards.filterIsInstance<WorkbenchCard.RecentFile>()
        var pdfDocs = 0
        var textDocs = checklistCount + noteCount
        var imgDocs = 0
        var mediaDocs = voiceCount
        recentFiles.forEach { file ->
          val lower = file.fileName.lowercase()
          when {
            file.mimeType == "application/pdf" || lower.endsWith(".pdf") -> pdfDocs++
            file.mimeType.startsWith("image/") -> imgDocs++
            file.mimeType.startsWith("text/") ||
              lower.endsWith(".docx") ||
              lower.endsWith(".md") ||
              lower.endsWith(".txt") ||
              lower.endsWith(".json") ||
              lower.endsWith(".csv") ||
              lower.endsWith(".yaml") ||
              lower.endsWith(".xml") -> textDocs++
            else -> mediaDocs++
          }
        }
        val dashboard =
          DocumentDashboardStats(
            totalItemsCount = allCards.size,
            thisWeekAddedCount = allCards.count { it.createdAt >= weekCutoff },
            pdfCount = pdfDocs,
            docTextCount = textDocs,
            imageCount = imgDocs,
            mediaArchiveCount = mediaDocs,
            recentFiles = recentFiles.take(3),
          )

        val normalizedQuery = query.trim().lowercase()
        val filtered =
          allCards.filter { card ->
            val matchesFilter =
              when (filter) {
                CardFilter.ALL -> true
                CardFilter.CHECKLIST -> card is WorkbenchCard.Checklist
                CardFilter.NOTE -> card is WorkbenchCard.Note
                CardFilter.VOICE_MEMO -> card is WorkbenchCard.VoiceMemo
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
                  is WorkbenchCard.VoiceMemo ->
                    card.transcriptOrNote.lowercase().contains(normalizedQuery)
                  is WorkbenchCard.RecentFile ->
                    card.fileName.lowercase().contains(normalizedQuery) ||
                      card.mimeType.lowercase().contains(normalizedQuery)
                }
            matchesFilter && matchesQuery
          }

        HomeUiState(
          cards = filtered,
          totalChecklists = checklistCount,
          totalNotes = noteCount,
          totalVoiceMemos = voiceCount,
          totalFiles = fileCount,
          dashboardStats = dashboard,
          searchQuery = query,
          activeFilter = filter,
          preferences = prefs,
          isPreferencesLoaded = true,
          isImportingFile = importing,
          statusMessage = message,
          availableUpdate = updateInfo,
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

  fun createVoiceMemo(title: String, filePath: String, durationMs: Long, note: String) {
    viewModelScope.launch {
      workbenchRepository.createVoiceMemo(title, filePath, durationMs, note)
    }
  }

  fun toggleVoiceMemoPlayback(card: WorkbenchCard.VoiceMemo) {
    voiceMemoManager.togglePlayback(card.id, card.audioFilePath)
  }

  fun seekVoiceMemo(cardId: Long, positionMs: Long) {
    voiceMemoManager.seekTo(cardId, positionMs)
  }

  fun togglePinCard(cardId: Long) {
    viewModelScope.launch { workbenchRepository.togglePinCard(cardId) }
  }

  fun deleteCard(cardId: Long) {
    if (audioPlaybackState.value.playingCardId == cardId) {
      voiceMemoManager.stopPlayback()
    }
    viewModelScope.launch { workbenchRepository.deleteCard(cardId) }
  }

  fun importLocalFile(uri: Uri) {
    viewModelScope.launch {
      isImportingFile.value = true
      try {
        workbenchRepository.importLocalFile(uri)
        FileCacheRepository.markDirty()
        statusMessage.value = "已导入本地文件并生成校验指纹"
      } catch (e: Exception) {
        statusMessage.value = "导入文件失败: ${e.message ?: "未知错误"}"
      } finally {
        isImportingFile.value = false
      }
    }
  }

  fun registerGeneratedFileCard(
    file: java.io.File,
    uriString: String,
    mimeType: String,
    excerpt: String = "",
  ) {
    viewModelScope.launch {
      FileCacheRepository.markDirty()
      workbenchRepository.registerGeneratedFileCard(file, uriString, mimeType, excerpt)
    }
  }

  fun togglePinnedTool(toolId: String) {
    viewModelScope.launch { settingsRepository.togglePinnedTool(toolId) }
  }

  fun togglePinnedDir(relativePath: String) {
    viewModelScope.launch { settingsRepository.togglePinnedDir(relativePath) }
  }

  fun addLinkedSafTreeUri(uriString: String) {
    viewModelScope.launch { settingsRepository.addLinkedSafTreeUri(uriString) }
  }

  fun removeLinkedSafTreeUri(uriString: String) {
    viewModelScope.launch { settingsRepository.removeLinkedSafTreeUri(uriString) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsRepository.setThemeMode(mode) }
  }

  fun setAppThemeStyle(style: AppThemeStyle) {
    viewModelScope.launch { settingsRepository.setAppThemeStyle(style) }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
  }

  fun setLiquidGlassPowerSave(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setLiquidGlassPowerSave(enabled) }
  }

  fun setAutoCheckUpdates(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setAutoCheckUpdates(enabled) }
  }

  fun setHasCompletedOnboarding(completed: Boolean) {
    viewModelScope.launch(NonCancellable) {
      settingsRepository.setHasCompletedOnboarding(completed)
    }
  }

  fun skipOnboardingForever() {
    onboardingHandledInSession.value = true
    showOnboardingWelcome.value = false
    activeOnboardingStep.value = null
    viewModelScope.launch(NonCancellable) {
      settingsRepository.setHasCompletedOnboarding(true)
    }
  }

  fun startOnboardingTutorial() {
    onboardingHandledInSession.value = true
    showOnboardingWelcome.value = false
    activeOnboardingStep.value = OnboardingStep.DASHBOARD
  }

  fun advanceOnboardingStep() {
    val current = activeOnboardingStep.value ?: return
    val steps = OnboardingStep.entries
    val nextIdx = steps.indexOf(current) + 1
    if (nextIdx < steps.size) {
      activeOnboardingStep.value = steps[nextIdx]
    } else {
      skipOnboardingForever()
    }
  }

  fun replayOnboardingFromHelp() {
    onboardingHandledInSession.value = true
    showOnboardingWelcome.value = false
    activeOnboardingStep.value = OnboardingStep.DASHBOARD
  }

  fun dismissAvailableUpdate() {
    availableUpdate.value = null
  }

  suspend fun saveAiSettings(baseUrl: String, model: String, plainApiKey: String?): Result<Unit> {
    return runCatching {
      settingsRepository.saveAiSettings(baseUrl, model, plainApiKey)
    }.onSuccess {
      statusMessage.value = "AI 中枢配置已加密保存至 Keystore"
    }.onFailure {
      statusMessage.value = "保存 AI 配置失败：${it.message ?: "硬件密钥库不可用"}"
    }
  }

  fun clearAiApiKey() {
    viewModelScope.launch {
      settingsRepository.clearAiApiKey()
      statusMessage.value = "已清除本地加密 API Key"
    }
  }

  suspend fun getDecryptedAiApiKey(): String = settingsRepository.getDecryptedAiApiKey()

  fun stepCounter(direction: Int) {
    viewModelScope.launch { settingsRepository.stepCounter(direction) }
  }

  fun resetCounter() {
    viewModelScope.launch { settingsRepository.setCounterValue(0L) }
  }

  fun setCounterStep(step: Int) {
    viewModelScope.launch { settingsRepository.setCounterStep(step) }
  }

  fun setCounterLabel(label: String) {
    viewModelScope.launch { settingsRepository.setCounterLabel(label) }
  }

  override fun onCleared() {
    voiceMemoManager.stopPlayback()
    voiceMemoManager.cancelRecording()
    super.onCleared()
  }
}
