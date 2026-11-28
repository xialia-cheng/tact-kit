package com.xialia.tactkit.data.repository

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.OkHttpClient
import okhttp3.Request

@Immutable
data class ReleaseInfo(
  val tagName: String,
  val title: String,
  val body: String,
  val htmlUrl: String,
  val apkDownloadUrl: String,
  val isUpdateAvailable: Boolean,
) {
  val officialDownloadUrl: String
    get() = apkDownloadUrl.ifBlank { htmlUrl }

  val fastestMirrorDownloadUrl: String
    get() = "https://ghfast.top/$officialDownloadUrl"

  val stableMirrorDownloadUrl: String
    get() = "https://gh-proxy.com/$officialDownloadUrl"
}

@Immutable
sealed interface ApkDownloadState {
  data object Idle : ApkDownloadState

  @Immutable
  data class Downloading(
    val sourceLabel: String,
    val url: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val speedBytesPerSec: Long,
  ) : ApkDownloadState

  @Immutable
  data class Completed(
    val apkFile: File,
    val totalBytes: Long,
  ) : ApkDownloadState

  @Immutable
  data class Failed(
    val sourceLabel: String,
    val url: String,
    val message: String,
  ) : ApkDownloadState
}

class AppUpdateRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(2500, TimeUnit.MILLISECONDS)
      .callTimeout(2500, TimeUnit.MILLISECONDS)
      .build(),
) {

  private val json = Json { ignoreUnknownKeys = true }

  private val downloadClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }

  /**
   * Dual-path concurrent racing: requests GitHub official API and gh-proxy.com mirror simultaneously.
   * Whichever returns a valid Release JSON first immediately wins and cancels the other request.
   */
  suspend fun fetchLatestRelease(currentVersionName: String): Result<ReleaseInfo> =
    withContext(Dispatchers.IO) {
      coroutineScope {
        val urls = listOf(LATEST_RELEASE_URL, MIRROR_LATEST_RELEASE_URL)
        val channel = Channel<Result<ReleaseInfo>>(capacity = urls.size)

        val jobs =
          urls.map { url ->
            launch {
              val res = runCatching { fetchFromUrl(url, currentVersionName) }
              channel.trySend(res)
            }
          }

        var lastError: Throwable? = null
        try {
          repeat(urls.size) {
            val result = channel.receive()
            if (result.isSuccess) {
              jobs.forEach { it.cancel() }
              return@coroutineScope result
            } else {
              lastError = result.exceptionOrNull()
            }
          }
          Result.failure(lastError ?: IllegalStateException("无法连接更新服务器"))
        } catch (e: CancellationException) {
          jobs.forEach { it.cancel() }
          throw e
        } finally {
          channel.close()
        }
      }
    }

  /**
   * Streams APK update directly into `cacheDir/updates/TactKit-update.apk` using OkHttp on Dispatchers.IO.
   * Throttles UI progress updates to ~100ms intervals and removes partial files on cancel or error.
   */
  suspend fun downloadApk(
    context: Context,
    url: String,
    sourceLabel: String,
    onProgress: (ApkDownloadState.Downloading) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME).apply { mkdirs() }
      val tempFile = File(updatesDir, UPDATE_APK_PART_NAME)
      val finalFile = File(updatesDir, UPDATE_APK_NAME)
      runCatching { tempFile.delete() }

      val request =
        Request.Builder()
          .url(url)
          .header("User-Agent", "TactKit-Android")
          .get()
          .build()

      val call = downloadClient.newCall(request)
      try {
        val file =
          suspendCancellableCoroutine<File> { cont ->
            cont.invokeOnCancellation {
              runCatching { call.cancel() }
              runCatching { tempFile.delete() }
            }

            try {
              call.execute().use { response ->
                if (!response.isSuccessful) {
                  error("下载失败 (HTTP ${response.code})")
                }
                val body = response.body ?: error("响应体为空")
                val mediaType = body.contentType()
                if (mediaType != null &&
                  mediaType.type.equals("text", ignoreCase = true) &&
                  mediaType.subtype.equals("html", ignoreCase = true)
                ) {
                  error("当前线路返回网页而非 APK 安装包，请切换其他线路重试")
                }

                val totalBytes = body.contentLength()
                val buffer = ByteArray(16 * 1024)
                var downloadedBytes = 0L
                val startTimeMs = SystemClock.elapsedRealtime()
                var lastEmitTimeMs = startTimeMs - PROGRESS_EMIT_INTERVAL_MS
                var lastSpeedCalcTimeMs = startTimeMs
                var lastSpeedBytes = 0L
                var currentSpeed = 0L

                FileOutputStream(tempFile).use { out ->
                  body.byteStream().use { input ->
                    while (true) {
                      cont.context.ensureActive()
                      val read = input.read(buffer)
                      if (read == -1) break
                      out.write(buffer, 0, read)
                      downloadedBytes += read

                      val now = SystemClock.elapsedRealtime()
                      if (now - lastEmitTimeMs >= PROGRESS_EMIT_INTERVAL_MS) {
                        val dtMs = (now - lastSpeedCalcTimeMs).coerceAtLeast(1L)
                        val deltaBytes = (downloadedBytes - lastSpeedBytes).coerceAtLeast(0L)
                        val instantSpeed = (deltaBytes * 1000L) / dtMs
                        currentSpeed =
                          if (currentSpeed == 0L) instantSpeed
                          else (currentSpeed * 3L + instantSpeed * 7L) / 10L
                        lastSpeedCalcTimeMs = now
                        lastSpeedBytes = downloadedBytes
                        lastEmitTimeMs = now

                        val pct =
                          if (totalBytes > 0L) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                          } else {
                            0f
                          }
                        onProgress(
                          ApkDownloadState.Downloading(
                            sourceLabel = sourceLabel,
                            url = url,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = pct,
                            speedBytesPerSec = currentSpeed,
                          )
                        )
                      }
                    }
                    out.flush()
                  }
                }

                if (downloadedBytes <= 0L) {
                  error("下载内容为空")
                }
                if (totalBytes > 0L && downloadedBytes < totalBytes) {
                  error("文件下载不完整 ($downloadedBytes / $totalBytes)")
                }

                onProgress(
                  ApkDownloadState.Downloading(
                    sourceLabel = sourceLabel,
                    url = url,
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
                    progress = 1f,
                    speedBytesPerSec = currentSpeed,
                  )
                )

                if (finalFile.exists()) {
                  finalFile.delete()
                }
                if (!tempFile.renameTo(finalFile)) {
                  tempFile.copyTo(finalFile, overwrite = true)
                  tempFile.delete()
                }

                if (cont.isActive) {
                  cont.resume(finalFile)
                }
              }
            } catch (t: Throwable) {
              runCatching { tempFile.delete() }
              if (cont.isActive) {
                if (t is CancellationException) {
                  cont.cancel(t)
                } else {
                  cont.resumeWithException(t)
                }
              }
            }
          }
        Result.success(file)
      } catch (e: CancellationException) {
        runCatching { tempFile.delete() }
        throw e
      } catch (e: Throwable) {
        runCatching { tempFile.delete() }
        Result.failure(e)
      }
    }

  private fun fetchFromUrl(url: String, currentVersionName: String): ReleaseInfo {
    val request =
      Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "TactKit-Android")
        .get()
        .build()

    client.newCall(request).execute().use { response ->
      if (response.code == 404) {
        error("仓库暂未发布正式 Release 标签，当前已是最新开发版")
      }
      if (!response.isSuccessful) {
        error("检查更新失败 (HTTP ${response.code})")
      }
      val rawBody = response.body?.string().orEmpty()
      val obj = json.parseToJsonElement(rawBody).jsonObject
      val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
      require(tagName.isNotEmpty()) { "未解析到有效的版本号标签" }

      val title =
        obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: tagName
      val body = obj["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
      val htmlUrl =
        obj["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
          ?: RELEASES_PAGE_URL

      val apkUrl =
        obj["assets"]
          ?.jsonArray
          ?.firstNotNullOfOrNull { assetEl ->
            val assetObj = assetEl.jsonObject
            val name = assetObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val dlUrl = assetObj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (dlUrl.endsWith(".apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)) {
              dlUrl
            } else {
              null
            }
          }
          ?: htmlUrl

      return ReleaseInfo(
        tagName = tagName,
        title = title,
        body = body,
        htmlUrl = htmlUrl,
        apkDownloadUrl = apkUrl,
        isUpdateAvailable = isNewerVersion(currentVersionName, tagName),
      )
    }
  }

  companion object {
    const val REPO_URL = "https://github.com/xialia-cheng/tact-kit"
    const val ISSUES_URL = "$REPO_URL/issues"
    const val RELEASES_PAGE_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_URL =
      "https://api.github.com/repos/xialia-cheng/tact-kit/releases/latest"
    const val MIRROR_LATEST_RELEASE_URL =
      "https://gh-proxy.com/https://api.github.com/repos/xialia-cheng/tact-kit/releases/latest"

    const val UPDATES_DIR_NAME = "updates"
    const val UPDATE_APK_NAME = "TactKit-update.apk"
    const val UPDATE_APK_PART_NAME = "TactKit-update.apk.part"
    private const val PROGRESS_EMIT_INTERVAL_MS = 100L

    fun cleanupPartialDownloads(context: Context) {
      val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME)
      if (!updatesDir.exists()) return
      runCatching { File(updatesDir, UPDATE_APK_PART_NAME).delete() }
    }

    fun isNewerVersion(currentVersion: String, latestTag: String): Boolean {
      val currentParts = parseNumericParts(currentVersion)
      val latestParts = parseNumericParts(latestTag)
      if (latestParts.isEmpty()) return false

      val count = maxOf(currentParts.size, latestParts.size)
      for (i in 0 until count) {
        val c = currentParts.getOrElse(i) { 0 }
        val l = latestParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
      }
      return false
    }

    private fun parseNumericParts(raw: String): List<Int> {
      val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
      return cleaned
        .split(".")
        .mapNotNull { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() }
    }
  }
}
