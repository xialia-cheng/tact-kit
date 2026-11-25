package com.xialia.tactkit

import android.content.Context
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import androidx.test.core.app.ApplicationProvider
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.domain.ToolCatalog
import com.xialia.tactkit.domain.ToolCategory
import com.xialia.tactkit.engine.ArchiveEngine
import com.xialia.tactkit.engine.BatchRenameRule
import com.xialia.tactkit.engine.DirectorySortBy
import com.xialia.tactkit.engine.FileCategory
import com.xialia.tactkit.engine.FileCategoryClassifier
import com.xialia.tactkit.engine.FileManagerEngine
import com.xialia.tactkit.provider.TactKitDocumentsProvider
import com.xialia.tactkit.ui.files.TactKitToolLaunch
import com.xialia.tactkit.ui.files.resolvePrimaryToolActions
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase6FileManagerTest {

  private lateinit var context: Context
  private lateinit var tempRoot: File

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    tempRoot = File(context.cacheDir, "fm_test_root_${System.nanoTime()}").apply { mkdirs() }
    TactKitDocumentsProvider.rootDirOverrideForTest = tempRoot
  }

  @After
  fun tearDown() {
    TactKitDocumentsProvider.rootDirOverrideForTest = null
    tempRoot.deleteRecursively()
  }

  @Test
  fun `classifier covers all 7 categories case-insensitively including dotfiles`() {
    assertEquals(FileCategory.DOCUMENTS, FileCategoryClassifier.classify("Report.PDF"))
    assertEquals(FileCategory.DOCUMENTS, FileCategoryClassifier.classify("spec.docx"))
    assertEquals(FileCategory.CODE_CONFIG, FileCategoryClassifier.classify(".env"))
    assertEquals(FileCategory.CODE_CONFIG, FileCategoryClassifier.classify("Main.KT"))
    assertEquals(FileCategory.IMAGES, FileCategoryClassifier.classify("photo.HEIC"))
    assertEquals(FileCategory.AUDIO_VIDEO, FileCategoryClassifier.classify("clip.MP4"))
    assertEquals(FileCategory.AUDIO_VIDEO, FileCategoryClassifier.classify("voice.opus"))
    assertEquals(FileCategory.ARCHIVES, FileCategoryClassifier.classify("backup.tar.gz"))
    assertEquals(FileCategory.PACKAGES, FileCategoryClassifier.classify("release.APK"))
    assertEquals(FileCategory.KEYS_CERTS_CALENDAR, FileCategoryClassifier.classify("signing.jks"))
    assertEquals(FileCategory.KEYS_CERTS_CALENDAR, FileCategoryClassifier.classify("tls.PEM"))
    assertEquals(FileCategory.KEYS_CERTS_CALENDAR, FileCategoryClassifier.classify("secret.tactenc"))
    assertEquals(FileCategory.KEYS_CERTS_CALENDAR, FileCategoryClassifier.classify("meeting.ics"))
    assertEquals(FileCategory.OTHER, FileCategoryClassifier.classify("blob.bin"))
  }

  @Test
  fun `tool routing maps extensions to right TactKit workshops`() {
    fun fakeEntry(name: String): com.xialia.tactkit.engine.ManagedFileEntry {
      val ext = FileCategoryClassifier.extractDisplayExtension(name)
      return com.xialia.tactkit.engine.ManagedFileEntry(
        id = "file:/$name",
        name = name,
        absolutePath = "/$name",
        safUriString = null,
        relativePath = name,
        isDirectory = false,
        sizeBytes = 128L,
        lastModifiedMs = 1000L,
        extension = ext,
        category = FileCategoryClassifier.classify(name),
        mimeType = FileCategoryClassifier.guessMimeType(name),
        sourceLabel = "来自 Download",
      )
    }

    assertTrue(
      resolvePrimaryToolActions(fakeEntry("doc.pdf")).any { it.launch == TactKitToolLaunch.PDF_TOOLKIT }
    )
    assertTrue(
      resolvePrimaryToolActions(fakeEntry("doc.docx")).any { it.launch == TactKitToolLaunch.DOCX_EDITOR }
    )
    assertTrue(
      resolvePrimaryToolActions(fakeEntry("cfg.json")).any { it.launch == TactKitToolLaunch.DATA_WORKSHOP }
    )
    assertTrue(
      resolvePrimaryToolActions(fakeEntry("arc.7z")).any { it.launch == TactKitToolLaunch.ARCHIVE_WORKSHOP }
    )
    assertTrue(
      resolvePrimaryToolActions(fakeEntry("vault.tactenc")).any { it.launch == TactKitToolLaunch.CRYPTO_VAULT }
    )
    assertTrue(
      resolvePrimaryToolActions(fakeEntry("pic.png")).any { it.launch == TactKitToolLaunch.AI_VISION }
    )
    assertTrue(
      resolvePrimaryToolActions(fakeEntry("readme.md")).any { it.launch == TactKitToolLaunch.AI_TEXT_ORGANIZE }
    )
  }

  @Test
  fun `scanRecentFiles skips Android data obb and sorts descending by lastModified`() = runTest {
    val qqDir = File(tempRoot, "Download/QQ").apply { mkdirs() }
    val wxDir = File(tempRoot, "Download/WeiXin").apply { mkdirs() }
    val androidData = File(tempRoot, "Android/data/pkg").apply { mkdirs() }
    val androidObb = File(tempRoot, "Android/obb/pkg").apply { mkdirs() }

    val f1 = File(qqDir, "release.jks").apply { writeText("jks"); setLastModified(2_000_000L) }
    val f2 = File(wxDir, "contract.pdf").apply { writeText("pdf"); setLastModified(5_000_000L) }
    File(androidData, "ignored.txt").apply { writeText("x"); setLastModified(9_000_000L) }
    File(androidObb, "ignored.obb").apply { writeText("x"); setLastModified(9_000_000L) }

    val recent = FileManagerEngine.scanRecentFiles(context, rootDir = tempRoot)
    assertEquals(2, recent.size)
    assertEquals(f2.name, recent[0].name)
    assertEquals("来自 微信", recent[0].sourceLabel)
    assertEquals(f1.name, recent[1].name)
    assertEquals("来自 QQ", recent[1].sourceLabel)

    val found = FileManagerEngine.searchFiles(context, query = ".jks", rootDir = tempRoot)
    assertEquals(1, found.size)
    assertEquals("release.jks", found.first().name)
  }

  @Test
  fun `directory listing folds empty and system media dirs with smartNoiseReduction`() = runTest {
    File(tempRoot, "Download").apply {
      mkdirs()
      File(this, "demo.txt").writeText("hello")
    }
    File(tempRoot, "Alarms").mkdirs()
    File(tempRoot, "Ringtones").apply {
      mkdirs()
      File(this, "ring.ogg").writeText("audio")
    }
    File(tempRoot, "EmptyFolder").mkdirs()
    File(tempRoot, ".secret.env").writeText("KEY=1")

    val reduced =
      FileManagerEngine.listDirectory(
        dir = tempRoot,
        rootDir = tempRoot,
        showHidden = false,
        sortBy = DirectorySortBy.NAME,
        smartNoiseReduction = true,
      )
    assertEquals(listOf("Download"), reduced.activeEntries.map { it.name })
    assertEquals(3, reduced.collapsedNoiseDirs.size)

    val raw =
      FileManagerEngine.listDirectory(
        dir = tempRoot,
        rootDir = tempRoot,
        showHidden = true,
        sortBy = DirectorySortBy.NAME,
        smartNoiseReduction = false,
      )
    assertTrue(raw.activeEntries.any { it.name == ".secret.env" })
    assertTrue(raw.collapsedNoiseDirs.isEmpty())
  }

  @Test
  fun `batch operations createFolder copy move rename packZip AES and delete work`() = runTest {
    val srcDir = FileManagerEngine.createFolder(tempRoot, "Work").getOrThrow()
    val copyDest = FileManagerEngine.createFolder(tempRoot, "Dest").getOrThrow()

    val a = File(srcDir, "alpha.txt").apply { writeText("AAA") }
    val b = File(srcDir, "beta.txt").apply { writeText("BBB") }
    val entries =
      listOf(
        FileManagerEngine.buildEntryFromFile(a, tempRoot),
        FileManagerEngine.buildEntryFromFile(b, tempRoot),
      )

    assertEquals(2, FileManagerEngine.copyEntries(entries, copyDest).getOrThrow())
    assertTrue(File(copyDest, "alpha.txt").exists())

    assertEquals(
      2,
      FileManagerEngine.batchRename(
        entries,
        BatchRenameRule(prefix = "v1_", addSequence = true, startIndex = 1),
      ).getOrThrow(),
    )
    assertTrue(File(srcDir, "v1_alpha_01.txt").exists())
    assertTrue(File(srcDir, "v1_beta_02.txt").exists())

    val renamedEntries =
      listOf(
        FileManagerEngine.buildEntryFromFile(File(srcDir, "v1_alpha_01.txt"), tempRoot),
      )
    val zipOut = File(tempRoot, "bundle.zip")
    FileManagerEngine.packEntriesToZip(renamedEntries, zipOut, zipPassword = "pass123").getOrThrow()
    val inspection = ArchiveEngine.inspectArchiveFile(zipOut, zipOut.name)
    assertTrue(inspection.requiresPassword)
    assertEquals(listOf("v1_alpha_01.txt"), inspection.entries.map { it.path })

    assertEquals(1, FileManagerEngine.deleteEntries(context, renamedEntries).getOrThrow())
    assertFalse(File(srcDir, "v1_alpha_01.txt").exists())
  }

  @Test
  fun `scanStorageWaste identifies files over 50MB and empty directories`() = runTest {
    val docs = File(tempRoot, "Docs").apply { mkdirs() }
    val bigFile = File(docs, "huge.iso")
    RandomAccessFile(bigFile, "rw").use { it.setLength(52L * 1024L * 1024L) }
    val emptyDir = File(tempRoot, "EmptyCache").apply { mkdirs() }

    val scan = FileManagerEngine.scanStorageWaste(tempRoot)
    assertEquals(1, scan.largeFilesOver50Mb.size)
    assertEquals("huge.iso", scan.largeFilesOver50Mb.first().name)
    assertTrue(scan.emptyDirectories.any { it.name == emptyDir.name })
  }

  @Test
  fun `TactKitDocumentsProvider serves roots recents children search and streams`() {
    val sub = File(tempRoot, "Download").apply { mkdirs() }
    val target = File(sub, "signing_cert.pem").apply { writeText("-----BEGIN CERTIFICATE-----") }

    val provider = Robolectric.setupContentProvider(TactKitDocumentsProvider::class.java)
    provider.queryRoots(null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      val idIdx = cursor.getColumnIndex(Root.COLUMN_ROOT_ID)
      assertEquals(TactKitDocumentsProvider.ROOT_ID, cursor.getString(idIdx))
    }

    var docId: String? = null
    provider.queryRecentDocuments(TactKitDocumentsProvider.ROOT_ID, null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      docId = cursor.getString(cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID))
      val name = cursor.getString(cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME))
      assertEquals("signing_cert.pem", name)
    }

    provider.querySearchDocuments(TactKitDocumentsProvider.ROOT_ID, ".pem", null).use { cursor ->
      assertEquals(1, cursor.count)
    }

    assertNotNull(docId)
    provider.openDocument(docId!!, "r", null).use { pfd ->
      val content = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
      assertTrue(content.contains("BEGIN CERTIFICATE"))
    }
    assertTrue(target.exists())
  }

  @Test
  fun `ToolCatalog and SettingsRepository expose file_manager and pinned dirs`() = runTest {
    val tool = ToolCatalog.findById("file_manager")
    assertNotNull(tool)
    assertEquals(ToolCategory.FILE_NETWORK, tool?.category)

    val repo = SettingsRepository(context)
    repo.togglePinnedDir("Download/Work")
    repo.addLinkedSafTreeUri("content://com.android.externalstorage.documents/tree/primary%3AQQ")
    val prefs = repo.preferencesFlow.first()
    assertTrue(prefs.pinnedDirs.contains("Download/Work"))
    assertEquals(1, prefs.linkedSafTreeUris.size)
  }
}
