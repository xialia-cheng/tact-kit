package com.xialia.tactkit

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xialia.tactkit.data.TactKitDatabase
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.datastore.ThemeMode
import com.xialia.tactkit.data.datastore.UserPreferences
import com.xialia.tactkit.data.model.WorkbenchCard
import com.xialia.tactkit.data.repository.WorkbenchRepository
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TactKitRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: TactKitDatabase
  private lateinit var workbenchRepository: WorkbenchRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, TactKitDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    workbenchRepository = WorkbenchRepository(database.workbenchCardDao(), context)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `app name matches TactKit specification`() {
    assertEquals("简工坊 TactKit", context.getString(R.string.app_name))
  }

  @Test
  fun `checklist and note CRUD operations persist in Room`() = runTest {
    val checklistId =
      workbenchRepository.createChecklist("测试清单", listOf("检查 SHA-256", "构建 APK"))
    val noteId = workbenchRepository.createNote("Gradle 笔记", "# TactKit")

    var cards = workbenchRepository.observeCards().first()
    assertEquals(2, cards.size)

    val checklist = cards.filterIsInstance<WorkbenchCard.Checklist>().first { it.id == checklistId }
    assertEquals(2, checklist.items.size)
    assertFalse(checklist.items[0].checked)

    workbenchRepository.toggleChecklistItem(checklistId, checklist.items[0].id)
    workbenchRepository.addChecklistItem(checklistId, "新增条目")
    workbenchRepository.updateNote(noteId, "Gradle 笔记 v2", "# TactKit Phase 1")
    workbenchRepository.togglePinCard(noteId)

    cards = workbenchRepository.observeCards().first()
    val updatedChecklist =
      cards.filterIsInstance<WorkbenchCard.Checklist>().first { it.id == checklistId }
    assertTrue(updatedChecklist.items[0].checked)
    assertEquals(3, updatedChecklist.items.size)

    val updatedNote = cards.filterIsInstance<WorkbenchCard.Note>().first { it.id == noteId }
    assertEquals("Gradle 笔记 v2", updatedNote.title)
    assertTrue(updatedNote.pinned)
  }

  @Test
  fun `settings repository persists power save mode theme and pinned tools`() = runTest {
    val tempFile = File.createTempFile("tactkit_prefs_", ".preferences_pb")
    tempFile.deleteOnExit()
    val dataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
    val settingsRepo = SettingsRepository(dataStore)

    val initial = settingsRepo.preferencesFlow.first()
    assertEquals(ThemeMode.SYSTEM, initial.themeMode)
    assertFalse(initial.liquidGlassPowerSave)
    assertEquals(UserPreferences.MAX_PINNED_TOOLS, initial.pinnedToolIds.size)

    settingsRepo.setLiquidGlassPowerSave(true)
    settingsRepo.setThemeMode(ThemeMode.DARK)
    settingsRepo.togglePinnedTool("codec_hash")

    val updated = settingsRepo.preferencesFlow.first()
    assertTrue(updated.liquidGlassPowerSave)
    assertEquals(ThemeMode.DARK, updated.themeMode)
    assertEquals(UserPreferences.MAX_PINNED_TOOLS, updated.pinnedToolIds.size)
    assertTrue("codec_hash" in updated.pinnedToolIds)
  }

  @Test
  fun `QuickLookActivity resolves ACTION_SEND text share intent`() {
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "TactKit Quick Look")
      }
    val matches = context.packageManager.queryIntentActivities(shareIntent, 0)
    assertTrue(matches.any { it.activityInfo.name == QuickLookActivity::class.java.name })
  }

  @Test
  fun `voice memo card persists in Room and cleans up audio file on delete`() = runTest {
    val audioFile = File.createTempFile("voice_test_", ".m4a", context.cacheDir)
    audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
    assertTrue(audioFile.exists())

    val cardId =
      workbenchRepository.createVoiceMemo(
        title = "会议口述",
        audioFilePath = audioFile.absolutePath,
        durationMs = 12_500L,
        note = "周五前发布 v0.2.0",
      )

    val cards = workbenchRepository.observeCards().first()
    val memo = cards.filterIsInstance<WorkbenchCard.VoiceMemo>().first { it.id == cardId }
    assertEquals("会议口述", memo.title)
    assertEquals(12_500L, memo.durationMs)
    assertEquals("周五前发布 v0.2.0", memo.transcriptOrNote)

    workbenchRepository.deleteCard(cardId)
    assertFalse(audioFile.exists())
  }

  @Test
  fun `settings repository persists AI endpoint and model preferences`() = runTest {
    val tempFile = File.createTempFile("tactkit_ai_prefs_", ".preferences_pb")
    tempFile.deleteOnExit()
    val dataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
    val settingsRepo = SettingsRepository(dataStore)

    settingsRepo.saveAiSettings(
      baseUrl = "https://api.deepseek.com/v1",
      model = "deepseek-chat",
      plainApiKey = null,
    )

    val updated = settingsRepo.preferencesFlow.first()
    assertEquals("https://api.deepseek.com/v1", updated.aiBaseUrl)
    assertEquals("deepseek-chat", updated.aiModel)
    assertFalse(updated.hasAiApiKey)
  }
}
