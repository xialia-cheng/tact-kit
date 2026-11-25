package com.xialia.tactkit.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

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

class AppUpdateRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(2500, TimeUnit.MILLISECONDS)
      .callTimeout(2500, TimeUnit.MILLISECONDS)
      .build(),
) {

  private val json = Json { ignoreUnknownKeys = true }

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
