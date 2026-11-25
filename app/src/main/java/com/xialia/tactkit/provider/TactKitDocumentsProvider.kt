package com.xialia.tactkit.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.xialia.tactkit.R
import com.xialia.tactkit.engine.FileCategoryClassifier
import com.xialia.tactkit.engine.FileManagerEngine
import com.xialia.tactkit.engine.ManagedFileEntry
import java.io.File
import java.io.FileNotFoundException

class TactKitDocumentsProvider : DocumentsProvider() {

  companion object {
    const val ROOT_ID = "tactkit_all_files"
    const val ROOT_DOC_ID = "root"
    private const val FILE_DOC_PREFIX = "file:"

    private val DEFAULT_ROOT_PROJECTION =
      arrayOf(
        Root.COLUMN_ROOT_ID,
        Root.COLUMN_FLAGS,
        Root.COLUMN_ICON,
        Root.COLUMN_TITLE,
        Root.COLUMN_SUMMARY,
        Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_MIME_TYPES,
        Root.COLUMN_AVAILABLE_BYTES,
      )

    private val DEFAULT_DOCUMENT_PROJECTION =
      arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE,
        Document.COLUMN_SUMMARY,
      )

    @Volatile
    internal var rootDirOverrideForTest: File? = null
  }

  override fun onCreate(): Boolean = true

  private fun rootDir(): File = rootDirOverrideForTest ?: FileManagerEngine.defaultRootDir()

  private fun docIdForFile(file: File): String {
    val root = rootDir()
    return if (file.absolutePath == root.absolutePath) {
      ROOT_DOC_ID
    } else {
      "$FILE_DOC_PREFIX${file.absolutePath}"
    }
  }

  private fun fileForDocId(documentId: String): File {
    val root = rootDir()
    val candidate =
      when {
        documentId == ROOT_DOC_ID -> root
        documentId.startsWith(FILE_DOC_PREFIX) -> File(documentId.removePrefix(FILE_DOC_PREFIX))
        else -> throw FileNotFoundException("未知文档 ID：$documentId")
      }
    val rootCanon =
      runCatching { root.canonicalPath }.getOrElse { throw FileNotFoundException("存储根目录不可访问") }
    val targetCanon =
      runCatching { candidate.canonicalPath }.getOrElse { throw FileNotFoundException("路径无效") }
    if (targetCanon != rootCanon && !targetCanon.startsWith("$rootCanon/")) {
      throw FileNotFoundException("越权路径访问被拒绝")
    }
    if (!candidate.exists()) {
      throw FileNotFoundException("文件不存在：$targetCanon")
    }
    return candidate
  }

  override fun queryRoots(projection: Array<out String>?): Cursor {
    val cols = projection ?: DEFAULT_ROOT_PROJECTION
    val cursor = MatrixCursor(cols)
    val ctx = context
    val title = ctx?.getString(R.string.documents_provider_title) ?: "简工坊 · 全盘文件"
    val root = rootDir()
    val avail = FileManagerEngine.readStorageOverview(root).availableBytes
    val flags =
      Root.FLAG_LOCAL_ONLY or
        Root.FLAG_SUPPORTS_RECENTS or
        Root.FLAG_SUPPORTS_SEARCH or
        Root.FLAG_SUPPORTS_IS_CHILD

    cursor.newRow().apply {
      add(Root.COLUMN_ROOT_ID, ROOT_ID)
      add(Root.COLUMN_FLAGS, flags)
      add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
      add(Root.COLUMN_TITLE, title)
      add(Root.COLUMN_SUMMARY, "Real-File 检索 · .jks/.pem/.apk 与聊天目录")
      add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
      add(Root.COLUMN_MIME_TYPES, "*/*")
      add(Root.COLUMN_AVAILABLE_BYTES, avail)
    }
    return cursor
  }

  override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
    val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
    val cursor = MatrixCursor(cols)
    val file = fileForDocId(documentId)
    includeFileRow(cursor, file, documentId)
    return cursor
  }

  override fun queryChildDocuments(
    parentDocumentId: String,
    projection: Array<out String>?,
    sortOrder: String?,
  ): Cursor {
    val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
    val cursor = MatrixCursor(cols)
    val parent = fileForDocId(parentDocumentId)
    val listing =
      FileManagerEngine.listDirectorySync(
        dir = parent,
        rootDir = rootDir(),
        showHidden = false,
        smartNoiseReduction = false,
      )
    listing.activeEntries.forEach { entry ->
      val f = entry.absolutePath?.let(::File) ?: return@forEach
      includeFileRow(cursor, f, docIdForFile(f))
    }
    return cursor
  }

  override fun queryRecentDocuments(
    rootId: String,
    projection: Array<out String>?,
  ): Cursor {
    val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
    val cursor = MatrixCursor(cols)
    val ctx = context ?: return cursor
    val recents =
      FileManagerEngine.scanRecentFilesSync(
        context = ctx,
        rootDir = rootDir(),
        linkedSafTreeUris = emptyList(),
        limit = 64,
        includeHidden = false,
      )
    recents.forEach { entry ->
      includeEntryRow(cursor, entry)
    }
    return cursor
  }

  override fun querySearchDocuments(
    rootId: String,
    query: String,
    projection: Array<out String>?,
  ): Cursor {
    val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
    val cursor = MatrixCursor(cols)
    val ctx = context ?: return cursor
    val matches =
      FileManagerEngine.searchFilesSync(
        context = ctx,
        query = query,
        rootDir = rootDir(),
        linkedSafTreeUris = emptyList(),
        limit = 80,
        includeHidden = false,
      )
    matches.forEach { entry ->
      includeEntryRow(cursor, entry)
    }
    return cursor
  }

  override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
    return runCatching {
      val parentCanon = fileForDocId(parentDocumentId).canonicalPath
      val docCanon = fileForDocId(documentId).canonicalPath
      docCanon == parentCanon || docCanon.startsWith("$parentCanon/")
    }.getOrDefault(false)
  }

  override fun openDocument(
    documentId: String,
    mode: String,
    signal: CancellationSignal?,
  ): ParcelFileDescriptor {
    val file = fileForDocId(documentId)
    if (file.isDirectory) {
      throw FileNotFoundException("无法以流方式打开文件夹")
    }
    val accessMode = ParcelFileDescriptor.parseMode(mode)
    return ParcelFileDescriptor.open(file, accessMode)
  }

  private fun includeFileRow(cursor: MatrixCursor, file: File, docId: String) {
    val isDir = file.isDirectory
    val name = if (docId == ROOT_DOC_ID) "内部存储" else file.name
    val mime = FileCategoryClassifier.guessMimeType(name, isDirectory = isDir)
    val flags =
      when {
        isDir -> 0
        mime.startsWith("image/") -> Document.FLAG_SUPPORTS_THUMBNAIL
        else -> 0
      }
    cursor.newRow().apply {
      add(Document.COLUMN_DOCUMENT_ID, docId)
      add(Document.COLUMN_MIME_TYPE, mime)
      add(Document.COLUMN_DISPLAY_NAME, name)
      add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
      add(Document.COLUMN_FLAGS, flags)
      add(Document.COLUMN_SIZE, if (isDir) 0L else file.length())
      add(
        Document.COLUMN_SUMMARY,
        if (isDir) "目录" else FileManagerEngine.toRelativePath(rootDir(), file),
      )
    }
  }

  private fun includeEntryRow(cursor: MatrixCursor, entry: ManagedFileEntry) {
    val path = entry.absolutePath ?: return
    val docId = "$FILE_DOC_PREFIX$path"
    val flags = if (entry.mimeType.startsWith("image/")) Document.FLAG_SUPPORTS_THUMBNAIL else 0
    cursor.newRow().apply {
      add(Document.COLUMN_DOCUMENT_ID, docId)
      add(Document.COLUMN_MIME_TYPE, entry.mimeType)
      add(Document.COLUMN_DISPLAY_NAME, entry.name)
      add(Document.COLUMN_LAST_MODIFIED, entry.lastModifiedMs)
      add(Document.COLUMN_FLAGS, flags)
      add(Document.COLUMN_SIZE, entry.sizeBytes)
      add(Document.COLUMN_SUMMARY, "${entry.sourceLabel} · ${entry.relativePath}")
    }
  }
}
