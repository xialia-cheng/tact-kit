package com.xialia.tactkit.data.repository

import androidx.compose.runtime.Immutable
import com.xialia.tactkit.engine.ManagedFileEntry
import com.xialia.tactkit.engine.MessengerDirHint
import com.xialia.tactkit.engine.StorageOverview

/**
 * Application-scoped in-memory singleton cache for FileManager recent scan results and
 * storage snapshot metadata so re-entering FileManagerScreen renders immediately in 0ms
 * without re-traversing internal storage until explicitly refreshed or mutated.
 */
object FileCacheRepository {

  @Immutable
  data class RecentCacheKey(
    val hasPermission: Boolean,
    val linkedSafTreeUris: List<String>,
    val includeHidden: Boolean,
  )

  private val lock = Any()

  @Volatile private var cachedKey: RecentCacheKey? = null
  @Volatile private var cachedRecentFiles: List<ManagedFileEntry> = emptyList()
  @Volatile private var hasRecentCache: Boolean = false
  @Volatile private var recentDirty: Boolean = false

  @Volatile private var cachedStorageOverview: StorageOverview? = null
  @Volatile private var cachedMessengerDirs: List<MessengerDirHint>? = null
  @Volatile private var storageDirty: Boolean = true

  fun getRecentIfFresh(key: RecentCacheKey): List<ManagedFileEntry>? =
    synchronized(lock) {
      if (hasRecentCache && !recentDirty && cachedKey == key) {
        cachedRecentFiles
      } else {
        null
      }
    }

  fun peekRecent(): List<ManagedFileEntry> = cachedRecentFiles

  fun hasAnyRecentCache(): Boolean = hasRecentCache

  fun updateRecent(
    key: RecentCacheKey,
    files: List<ManagedFileEntry>,
  ) {
    synchronized(lock) {
      cachedKey = key
      cachedRecentFiles = files
      hasRecentCache = true
      recentDirty = false
    }
  }

  fun getStorageSnapshot(): Pair<StorageOverview?, List<MessengerDirHint>?> =
    synchronized(lock) { cachedStorageOverview to cachedMessengerDirs }

  fun isStorageSnapshotFresh(): Boolean =
    synchronized(lock) { !storageDirty && cachedStorageOverview != null && cachedMessengerDirs != null }

  fun updateStorageSnapshot(
    overview: StorageOverview,
    messengerDirs: List<MessengerDirHint>,
  ) {
    synchronized(lock) {
      cachedStorageOverview = overview
      cachedMessengerDirs = messengerDirs
      storageDirty = false
    }
  }

  fun markDirty() {
    synchronized(lock) {
      recentDirty = true
      storageDirty = true
    }
  }

  fun invalidate() = markDirty()

  fun clear() {
    synchronized(lock) {
      cachedKey = null
      cachedRecentFiles = emptyList()
      hasRecentCache = false
      recentDirty = false
      cachedStorageOverview = null
      cachedMessengerDirs = null
      storageDirty = true
    }
  }
}
