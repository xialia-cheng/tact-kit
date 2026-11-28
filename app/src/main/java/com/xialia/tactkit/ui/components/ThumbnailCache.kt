package com.xialia.tactkit.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Global asynchronous thumbnail LRU cache (~24 MB) with bounded Dispatchers.IO concurrency
 * and BitmapFactory inJustDecodeBounds subsampling down to ~160 px.
 */
object ThumbnailCache {
  private const val CACHE_MAX_SIZE_KB = 24 * 1024
  const val DEFAULT_TARGET_PX = 160

  private val memoryCache =
    object : LruCache<String, Bitmap>(CACHE_MAX_SIZE_KB) {
      override fun sizeOf(key: String, value: Bitmap): Int =
        (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val decodeDispatcher = Dispatchers.IO.limitedParallelism(3)

  private val decodeSemaphore = Semaphore(3)

  fun buildKey(entryId: String, lastModifiedMs: Long, targetPx: Int = DEFAULT_TARGET_PX): String =
    "$entryId|$lastModifiedMs|$targetPx"

  fun getCached(key: String?): Bitmap? =
    key?.takeIf { it.isNotBlank() }?.let { memoryCache.get(it) }?.takeIf { !it.isRecycled }

  fun putCached(key: String, bitmap: Bitmap) {
    if (key.isNotBlank() && !bitmap.isRecycled) {
      memoryCache.put(key, bitmap)
    }
  }

  fun clear() {
    memoryCache.evictAll()
  }

  suspend fun getOrLoadImageThumbnail(
    context: Context,
    cacheKey: String,
    absolutePath: String?,
    safUriString: String?,
    extension: String = "",
    targetPx: Int = DEFAULT_TARGET_PX,
  ): Bitmap? {
    getCached(cacheKey)?.let { return it }
    val appContext = context.applicationContext
    return withContext(decodeDispatcher) {
      getCached(cacheKey)?.let { return@withContext it }
      decodeSemaphore.withPermit {
        getCached(cacheKey)?.let { return@withPermit it }
        val decoded =
          decodeScaledImageThumbnail(
            context = appContext,
            absolutePath = absolutePath,
            safUriString = safUriString,
            extension = extension,
            targetPx = targetPx,
          )
        if (decoded != null && !decoded.isRecycled) {
          putCached(cacheKey, decoded)
        }
        decoded
      }
    }
  }

  suspend fun getOrLoadVideoThumbnail(
    context: Context,
    cacheKey: String,
    absolutePath: String?,
    safUriString: String?,
    targetPx: Int = DEFAULT_TARGET_PX,
  ): Bitmap? {
    getCached(cacheKey)?.let { return it }
    val appContext = context.applicationContext
    return withContext(decodeDispatcher) {
      getCached(cacheKey)?.let { return@withContext it }
      decodeSemaphore.withPermit {
        getCached(cacheKey)?.let { return@withPermit it }
        val decoded =
          decodeScaledVideoThumbnail(
            context = appContext,
            absolutePath = absolutePath,
            safUriString = safUriString,
            targetPx = targetPx,
          )
        if (decoded != null && !decoded.isRecycled) {
          putCached(cacheKey, decoded)
        }
        decoded
      }
    }
  }

  private fun decodeScaledImageThumbnail(
    context: Context,
    absolutePath: String?,
    safUriString: String?,
    extension: String,
    targetPx: Int,
  ): Bitmap? =
    runCatching {
      val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      val extLower = extension.lowercase(Locale.ROOT)
      val preferredConfig =
        if (extLower in setOf("jpg", "jpeg", "bmp", "heic", "heif")) {
          Bitmap.Config.RGB_565
        } else {
          Bitmap.Config.ARGB_8888
        }

      if (absolutePath != null) {
        val file = File(absolutePath)
        if (!file.exists() || !file.canRead()) return null
        BitmapFactory.decodeFile(absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val decodeOpts =
          BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
            inPreferredConfig = preferredConfig
          }
        val sampled = BitmapFactory.decodeFile(absolutePath, decodeOpts) ?: return null
        downscaleIfOversized(sampled, targetPx)
      } else if (safUriString != null) {
        val uri = Uri.parse(safUriString)
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val decodeOpts =
          BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, targetPx)
            inPreferredConfig = preferredConfig
          }
        val sampled =
          context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
          } ?: return null
        downscaleIfOversized(sampled, targetPx)
      } else {
        null
      }
    }.getOrNull()

  private fun decodeScaledVideoThumbnail(
    context: Context,
    absolutePath: String?,
    safUriString: String?,
    targetPx: Int,
  ): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return runCatching {
      if (absolutePath != null) {
        retriever.setDataSource(absolutePath)
      } else if (safUriString != null) {
        retriever.setDataSource(context, Uri.parse(safUriString))
      } else {
        return null
      }

      val raw =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
          val vw =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
              ?: 0
          val vh =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
              ?: 0
          if (vw > 0 && vh > 0) {
            val scale = (targetPx.toFloat() / maxOf(vw, vh)).coerceAtMost(1f)
            val dstW = (vw * scale).toInt().coerceAtLeast(32)
            val dstH = (vh * scale).toInt().coerceAtLeast(32)
            retriever.getScaledFrameAtTime(
              500_000L,
              MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
              dstW,
              dstH,
            )
          } else {
            null
          }
        } else {
          null
        }
          ?: retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          ?: retriever.frameAtTime
          ?: return null

      downscaleIfOversized(raw, targetPx)
    }.getOrNull().also {
      runCatching { retriever.release() }
    }
  }

  private fun downscaleIfOversized(bitmap: Bitmap, targetPx: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= targetPx * 2) return bitmap
    val ratio = targetPx.toFloat() / maxSide.toFloat()
    val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return runCatching {
      val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
      if (scaled !== bitmap) bitmap.recycle()
      scaled
    }.getOrDefault(bitmap)
  }

  private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
    var inSampleSize = 1
    if (height > targetPx || width > targetPx) {
      val halfH = height / 2
      val halfW = width / 2
      while (halfH / inSampleSize >= targetPx && halfW / inSampleSize >= targetPx) {
        inSampleSize *= 2
      }
    }
    return inSampleSize.coerceAtLeast(1)
  }
}
