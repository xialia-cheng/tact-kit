package com.xialia.tactkit.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReleaseInfo(
  val tagName: String,
  val title: String,
  val body: String,
  val htmlUrl: String,
  val isUpdateAvailable: Boolean,
)

class AppUpdateRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build(),
) {

  private val json = Json { ignoreUnknownKeys = true }

  suspend fun fetchLatestRelease(currentVersionName: String): Result<ReleaseInfo> =
    withContext(Dispatchers.IO) {
      runCatching {
        val request =
          Request.Builder()
            .url(LATEST_RELEASE_URL)
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
          val body =
            obj["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
          val htmlUrl =
            obj["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
              ?: RELEASES_PAGE_URL

          ReleaseInfo(
            tagName = tagName,
            title = title,
            body = body,
            htmlUrl = htmlUrl,
            isUpdateAvailable = isNewerVersion(currentVersionName, tagName),
          )
        }
      }
    }

  companion object {
    const val REPO_URL = "https://github.com/xialia-cheng/tact-kit"
    const val RELEASES_PAGE_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_URL =
      "https://api.github.com/repos/xialia-cheng/tact-kit/releases/latest"

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
