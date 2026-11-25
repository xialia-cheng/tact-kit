package com.xialia.tactkit.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DirectorySortBy(val label: String) {
  NAME("名称"),
  MODIFIED("修改时间"),
  SIZE("大小"),
  TYPE("类型"),
}

enum class SizeFilterMode(val label: String, val minBytes: Long) {
  ALL("全部大小", 0L),
  OVER_10_MB(">10 MB", 10L * 1024L * 1024L),
  OVER_50_MB(">50 MB", 50L * 1024L * 1024L),
}

data class ManagedFileEntry(
  val id: String,
  val name: String,
  val absolutePath: String?,
  val safUriString: String?,
  val relativePath: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val lastModifiedMs: Long,
  val extension: String,
  val category: FileCategory,
  val mimeType: String,
  val sourceLabel: String,
  val childCount: Int = 0,
  val isNoiseDirectory: Boolean = false,
) {
  val isSafBacked: Boolean get() = safUriString != null && absolutePath == null
}

data class StorageOverview(
  val totalBytes: Long,
  val usedBytes: Long,
  val availableBytes: Long,
)

data class MessengerDirHint(
  val label: String,
  val relativePath: String,
  val absolutePath: String,
  val fileCountEstimate: Int,
)

data class DirectoryListing(
  val currentDir: File,
  val currentRelativePath: String,
  val activeEntries: List<ManagedFileEntry>,
  val collapsedNoiseDirs: List<ManagedFileEntry>,
)

data class SpaceScanResult(
  val largeFilesOver50Mb: List<ManagedFileEntry>,
  val emptyDirectories: List<ManagedFileEntry>,
  val scannedFileCount: Int,
)

data class BatchRenameRule(
  val singleNewName: String? = null,
  val prefix: String = "",
  val suffix: String = "",
  val replaceFrom: String = "",
  val replaceTo: String = "",
  val addSequence: Boolean = false,
  val startIndex: Int = 1,
)

object FileManagerEngine {

  private const val MAX_TRAVERSAL_DEPTH = 16
  private const val MAX_SAF_DEPTH = 5
  private const val MAX_SAF_ITEMS_PER_TREE = 400
  private const val LARGE_FILE_THRESHOLD = 50L * 1024L * 1024L

  private val NOISE_SYSTEM_DIRS =
    setOf("alarms", "audiobooks", "notifications", "podcasts", "ringtones")

  private val SKIPPED_RELATIVE_PREFIXES =
    listOf("Android/data", "Android/obb")

  private val SKIPPED_DIR_NAMES =
    setOf(".thumbnails", ".trash", ".Trashes")

  private val MESSENGER_CANDIDATES =
    listOf(
      "Download/WeiXin" to "微信下载",
      "Download/QQ" to "QQ 下载",
      "Tencent/QQfile_recv" to "QQ 接收文件",
      "Android/data/com.tencent.mm/MicroMsg/Download" to "微信旧版下载",
      "Pictures/WeiXin" to "微信图片",
      "Pictures/QQ" to "QQ 图片",
    )

  fun defaultRootDir(): File = Environment.getExternalStorageDirectory()

  fun readStorageOverview(rootDir: File = defaultRootDir()): StorageOverview {
    return runCatching {
      val stat = StatFs(rootDir.absolutePath)
      val total = stat.totalBytes
      val avail = stat.availableBytes
      val used = (total - avail).coerceAtLeast(0L)
      StorageOverview(totalBytes = total, usedBytes = used, availableBytes = avail)
    }.getOrElse {
      StorageOverview(totalBytes = 0L, usedBytes = 0L, availableBytes = 0L)
    }
  }

  fun detectMessengerDirs(rootDir: File = defaultRootDir()): List<MessengerDirHint> {
    if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
    return MESSENGER_CANDIDATES.mapNotNull { (relPath, label) ->
      val dir = File(rootDir, relPath)
      if (dir.exists() && dir.isDirectory && dir.canRead()) {
        val count = dir.list()?.size ?: 0
        MessengerDirHint(
          label = label,
          relativePath = relPath,
          absolutePath = dir.absolutePath,
          fileCountEstimate = count,
        )
      } else {
        null
      }
    }
  }

  fun resolveSourceLabel(relativePath: String, parentName: String, isSaf: Boolean = false): String {
    val normalized = relativePath.replace('\\', '/').trimStart('/')
    val lower = normalized.lowercase(Locale.ROOT)
    return when {
      lower.startsWith("download/qq") || lower.contains("/qqfile_recv") || lower.startsWith("pictures/qq") ->
        "来自 QQ"
      lower.startsWith("download/weixin") || lower.contains("micromsg/download") || lower.startsWith("pictures/weixin") ->
        "来自 微信"
      lower.startsWith("download/") || lower == "download" -> "来自 Download"
      lower.startsWith("documents/") || lower == "documents" -> "来自 Documents"
      lower.startsWith("pictures/") || lower == "pictures" -> "来自 Pictures"
      lower.startsWith("dcim/") -> "来自 相册"
      lower.startsWith("movies/") -> "来自 Movies"
      lower.startsWith("music/") -> "来自 Music"
      isSaf -> if (parentName.isNotBlank()) "来自 $parentName" else "来自 关联目录"
      parentName.isNotBlank() -> "来自 $parentName"
      else -> "来自 根目录"
    }
  }

  fun toRelativePath(rootDir: File, target: File): String {
    val rootPath = rootDir.absolutePath.trimEnd('/')
    val targetPath = target.absolutePath
    return if (targetPath == rootPath) {
      ""
    } else if (targetPath.startsWith("$rootPath/")) {
      targetPath.removePrefix("$rootPath/")
    } else {
      target.name
    }
  }

  fun buildEntryFromFile(
    file: File,
    rootDir: File = defaultRootDir(),
    isNoiseDirectory: Boolean = false,
  ): ManagedFileEntry {
    val isDir = file.isDirectory
    val name = file.name.ifBlank { rootDir.name }
    val rel = toRelativePath(rootDir, file)
    val ext = if (isDir) "" else FileCategoryClassifier.extractDisplayExtension(name)
    val category = if (isDir) FileCategory.OTHER else FileCategoryClassifier.classify(name)
    val mime = FileCategoryClassifier.guessMimeType(name, isDirectory = isDir)
    val parentName = file.parentFile?.name.orEmpty()
    val source = resolveSourceLabel(rel, parentName, isSaf = false)
    val childCount = if (isDir) (file.list()?.size ?: 0) else 0
    return ManagedFileEntry(
      id = "file:${file.absolutePath}",
      name = name,
      absolutePath = file.absolutePath,
      safUriString = null,
      relativePath = rel,
      isDirectory = isDir,
      sizeBytes = if (isDir) 0L else file.length(),
      lastModifiedMs = file.lastModified(),
      extension = ext,
      category = category,
      mimeType = mime,
      sourceLabel = source,
      childCount = childCount,
      isNoiseDirectory = isNoiseDirectory,
    )
  }

  suspend fun scanRecentFiles(
    context: Context,
    rootDir: File = defaultRootDir(),
    linkedSafTreeUris: List<String> = emptyList(),
    limit: Int = 600,
    includeHidden: Boolean = false,
  ): List<ManagedFileEntry> =
    withContext(Dispatchers.IO) {
      scanRecentFilesSync(
        context = context,
        rootDir = rootDir,
        linkedSafTreeUris = linkedSafTreeUris,
        limit = limit,
        includeHidden = includeHidden,
      )
    }

  fun scanRecentFilesSync(
    context: Context,
    rootDir: File = defaultRootDir(),
    linkedSafTreeUris: List<String> = emptyList(),
    limit: Int = 600,
    includeHidden: Boolean = false,
  ): List<ManagedFileEntry> {
    val results = ArrayList<ManagedFileEntry>(256)
    val seenIds = HashSet<String>(512)

    if (rootDir.exists() && rootDir.isDirectory && rootDir.canRead()) {
      val stack = ArrayDeque<Pair<File, Int>>()
      val visitedCanonical = HashSet<String>()
      stack.addLast(rootDir to 0)
      runCatching { visitedCanonical.add(rootDir.canonicalPath) }

      while (stack.isNotEmpty()) {
        val (dir, depth) = stack.removeLast()
        val children = dir.listFiles() ?: continue
        for (child in children) {
          val childName = child.name
          if (!includeHidden && childName.startsWith(".")) continue
          if (child.isDirectory) {
            if (depth >= MAX_TRAVERSAL_DEPTH) continue
            if (childName in SKIPPED_DIR_NAMES) continue
            val rel = toRelativePath(rootDir, child)
            if (shouldSkipRelativePath(rel)) continue
            val canon = runCatching { child.canonicalPath }.getOrNull() ?: continue
            if (visitedCanonical.add(canon)) {
              stack.addLast(child to (depth + 1))
            }
          } else if (child.isFile) {
            val entry = buildEntryFromFile(child, rootDir)
            if (seenIds.add(entry.id)) {
              results.add(entry)
            }
          }
        }
      }
    }

    linkedSafTreeUris.forEach { treeUriStr ->
      val safEntries = querySafTreeFiles(context, treeUriStr, includeHidden)
      for (entry in safEntries) {
        val signatureKey = "saf_sig:${entry.name}:${entry.sizeBytes}:${entry.lastModifiedMs}"
        if (seenIds.add(entry.id) && seenIds.add(signatureKey)) {
          results.add(entry)
        }
      }
    }

    results.sortByDescending { it.lastModifiedMs }
    return if (results.size > limit) results.take(limit) else results
  }

  private fun querySafTreeFiles(
    context: Context,
    treeUriStr: String,
    includeHidden: Boolean,
  ): List<ManagedFileEntry> {
    val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return emptyList()
    val resolver = context.contentResolver
    val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return emptyList()
    val rootLabel = rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "关联目录" }
    val out = ArrayList<ManagedFileEntry>(64)
    val queue = ArrayDeque<Triple<String, String, Int>>()
    queue.addLast(Triple(rootDocId, rootLabel, 0))

    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
      )

    while (queue.isNotEmpty() && out.size < MAX_SAF_ITEMS_PER_TREE) {
      val (docId, parentRel, depth) = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
      runCatching {
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
          val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
          val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
          val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
          val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

          while (cursor.moveToNext() && out.size < MAX_SAF_ITEMS_PER_TREE) {
            val childId = if (idCol >= 0) cursor.getString(idCol) else continue
            val name = if (nameCol >= 0) cursor.getString(nameCol) ?: continue else continue
            if (!includeHidden && name.startsWith(".")) continue
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val relPath = "$parentRel/$name"
            if (isDir) {
              if (depth < MAX_SAF_DEPTH) {
                queue.addLast(Triple(childId, relPath, depth + 1))
              }
            } else {
              val size = if (sizeCol >= 0) cursor.getLong(sizeCol).coerceAtLeast(0L) else 0L
              val mod = if (modCol >= 0) cursor.getLong(modCol).coerceAtLeast(0L) else 0L
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
              val ext = FileCategoryClassifier.extractDisplayExtension(name)
              val cat = FileCategoryClassifier.classify(name)
              val resolvedMime = mime.ifBlank { FileCategoryClassifier.guessMimeType(name, false) }
              out.add(
                ManagedFileEntry(
                  id = "saf:$docUri",
                  name = name,
                  absolutePath = null,
                  safUriString = docUri.toString(),
                  relativePath = relPath,
                  isDirectory = false,
                  sizeBytes = size,
                  lastModifiedMs = mod,
                  extension = ext,
                  category = cat,
                  mimeType = resolvedMime,
                  sourceLabel = resolveSourceLabel(relPath, rootLabel, isSaf = true),
                )
              )
            }
          }
        }
      }
    }
    return out
  }

  suspend fun listDirectory(
    dir: File,
    rootDir: File = defaultRootDir(),
    showHidden: Boolean = false,
    sortBy: DirectorySortBy = DirectorySortBy.NAME,
    smartNoiseReduction: Boolean = true,
  ): DirectoryListing =
    withContext(Dispatchers.IO) {
      listDirectorySync(dir, rootDir, showHidden, sortBy, smartNoiseReduction)
    }

  fun listDirectorySync(
    dir: File,
    rootDir: File = defaultRootDir(),
    showHidden: Boolean = false,
    sortBy: DirectorySortBy = DirectorySortBy.NAME,
    smartNoiseReduction: Boolean = true,
  ): DirectoryListing {
    val rawChildren = dir.listFiles()?.toList().orEmpty()
    val filtered =
      rawChildren.filter { child ->
        showHidden || !child.name.startsWith(".")
      }

    val active = mutableListOf<ManagedFileEntry>()
    val noise = mutableListOf<ManagedFileEntry>()

    for (child in filtered) {
      val isDir = child.isDirectory
      val isNoise =
        if (isDir) {
          val lowerName = child.name.lowercase(Locale.ROOT)
          val isSystemMediaNoise = lowerName in NOISE_SYSTEM_DIRS
          val isEmpty = (child.listFiles()?.count { showHidden || !it.name.startsWith(".") } ?: 0) == 0
          isSystemMediaNoise || isEmpty
        } else {
          false
        }
      val entry = buildEntryFromFile(child, rootDir, isNoiseDirectory = isNoise)
      if (smartNoiseReduction && isNoise) {
        noise.add(entry)
      } else {
        active.add(entry)
      }
    }

    val comparator = directoryComparator(sortBy)
    active.sortWith(comparator)
    noise.sortWith(comparator)

    return DirectoryListing(
      currentDir = dir,
      currentRelativePath = toRelativePath(rootDir, dir),
      activeEntries = active,
      collapsedNoiseDirs = noise,
    )
  }

  private fun directoryComparator(sortBy: DirectorySortBy): Comparator<ManagedFileEntry> {
    return Comparator { a, b ->
      if (a.isDirectory != b.isDirectory) {
        return@Comparator if (a.isDirectory) -1 else 1
      }
      val cmp =
        when (sortBy) {
          DirectorySortBy.NAME -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
          DirectorySortBy.MODIFIED -> b.lastModifiedMs.compareTo(a.lastModifiedMs)
          DirectorySortBy.SIZE -> b.sizeBytes.compareTo(a.sizeBytes)
          DirectorySortBy.TYPE -> {
            val typeCmp = a.extension.compareTo(b.extension)
            if (typeCmp != 0) typeCmp else a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
          }
        }
      if (cmp != 0) cmp else a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
    }
  }

  suspend fun searchFiles(
    context: Context,
    query: String,
    rootDir: File = defaultRootDir(),
    linkedSafTreeUris: List<String> = emptyList(),
    limit: Int = 250,
    includeHidden: Boolean = false,
  ): List<ManagedFileEntry> =
    withContext(Dispatchers.IO) {
      searchFilesSync(context, query, rootDir, linkedSafTreeUris, limit, includeHidden)
    }

  fun searchFilesSync(
    context: Context,
    query: String,
    rootDir: File = defaultRootDir(),
    linkedSafTreeUris: List<String> = emptyList(),
    limit: Int = 250,
    includeHidden: Boolean = false,
  ): List<ManagedFileEntry> {
    val cleanQuery = query.trim().lowercase(Locale.ROOT)
    if (cleanQuery.isEmpty()) return emptyList()
    val extQuery = if (cleanQuery.startsWith(".")) cleanQuery.removePrefix(".") else null
    val allRecent =
      scanRecentFilesSync(
        context = context,
        rootDir = rootDir,
        linkedSafTreeUris = linkedSafTreeUris,
        limit = 3000,
        includeHidden = includeHidden,
      )
    return allRecent
      .filter { entry ->
        if (extQuery != null) {
          entry.extension == extQuery || entry.name.lowercase(Locale.ROOT).contains(cleanQuery)
        } else {
          entry.name.lowercase(Locale.ROOT).contains(cleanQuery) ||
            entry.relativePath.lowercase(Locale.ROOT).contains(cleanQuery) ||
            entry.extension == cleanQuery
        }
      }
      .take(limit)
  }

  suspend fun scanStorageWaste(rootDir: File = defaultRootDir()): SpaceScanResult =
    withContext(Dispatchers.IO) {
      val largeFiles = mutableListOf<ManagedFileEntry>()
      val emptyDirs = mutableListOf<ManagedFileEntry>()
      var fileCount = 0

      if (rootDir.exists() && rootDir.isDirectory && rootDir.canRead()) {
        val stack = ArrayDeque<Pair<File, Int>>()
        val visited = HashSet<String>()
        stack.addLast(rootDir to 0)
        runCatching { visited.add(rootDir.canonicalPath) }

        while (stack.isNotEmpty()) {
          val (dir, depth) = stack.removeLast()
          val children = dir.listFiles() ?: continue
          if (dir != rootDir && children.isEmpty()) {
            val rel = toRelativePath(rootDir, dir)
            if (!shouldSkipRelativePath(rel) && !dir.name.startsWith(".")) {
              emptyDirs.add(buildEntryFromFile(dir, rootDir, isNoiseDirectory = true))
            }
            continue
          }
          for (child in children) {
            if (child.name in SKIPPED_DIR_NAMES) continue
            if (child.isDirectory) {
              if (depth >= MAX_TRAVERSAL_DEPTH) continue
              val rel = toRelativePath(rootDir, child)
              if (shouldSkipRelativePath(rel)) continue
              val canon = runCatching { child.canonicalPath }.getOrNull() ?: continue
              if (visited.add(canon)) {
                stack.addLast(child to (depth + 1))
              }
            } else if (child.isFile) {
              fileCount++
              if (child.length() >= LARGE_FILE_THRESHOLD) {
                largeFiles.add(buildEntryFromFile(child, rootDir))
              }
            }
          }
        }
      }

      largeFiles.sortByDescending { it.sizeBytes }
      emptyDirs.sortBy { it.relativePath.lowercase(Locale.ROOT) }
      SpaceScanResult(
        largeFilesOver50Mb = largeFiles,
        emptyDirectories = emptyDirs,
        scannedFileCount = fileCount,
      )
    }

  suspend fun createFolder(parentDir: File, folderName: String): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitized = folderName.trim().replace('/', '_').replace('\\', '_')
        require(sanitized.isNotEmpty() && sanitized != "." && sanitized != "..") {
          "请输入合法的文件夹名称"
        }
        val target = File(parentDir, sanitized)
        require(!target.exists()) { "同名文件或文件夹已存在：$sanitized" }
        check(target.mkdirs()) { "创建文件夹失败，请检查存储权限" }
        target
      }
    }

  suspend fun copyEntries(entries: List<ManagedFileEntry>, targetDir: File): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(targetDir.exists() && targetDir.isDirectory) { "目标目录不存在" }
        var count = 0
        entries.forEach { entry ->
          val src = entry.absolutePath?.let(::File) ?: return@forEach
          if (!src.exists()) return@forEach
          if (src.isDirectory && targetDir.canonicalPath.startsWith(src.canonicalPath)) {
            error("不能将文件夹复制到自身内部：${src.name}")
          }
          val dst = uniqueSiblingTarget(targetDir, src.name)
          src.copyRecursively(dst, overwrite = false)
          count++
        }
        count
      }
    }

  suspend fun moveEntries(entries: List<ManagedFileEntry>, targetDir: File): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(targetDir.exists() && targetDir.isDirectory) { "目标目录不存在" }
        var count = 0
        entries.forEach { entry ->
          val src = entry.absolutePath?.let(::File) ?: return@forEach
          if (!src.exists()) return@forEach
          if (src.parentFile?.canonicalPath == targetDir.canonicalPath) return@forEach
          if (src.isDirectory && targetDir.canonicalPath.startsWith(src.canonicalPath)) {
            error("不能将文件夹移动到自身内部：${src.name}")
          }
          val dst = uniqueSiblingTarget(targetDir, src.name)
          if (!src.renameTo(dst)) {
            src.copyRecursively(dst, overwrite = false)
            check(src.deleteRecursively()) { "无法删除原文件：${src.name}" }
          }
          count++
        }
        count
      }
    }

  suspend fun batchRename(entries: List<ManagedFileEntry>, rule: BatchRenameRule): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        var count = 0
        if (entries.size == 1 && !rule.singleNewName.isNullOrBlank()) {
          val src = entries.first().absolutePath?.let(::File) ?: error("仅支持重命名本地存储文件")
          val sanitized = rule.singleNewName.trim().replace('/', '_').replace('\\', '_')
          require(sanitized.isNotEmpty() && sanitized != "." && sanitized != "..") { "文件名不合法" }
          val dst = File(src.parentFile, sanitized)
          require(!dst.exists() || dst.absolutePath == src.absolutePath) { "目标文件名已存在" }
          if (dst.absolutePath != src.absolutePath) {
            check(src.renameTo(dst)) { "重命名失败" }
          }
          return@runCatching 1
        }

        entries.forEachIndexed { idx, entry ->
          val src = entry.absolutePath?.let(::File) ?: return@forEachIndexed
          val parent = src.parentFile ?: return@forEachIndexed
          val dotIdx = src.name.lastIndexOf('.')
          val hasExt = !src.isDirectory && dotIdx > 0
          val rawStem = if (hasExt) src.name.substring(0, dotIdx) else src.name
          val extPart = if (hasExt) src.name.substring(dotIdx) else ""

          val replacedStem =
            if (rule.replaceFrom.isNotEmpty()) {
              rawStem.replace(rule.replaceFrom, rule.replaceTo)
            } else {
              rawStem
            }
          val seqPart =
            if (rule.addSequence) {
              "_%02d".format(Locale.ROOT, rule.startIndex + idx)
            } else {
              ""
            }
          val candidateName = "${rule.prefix}${replacedStem}${rule.suffix}${seqPart}${extPart}"
            .trim()
            .replace('/', '_')
          if (candidateName.isNotEmpty() && candidateName != src.name) {
            val dst = uniqueSiblingTarget(parent, candidateName)
            if (src.renameTo(dst)) count++
          }
        }
        count
      }
    }

  suspend fun deleteEntries(context: Context, entries: List<ManagedFileEntry>): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        var count = 0
        entries.forEach { entry ->
          val path = entry.absolutePath
          if (path != null) {
            val f = File(path)
            if (f.exists() && f.deleteRecursively()) count++
          } else if (entry.safUriString != null) {
            val uri = Uri.parse(entry.safUriString)
            if (runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)) {
              count++
            }
          }
        }
        count
      }
    }

  suspend fun packEntriesToZip(
    entries: List<ManagedFileEntry>,
    outputZipFile: File,
    zipPassword: String? = null,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val localFiles = mutableListOf<Pair<String, File>>()
        val usedNames = HashSet<String>()

        fun addFileRecursive(basePrefix: String, file: File) {
          if (file.isFile) {
            val relName = if (basePrefix.isEmpty()) file.name else "$basePrefix/${file.name}"
            val unique = if (usedNames.add(relName)) relName else "${System.nanoTime()}_$relName"
            localFiles.add(unique to file)
          } else if (file.isDirectory) {
            val nextPrefix = if (basePrefix.isEmpty()) file.name else "$basePrefix/${file.name}"
            file.listFiles()?.forEach { child -> addFileRecursive(nextPrefix, child) }
          }
        }

        entries.forEach { entry ->
          val f = entry.absolutePath?.let(::File)
          if (f != null && f.exists()) {
            addFileRecursive("", f)
          }
        }
        require(localFiles.isNotEmpty()) { "所选项目中没有可打包的本地文件" }
        outputZipFile.parentFile?.mkdirs()
        ArchiveEngine.packLocalFiles(
          namedFiles = localFiles,
          outputFile = outputZipFile,
          format = PackArchiveFormat.ZIP,
          zipPassword = zipPassword?.takeIf { it.isNotEmpty() },
        )
        outputZipFile
      }
    }

  fun toShareableUri(context: Context, entry: ManagedFileEntry): Uri? {
    val path = entry.absolutePath
    if (path != null) {
      val file = File(path)
      if (!file.exists()) return null
      return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      }.getOrElse { Uri.fromFile(file) }
    }
    return entry.safUriString?.let { Uri.parse(it) }
  }

  private fun uniqueSiblingTarget(parentDir: File, desiredName: String): File {
    val candidate = File(parentDir, desiredName)
    if (!candidate.exists()) return candidate
    val dotIdx = desiredName.lastIndexOf('.')
    val stem = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
    val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
    var idx = 1
    while (true) {
      val next = File(parentDir, "${stem}_$idx$ext")
      if (!next.exists()) return next
      idx++
    }
  }

  private fun shouldSkipRelativePath(relativePath: String): Boolean {
    val clean = relativePath.replace('\\', '/').trimStart('/')
    return SKIPPED_RELATIVE_PREFIXES.any { prefix ->
      clean.equals(prefix, ignoreCase = true) ||
        clean.startsWith("$prefix/", ignoreCase = true)
    }
  }

  fun formatRelativeTime(lastModifiedMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (lastModifiedMs <= 0L) return "未知时间"
    val diff = (nowMs - lastModifiedMs).coerceAtLeast(0L)
    val minuteMs = 60_000L
    val hourMs = 60L * minuteMs
    val dayMs = 24L * hourMs
    return when {
      diff < minuteMs -> "刚刚"
      diff < hourMs -> "${diff / minuteMs} 分钟前"
      diff < dayMs -> "${diff / hourMs} 小时前"
      diff < 2L * dayMs -> "昨天"
      diff < 7L * dayMs -> "${diff / dayMs} 天前"
      else -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(lastModifiedMs))
    }
  }
}
