package com.xialia.tactkit.engine

import com.xialia.tactkit.data.repository.FileCacheRepository
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class LanSharedOutFile(
  val id: String = UUID.randomUUID().toString().take(8),
  val displayName: String,
  val byteSize: Long,
  val stagedFile: File,
)

data class LanReceivedFile(
  val id: String = UUID.randomUUID().toString().take(8),
  val fileName: String,
  val byteSize: Long,
  val savedFile: File,
  val receivedAt: Long = System.currentTimeMillis(),
)

data class LanReceivedText(
  val id: String = UUID.randomUUID().toString().take(8),
  val content: String,
  val receivedAt: Long = System.currentTimeMillis(),
)

/**
 * Lightweight temporary LAN HTTP file & text sharing server powered by pure Kotlin/Java ServerSocket.
 * Upgraded with 128 KB streaming buffers and bidirectional clipboard text submission (`POST /clipboard`).
 */
class LanShareServer(
  private val uploadDir: File,
  private val onFileReceived: (LanReceivedFile) -> Unit,
  private val onLog: (String) -> Unit = {},
  private val onTextReceived: (LanReceivedText) -> Unit = {},
) {
  private val running = AtomicBoolean(false)
  private var serverSocket: ServerSocket? = null
  private var acceptThread: Thread? = null
  private var clientPool: ExecutorService? = null

  val sharedOutFiles = CopyOnWriteArrayList<LanSharedOutFile>()

  val isRunning: Boolean
    get() = running.get()

  val port: Int
    get() = serverSocket?.localPort ?: -1

  fun start(preferredPort: Int = 8765): Int {
    if (running.get()) return port
    uploadDir.mkdirs()

    val socket =
      runCatching { ServerSocket(preferredPort) }
        .getOrElse { ServerSocket(0) }

    serverSocket = socket
    running.set(true)
    clientPool = Executors.newFixedThreadPool(4)

    acceptThread =
      Thread {
        while (running.get() && !socket.isClosed) {
          try {
            val client = socket.accept()
            clientPool?.execute { handleClient(client) }
          } catch (_: Exception) {
            if (!running.get()) break
          }
        }
      }.apply {
        name = "TactKit-LanShare-Accept"
        isDaemon = true
        start()
      }

    onLog("HTTP 临时服务已启动，端口 ${socket.localPort}")
    return socket.localPort
  }

  fun stop() {
    if (!running.getAndSet(false)) return
    runCatching { serverSocket?.close() }
    serverSocket = null
    runCatching { clientPool?.shutdownNow() }
    clientPool = null
    acceptThread = null
    onLog("HTTP 临时服务已停止")
  }

  private fun handleClient(client: Socket) {
    client.use { sock ->
      sock.soTimeout = 30_000
      runCatching {
        sock.sendBufferSize = 128 * 1024
        sock.receiveBufferSize = 128 * 1024
      }
      val rawInput = BufferedInputStream(sock.getInputStream(), 128 * 1024)
      val out = sock.getOutputStream()

      val headerBytes = readHeaderBlock(rawInput) ?: return
      val headerText = String(headerBytes, Charsets.ISO_8859_1)
      val lines = headerText.split("\r\n")
      if (lines.isEmpty()) return

      val requestParts = lines[0].split(" ")
      if (requestParts.size < 2) return
      val method = requestParts[0].uppercase()
      val path = requestParts[1].substringBefore("?")

      val headers = mutableMapOf<String, String>()
      for (i in 1 until lines.size) {
        val line = lines[i]
        val colon = line.indexOf(':')
        if (colon > 0) {
          headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
      }

      when {
        method == "GET" && path == "/" -> {
          sendHtmlPage(out)
        }
        method == "GET" && path.startsWith("/download/") -> {
          val id = path.removePrefix("/download/")
          val target = sharedOutFiles.firstOrNull { it.id == id }
          if (target != null && target.stagedFile.exists()) {
            sendFileDownload(out, target)
          } else {
            sendSimpleResponse(
              out,
              404,
              "Not Found",
              "text/plain; charset=utf-8",
              "File not found".toByteArray(),
            )
          }
        }
        method == "POST" && path == "/upload" -> {
          val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
          val contentType = headers["content-type"].orEmpty()
          val saved = handleMultipartUpload(rawInput, contentType, contentLength)
          if (saved != null) {
            FileCacheRepository.markDirty()
            onFileReceived(saved)
            onLog("收到文件：${saved.fileName} (${saved.byteSize} B)")
            val redirectHtml =
              """<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="1;url=/"></head><body style="font-family:sans-serif;padding:24px;"><h3>上传成功：${escapeHtml(saved.fileName)}</h3><p>正在返回...</p></body></html>"""
            sendSimpleResponse(
              out,
              200,
              "OK",
              "text/html; charset=utf-8",
              redirectHtml.toByteArray(Charsets.UTF_8),
            )
          } else {
            sendSimpleResponse(
              out,
              400,
              "Bad Request",
              "text/plain; charset=utf-8",
              "Upload failed".toByteArray(),
            )
          }
        }
        method == "POST" && path == "/clipboard" -> {
          val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
          val contentType = headers["content-type"].orEmpty()
          val text = handleTextSubmission(rawInput, contentType, contentLength)
          if (!text.isNullOrBlank()) {
            val item = LanReceivedText(content = text)
            onTextReceived(item)
            onLog("收到文字片段 (${text.length} 字)")
            val redirectHtml =
              """<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="1;url=/"></head><body style="font-family:sans-serif;padding:24px;"><h3>文字已发送至简工坊</h3><p>正在返回...</p></body></html>"""
            sendSimpleResponse(
              out,
              200,
              "OK",
              "text/html; charset=utf-8",
              redirectHtml.toByteArray(Charsets.UTF_8),
            )
          } else {
            sendSimpleResponse(
              out,
              400,
              "Bad Request",
              "text/plain; charset=utf-8",
              "Empty text".toByteArray(),
            )
          }
        }
        else -> {
          sendSimpleResponse(
            out,
            404,
            "Not Found",
            "text/plain; charset=utf-8",
            "Not Found".toByteArray(),
          )
        }
      }
    }
  }

  private fun sendHtmlPage(out: OutputStream) {
    val filesHtml =
      if (sharedOutFiles.isEmpty()) {
        "<p style='color:#666;'>当前手机端尚未添加可下载文件</p>"
      } else {
        sharedOutFiles.joinToString("") { item ->
          val safeName = escapeHtml(item.displayName)
          "<li style='margin:8px 0;'><a href='/download/${item.id}' style='color:#1a73e8;text-decoration:none;font-weight:600;'>$safeName</a> <span style='color:#777;font-size:12px;'>(${item.byteSize} B)</span></li>"
        }
      }

    val html =
      """
      <!doctype html>
      <html lang="zh-CN">
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>简工坊 · 局域网快传</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; background:#f4f5f9; color:#181a20; margin:0; padding:20px; }
          .card { max-width:560px; margin:16px auto; background:#fff; border-radius:20px; padding:20px; box-shadow:0 2px 10px rgba(0,0,0,0.04); }
          h2 { margin-top:0; font-size:20px; }
          ul { padding-left:18px; }
          button { background:#1a73e8; color:#fff; border:none; border-radius:12px; padding:10px 18px; font-size:15px; cursor:pointer; }
          input[type=file] { margin:12px 0; display:block; width:100%; }
          textarea { width:100%; box-sizing:border-box; border:1px solid #d0d5dd; border-radius:12px; padding:10px; font-size:14px; min-height:96px; margin:10px 0; font-family:inherit; }
        </style>
      </head>
      <body>
        <div class="card">
          <h2>简工坊 (TactKit) · 局域网快传</h2>
          <p style="color:#5f6368;font-size:14px;">同局域网设备免安装高速直连，关闭服务后立即断开。</p>
          <h3>从手机下载文件</h3>
          <ul>$filesHtml</ul>
        </div>
        <div class="card">
          <h3>发送文字到手机</h3>
          <form method="POST" action="/clipboard">
            <textarea name="text" placeholder="粘贴链接、命令、笔记或任意文本发送到手机..." required></textarea>
            <button type="submit">发送到手机</button>
          </form>
        </div>
        <div class="card">
          <h3>发送文件到手机</h3>
          <form method="POST" action="/upload" enctype="multipart/form-data">
            <input type="file" name="file" required />
            <button type="submit">上传到简工坊</button>
          </form>
        </div>
      </body>
      </html>
      """.trimIndent()
    sendSimpleResponse(
      out,
      200,
      "OK",
      "text/html; charset=utf-8",
      html.toByteArray(Charsets.UTF_8),
    )
  }

  private fun sendFileDownload(out: OutputStream, item: LanSharedOutFile) {
    val file = item.stagedFile
    val encodedName = URLEncoder.encode(item.displayName, "UTF-8").replace("+", "%20")
    val header =
      buildString {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: application/octet-stream\r\n")
        append("Content-Length: ${file.length()}\r\n")
        append("Content-Disposition: attachment; filename*=UTF-8''$encodedName\r\n")
        append("Connection: close\r\n\r\n")
      }
    out.write(header.toByteArray(Charsets.ISO_8859_1))
    FileInputStream(file).use { fis ->
      val buf = ByteArray(128 * 1024)
      var read = fis.read(buf)
      while (read > 0) {
        out.write(buf, 0, read)
        read = fis.read(buf)
      }
    }
    out.flush()
  }

  private fun sendSimpleResponse(
    out: OutputStream,
    code: Int,
    reason: String,
    contentType: String,
    body: ByteArray,
  ) {
    val header =
      "HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
    out.write(header.toByteArray(Charsets.ISO_8859_1))
    out.write(body)
    out.flush()
  }

  private fun readHeaderBlock(input: InputStream): ByteArray? {
    val baos = ByteArrayOutputStream(1024)
    var state = 0
    while (baos.size() < 32 * 1024) {
      val b = input.read()
      if (b == -1) return null
      baos.write(b)
      state =
        when (state) {
          0 -> if (b == '\r'.code) 1 else 0
          1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
          2 -> if (b == '\r'.code) 3 else 0
          3 -> if (b == '\n'.code) return baos.toByteArray() else if (b == '\r'.code) 1 else 0
          else -> 0
        }
    }
    return null
  }

  private fun handleTextSubmission(
    input: InputStream,
    contentType: String,
    contentLength: Long,
  ): String? {
    if (contentLength <= 0 || contentLength > 2L * 1024L * 1024L) return null
    val buf = ByteArray(contentLength.toInt())
    var offset = 0
    while (offset < buf.size) {
      val read = input.read(buf, offset, buf.size - offset)
      if (read <= 0) break
      offset += read
    }
    val rawBody = String(buf, 0, offset, Charsets.UTF_8)
    return if (contentType.contains("application/x-www-form-urlencoded", ignoreCase = true)) {
      rawBody.split("&")
        .mapNotNull { pair ->
          val idx = pair.indexOf('=')
          if (idx <= 0) null
          else {
            val k = pair.substring(0, idx)
            val v = pair.substring(idx + 1)
            if (k == "text") runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v) else null
          }
        }
        .firstOrNull()
    } else {
      rawBody.trim()
    }
  }

  private fun handleMultipartUpload(
    input: InputStream,
    contentType: String,
    contentLength: Long,
  ): LanReceivedFile? {
    val boundary =
      contentType.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("boundary=") }
        ?.removePrefix("boundary=")
        ?.trim('"')
        ?: return null

    if (contentLength <= 0 || contentLength > 512L * 1024L * 1024L) return null

    val headerBuffer = ByteArrayOutputStream(4096)
    var headerState = 0
    var bytesConsumed = 0L
    while (bytesConsumed < contentLength && headerBuffer.size() < 32 * 1024) {
      val b = input.read()
      if (b == -1) return null
      headerBuffer.write(b)
      bytesConsumed++
      headerState =
        when (headerState) {
          0 -> if (b == '\r'.code) 1 else 0
          1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
          2 -> if (b == '\r'.code) 3 else 0
          3 -> if (b == '\n'.code) break else if (b == '\r'.code) 1 else 0
          else -> 0
        }
    }
    if (headerState != 3) return null

    val partHeaderStr = headerBuffer.toString(Charsets.UTF_8.name())
    val rawFileName =
      Regex("""filename="([^"]+)"""")
        .find(partHeaderStr)
        ?.groupValues
        ?.getOrNull(1)
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?.takeIf { it.isNotBlank() }
        ?: "received_${System.currentTimeMillis()}.bin"

    val safeFileName = rawFileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val destFile = File(uploadDir, "${System.currentTimeMillis()}_$safeFileName")

    val closingMarker = "\r\n--$boundary".toByteArray(Charsets.ISO_8859_1)
    val tailReserve = closingMarker.size + 128
    val pending = ByteArrayOutputStream(128 * 1024 + tailReserve)
    var totalWritten = 0L

    FileOutputStream(destFile).use { fos ->
      val buf = ByteArray(128 * 1024)
      var remaining = contentLength - bytesConsumed
      while (remaining > 0) {
        val toRead = minOf(buf.size.toLong(), remaining).toInt()
        val read = input.read(buf, 0, toRead)
        if (read <= 0) break
        remaining -= read
        pending.write(buf, 0, read)
        val cur = pending.toByteArray()
        if (cur.size > tailReserve) {
          val flushLen = cur.size - tailReserve
          fos.write(cur, 0, flushLen)
          totalWritten += flushLen
          pending.reset()
          pending.write(cur, flushLen, cur.size - flushLen)
        }
      }
      val tail = pending.toByteArray()
      val markerIdx = indexOfBytes(tail, closingMarker, 0)
      val finalLen = if (markerIdx >= 0) markerIdx else tail.size
      if (finalLen > 0) {
        fos.write(tail, 0, finalLen)
        totalWritten += finalLen
      }
    }

    return LanReceivedFile(
      fileName = safeFileName,
      byteSize = totalWritten,
      savedFile = destFile,
    )
  }

  private fun indexOfBytes(haystack: ByteArray, needle: ByteArray, start: Int): Int {
    if (needle.isEmpty() || start + needle.size > haystack.size) return -1
    outer@ for (i in start..(haystack.size - needle.size)) {
      for (j in needle.indices) {
        if (haystack[i + j] != needle[j]) continue@outer
      }
      return i
    }
    return -1
  }

  private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  companion object {
    fun resolveLocalIpv4Addresses(): List<String> {
      val result = mutableListOf<String>()
      runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        while (interfaces.hasMoreElements()) {
          val nif = interfaces.nextElement()
          if (!nif.isUp || nif.isLoopback) continue
          val addrs = nif.inetAddresses
          while (addrs.hasMoreElements()) {
            val addr = addrs.nextElement()
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
              val ip = addr.hostAddress ?: continue
              result += ip
            }
          }
        }
      }
      return result.distinct()
    }
  }
}
