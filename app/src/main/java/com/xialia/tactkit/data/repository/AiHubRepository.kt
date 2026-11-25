package com.xialia.tactkit.data.repository

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class ChecklistDraft(
  val title: String,
  val items: List<String>,
)

class AiHubRepository {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build()

  private val json = Json { ignoreUnknownKeys = true }
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun testConnection(baseUrl: String, model: String, apiKey: String): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(apiKey.isNotBlank()) { "请先填写 API Key" }
        require(model.isNotBlank()) { "请先填写模型名称" }
        val startMs = System.currentTimeMillis()
        val endpoint = normalizeChatCompletionsEndpoint(baseUrl)
        val bodyObj = buildJsonObject {
          put("model", model.trim())
          put("max_tokens", 16)
          put(
            "messages",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("role", "user")
                  put("content", "Reply with OK.")
                }
              )
            },
          )
        }
        val reply = executeChatRequest(endpoint, apiKey.trim(), bodyObj.toString())
        val elapsed = System.currentTimeMillis() - startMs
        "连接成功 (${elapsed} ms · ${endpoint})：${reply.take(40)}"
      }
    }

  suspend fun imageToMarkdown(
    baseUrl: String,
    model: String,
    apiKey: String,
    bitmap: Bitmap,
    instruction: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(apiKey.isNotBlank()) { "请先在设置中配置 API Key" }
        val scaled = scaleForVision(bitmap, maxSide = 1280)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val endpoint = normalizeChatCompletionsEndpoint(baseUrl)
        val prompt =
          instruction.trim().ifEmpty {
            "请精准识别图片中的文字、公式或表格，直接输出结构清晰的 Markdown（公式使用 LaTeX \$...\$ / \$\$...\$\$，表格使用 GFM 表格语法）。"
          }
        val bodyObj = buildJsonObject {
          put("model", model.trim())
          put(
            "messages",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("role", "user")
                  put(
                    "content",
                    buildJsonArray {
                      add(
                        buildJsonObject {
                          put("type", "text")
                          put("text", prompt)
                        }
                      )
                      add(
                        buildJsonObject {
                          put("type", "image_url")
                          put(
                            "image_url",
                            buildJsonObject { put("url", "data:image/jpeg;base64,$b64") },
                          )
                        }
                      )
                    },
                  )
                }
              )
            },
          )
        }
        executeChatRequest(endpoint, apiKey.trim(), bodyObj.toString())
      }
    }

  suspend fun summarizeOrAskPdf(
    baseUrl: String,
    model: String,
    apiKey: String,
    pdfText: String,
    question: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(apiKey.isNotBlank()) { "请先在设置中配置 API Key" }
        require(pdfText.isNotBlank()) { "未能从该 PDF 提取到文本（可能是纯扫描件）" }
        val endpoint = normalizeChatCompletionsEndpoint(baseUrl)
        val userPrompt =
          if (question.isBlank()) {
            "请用结构化 Markdown 总结以下 PDF 文档的核心要点、关键数据与待办行动：\n\n$pdfText"
          } else {
            "请严格基于以下 PDF 文档内容回答问题，输出清晰 Markdown。\n问题：$question\n\n文档内容：\n$pdfText"
          }
        val bodyObj = buildJsonObject {
          put("model", model.trim())
          put(
            "messages",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("role", "user")
                  put("content", userPrompt)
                }
              )
            },
          )
        }
        executeChatRequest(endpoint, apiKey.trim(), bodyObj.toString())
      }
    }

  suspend fun organizeTextToChecklist(
    baseUrl: String,
    model: String,
    apiKey: String,
    rawText: String,
  ): Result<ChecklistDraft> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(apiKey.isNotBlank()) { "请先在设置中配置 API Key" }
        require(rawText.isNotBlank()) { "请输入需要整理的文本或口述记录" }
        val endpoint = normalizeChatCompletionsEndpoint(baseUrl)
        val prompt =
          """
          请把下面杂乱的文本/语音转写内容整理成清晰的待办清单。
          严格只返回如下 JSON 格式（不要添加额外解释文字）：
          {"title": "简短清单标题", "items": ["条目1", "条目2", "条目3"]}

          原文：
          $rawText
          """.trimIndent()

        val bodyObj = buildJsonObject {
          put("model", model.trim())
          put(
            "messages",
            buildJsonArray {
              add(
                buildJsonObject {
                  put("role", "user")
                  put("content", prompt)
                }
              )
            },
          )
        }
        val reply = executeChatRequest(endpoint, apiKey.trim(), bodyObj.toString())
        parseChecklistDraft(reply)
      }
    }

  private fun executeChatRequest(endpoint: String, apiKey: String, payload: String): String {
    val request =
      Request.Builder()
        .url(endpoint)
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(payload.toRequestBody(jsonMediaType))
        .build()

    try {
      client.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          error(formatHttpError(response.code, bodyText))
        }
        val root = json.parseToJsonElement(bodyText).jsonObject
        val choices = root["choices"]?.jsonArray ?: error("响应缺少 choices 字段")
        val first = choices.firstOrNull()?.jsonObject ?: error("choices 为空")
        val message = first["message"]?.jsonObject ?: error("缺少 message 字段")
        return message["content"]?.jsonPrimitive?.contentOrNull?.trim()
          ?: error("模型返回内容为空")
      }
    } catch (e: IOException) {
      error("网络请求失败（${endpoint}）：${e.message ?: e.javaClass.simpleName}")
    }
  }

  private fun scaleForVision(bitmap: Bitmap, maxSide: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxSide && h <= maxSide) return bitmap
    val ratio = minOf(maxSide.toFloat() / w, maxSide.toFloat() / h)
    return Bitmap.createScaledBitmap(
      bitmap,
      (w * ratio).toInt().coerceAtLeast(1),
      (h * ratio).toInt().coerceAtLeast(1),
      true,
    )
  }

  companion object {
    private val VERSION_SEGMENT_REGEX =
      Regex(".*/v\\d+(?:[a-zA-Z0-9._-]*)?$", RegexOption.IGNORE_CASE)
    private val sharedLenientJson = Json { ignoreUnknownKeys = true }

    fun normalizeChatCompletionsEndpoint(baseUrl: String): String {
      val trimmed = baseUrl.trim().trimEnd('/')
      if (trimmed.isEmpty()) {
        return "${com.xialia.tactkit.data.datastore.UserPreferences.DEFAULT_AI_BASE_URL}/chat/completions"
      }
      if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
        return trimmed
      }
      if (VERSION_SEGMENT_REGEX.matches(trimmed)) {
        return "$trimmed/chat/completions"
      }
      return "$trimmed/v1/chat/completions"
    }

    fun formatHttpError(statusCode: Int, rawBody: String): String {
      val serverMessage =
        runCatching {
          val obj = sharedLenientJson.parseToJsonElement(rawBody) as? JsonObject
          val errObj = obj?.get("error") as? JsonObject
          val nestedMsg = (errObj?.get("message") as? JsonPrimitive)?.contentOrNull
          val topMsg = (obj?.get("message") as? JsonPrimitive)?.contentOrNull
          val detailMsg = (obj?.get("detail") as? JsonPrimitive)?.contentOrNull
          (nestedMsg ?: topMsg ?: detailMsg)?.trim()
        }.getOrNull()

      val detail =
        serverMessage?.takeIf { it.isNotBlank() }
          ?: rawBody.trim().take(260).ifEmpty { "服务端未返回错误详情" }
      return "HTTP $statusCode: $detail"
    }

    fun parseChecklistDraft(rawReply: String): ChecklistDraft {
      val cleaned =
        rawReply
          .trim()
          .removePrefix("```json")
          .removePrefix("```")
          .removeSuffix("```")
          .trim()

      val start = cleaned.indexOf('{')
      val end = cleaned.lastIndexOf('}')
      if (start >= 0 && end > start) {
        val jsonSlice = cleaned.substring(start, end + 1)
        runCatching {
          val obj = sharedLenientJson.parseToJsonElement(jsonSlice) as JsonObject
          val title =
            (obj["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().ifEmpty { "AI 整理清单" }
          val items =
            (obj["items"] as? JsonArray)
              ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
              ?.filter { it.isNotEmpty() }
              .orEmpty()
          if (items.isNotEmpty()) return ChecklistDraft(title = title, items = items)
        }
      }

      val fallbackItems =
        cleaned
          .lines()
          .map { it.trim().removePrefix("- ").removePrefix("* ").removePrefix("[ ] ").trim() }
          .filter { it.isNotEmpty() }
      return ChecklistDraft(
        title = "AI 整理清单",
        items = fallbackItems.ifEmpty { listOf(cleaned) },
      )
    }
  }
}
