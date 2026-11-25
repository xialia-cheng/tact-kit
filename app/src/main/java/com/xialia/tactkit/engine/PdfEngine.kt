package com.xialia.tactkit.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AnnotationTool {
  HIGHLIGHTER,
  PEN,
  SIGNATURE,
}

data class PdfStroke(
  val tool: AnnotationTool,
  val colorArgb: Int,
  val normalizedWidth: Float,
  val normalizedPoints: List<Pair<Float, Float>>,
)

sealed interface PdfPrepareResult {
  data class Ready(
    val readableUri: Uri,
    val pageCount: Int,
    val tempFile: File?,
  ) : PdfPrepareResult

  data object PasswordRequired : PdfPrepareResult

  data object WrongPassword : PdfPrepareResult

  data class Error(val message: String) : PdfPrepareResult
}

object PdfEngine {

  private fun ensureInit(context: Context) {
    PDFBoxResourceLoader.init(context.applicationContext)
  }

  private fun openPdfFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
    return if (uri.scheme == "file" && !uri.path.isNullOrEmpty()) {
      val f = File(uri.path!!)
      if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
    } else {
      context.contentResolver.openFileDescriptor(uri, "r")
    }
  }

  private fun openPdfInputStream(context: Context, uri: Uri): InputStream? {
    return if (uri.scheme == "file" && !uri.path.isNullOrEmpty()) {
      val f = File(uri.path!!)
      if (f.exists()) FileInputStream(f) else null
    } else {
      context.contentResolver.openInputStream(uri)
    }
  }

  /**
   * Opens a PDF safely. If protected with a password, returns PasswordRequired / WrongPassword
   * without touching the original file. When password is valid, decrypts to an ephemeral cacheDir
   * file whose URI can be rendered by Android's PdfRenderer.
   */
  suspend fun prepareReadablePdf(
    context: Context,
    uri: Uri,
    password: String? = null,
  ): PdfPrepareResult =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      if (password == null) {
        try {
          val pfd = openPdfFileDescriptor(context, uri)
            ?: return@withContext PdfPrepareResult.Error("无法打开 PDF 文件")
          pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
              return@withContext PdfPrepareResult.Ready(
                readableUri = uri,
                pageCount = renderer.pageCount,
                tempFile = null,
              )
            }
          }
        } catch (_: SecurityException) {
          return@withContext PdfPrepareResult.PasswordRequired
        } catch (e: Throwable) {
          var fallbackPages = -1
          val encrypted = runCatching {
            openPdfInputStream(context, uri)?.use { input ->
              PDDocument.load(input).use { doc ->
                fallbackPages = doc.numberOfPages
              }
            }
            false
          }.getOrElse { err -> err is InvalidPasswordException }
          return@withContext when {
            encrypted -> PdfPrepareResult.PasswordRequired
            fallbackPages >= 0 ->
              PdfPrepareResult.Ready(readableUri = uri, pageCount = fallbackPages, tempFile = null)
            else -> PdfPrepareResult.Error(e.message ?: "无法读取 PDF")
          }
        }
      } else {
        var tempFile: File? = null
        try {
          val stream = openPdfInputStream(context, uri)
            ?: return@withContext PdfPrepareResult.Error("无法读取加密 PDF")
          var pageCount = 0
          stream.use { input ->
            PDDocument.load(input, password).use { doc ->
              doc.isAllSecurityToBeRemoved = true
              pageCount = doc.numberOfPages
              val outTmp = File.createTempFile("unlocked_preview_", ".pdf", context.cacheDir)
              tempFile = outTmp
              outTmp.outputStream().use { out -> doc.save(out) }
            }
          }
          val file = tempFile ?: return@withContext PdfPrepareResult.Error("创建临时文件失败")
          val unlockedUri = Uri.fromFile(file)
          PdfPrepareResult.Ready(
            readableUri = unlockedUri,
            pageCount = pageCount,
            tempFile = file,
          )
        } catch (_: InvalidPasswordException) {
          tempFile?.delete()
          PdfPrepareResult.WrongPassword
        } catch (e: Exception) {
          tempFile?.delete()
          PdfPrepareResult.Error(e.message ?: "解密 PDF 失败")
        }
      }
    }

  suspend fun getPageCount(context: Context, uri: Uri): Int =
    withContext(Dispatchers.IO) {
      runCatching {
        openPdfFileDescriptor(context, uri)?.use { pfd ->
          PdfRenderer(pfd).use { renderer -> renderer.pageCount }
        } ?: 0
      }.getOrElse {
        ensureInit(context)
        runCatching {
          openPdfInputStream(context, uri)?.use { input ->
            PDDocument.load(input).use { it.numberOfPages }
          } ?: 0
        }.getOrDefault(0)
      }
    }

  suspend fun renderPageBitmap(
    context: Context,
    uri: Uri,
    pageIndex: Int,
    targetWidthPx: Int = 1280,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      openPdfFileDescriptor(context, uri)?.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          if (pageIndex !in 0 until renderer.pageCount) return@withContext null
          renderer.openPage(pageIndex).use { page ->
            val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
            val width = targetWidthPx.coerceIn(320, 2048)
            val height = (width * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
          }
        }
      }
    }

  suspend fun mergePdfs(context: Context, inputUris: List<Uri>, outputUri: Uri) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      require(inputUris.size >= 2) { "请至少选择两个 PDF 文件进行合并" }
      val tempFiles = mutableListOf<File>()
      try {
        val merger = PDFMergerUtility()
        inputUris.forEachIndexed { idx, uri ->
          val tmp = File.createTempFile("merge_in_$idx", ".pdf", context.cacheDir)
          tempFiles += tmp
          openPdfInputStream(context, uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
          }
          merger.addSource(tmp)
        }
        val outTmp = File.createTempFile("merge_out_", ".pdf", context.cacheDir)
        tempFiles += outTmp
        merger.destinationFileName = outTmp.absolutePath
        merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())

        context.contentResolver.openOutputStream(outputUri)?.use { out ->
          outTmp.inputStream().use { it.copyTo(out) }
        } ?: error("无法写入输出文件")
      } finally {
        tempFiles.forEach { it.delete() }
      }
    }

  suspend fun splitPdfByRange(
    context: Context,
    inputUri: Uri,
    startPage1Based: Int,
    endPage1Based: Int,
    outputUri: Uri,
  ) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      openPdfInputStream(context, inputUri)?.use { input ->
        PDDocument.load(input).use { sourceDoc ->
          val total = sourceDoc.numberOfPages
          val from = startPage1Based.coerceIn(1, total)
          val to = endPage1Based.coerceIn(from, total)
          PDDocument().use { outDoc ->
            for (pageNum in from..to) {
              outDoc.addPage(sourceDoc.getPage(pageNum - 1))
            }
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
              outDoc.save(out)
            } ?: error("无法写入拆分后的 PDF")
          }
        }
      } ?: error("无法读取源 PDF")
    }

  suspend fun compressPdf(
    context: Context,
    inputUri: Uri,
    jpegQuality: Float = 0.65f,
    outputUri: Uri,
  ) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      val pfd: ParcelFileDescriptor =
        openPdfFileDescriptor(context, inputUri) ?: error("无法打开 PDF")
      pfd.use { fd ->
        PdfRenderer(fd).use { renderer ->
          PDDocument().use { outDoc ->
            for (i in 0 until renderer.pageCount) {
              renderer.openPage(i).use { page ->
                val scale = 1.4f
                val w = (page.width * scale).toInt().coerceIn(480, 1800)
                val h = (page.height * scale).toInt().coerceIn(480, 2600)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                outDoc.addPage(pdPage)
                val pdImage = JPEGFactory.createFromImage(outDoc, bmp, jpegQuality.coerceIn(0.25f, 0.95f))
                PDPageContentStream(outDoc, pdPage).use { cs ->
                  cs.drawImage(pdImage, 0f, 0f, pdPage.mediaBox.width, pdPage.mediaBox.height)
                }
                bmp.recycle()
              }
            }
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
              outDoc.save(out)
            } ?: error("无法写入压缩后的 PDF")
          }
        }
      }
    }

  suspend fun protectPdf(
    context: Context,
    inputUri: Uri,
    userPassword: String,
    ownerPassword: String,
    outputUri: Uri,
  ) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      require(userPassword.isNotBlank()) { "密码不能为空" }
      openPdfInputStream(context, inputUri)?.use { input ->
        PDDocument.load(input).use { doc ->
          val ap = AccessPermission()
          val policy = StandardProtectionPolicy(ownerPassword.ifBlank { userPassword }, userPassword, ap)
          policy.encryptionKeyLength = 128
          doc.protect(policy)
          context.contentResolver.openOutputStream(outputUri)?.use { out ->
            doc.save(out)
          } ?: error("无法写入加密 PDF")
        }
      } ?: error("无法读取 PDF")
    }

  suspend fun unlockPdf(
    context: Context,
    inputUri: Uri,
    password: String,
    outputUri: Uri,
  ) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      openPdfInputStream(context, inputUri)?.use { input ->
        PDDocument.load(input, password).use { doc ->
          doc.isAllSecurityToBeRemoved = true
          context.contentResolver.openOutputStream(outputUri)?.use { out ->
            doc.save(out)
          } ?: error("无法写入解密 PDF")
        }
      } ?: error("无法读取加密 PDF")
    }

  suspend fun extractText(context: Context, inputUri: Uri, maxChars: Int = 16_000): String =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      openPdfInputStream(context, inputUri)?.use { input ->
        PDDocument.load(input).use { doc ->
          val stripper = PDFTextStripper()
          val raw = stripper.getText(doc).orEmpty().trim()
          if (raw.length > maxChars) raw.take(maxChars) else raw
        }
      } ?: ""
    }

  suspend fun exportAnnotatedPdf(
    context: Context,
    inputUri: Uri,
    strokesByPage: Map<Int, List<PdfStroke>>,
    outputUri: Uri,
  ) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      openPdfInputStream(context, inputUri)?.use { input ->
        PDDocument.load(input).use { doc ->
          strokesByPage.forEach { (pageIndex, strokes) ->
            if (pageIndex in 0 until doc.numberOfPages && strokes.isNotEmpty()) {
              val page = doc.getPage(pageIndex)
              val box = page.cropBox ?: page.mediaBox
              val pageW = box.width
              val pageH = box.height
              val originX = box.lowerLeftX
              val originY = box.lowerLeftY

              PDPageContentStream(
                  doc,
                  page,
                  PDPageContentStream.AppendMode.APPEND,
                  true,
                  true,
                )
                .use { cs ->
                  strokes.forEach { stroke ->
                    if (stroke.normalizedPoints.size >= 2) {
                      val gs = PDExtendedGraphicsState()
                      val alpha =
                        if (stroke.tool == AnnotationTool.HIGHLIGHTER) 0.35f else 1.0f
                      gs.strokingAlphaConstant = alpha
                      cs.setGraphicsStateParameters(gs)

                      val r = Color.red(stroke.colorArgb)
                      val g = Color.green(stroke.colorArgb)
                      val b = Color.blue(stroke.colorArgb)
                      cs.setStrokingColor(r, g, b)
                      cs.setLineWidth((stroke.normalizedWidth * pageW).coerceIn(1f, 36f))
                      cs.setLineCapStyle(1)
                      cs.setLineJoinStyle(1)

                      val first = stroke.normalizedPoints.first()
                      cs.moveTo(
                        originX + first.first * pageW,
                        originY + (1f - first.second) * pageH,
                      )
                      stroke.normalizedPoints.drop(1).forEach { pt ->
                        cs.lineTo(
                          originX + pt.first * pageW,
                          originY + (1f - pt.second) * pageH,
                        )
                      }
                      cs.stroke()
                    }
                  }
                }
            }
          }
          context.contentResolver.openOutputStream(outputUri)?.use { out ->
            doc.save(out)
          } ?: error("无法保存批注后的 PDF")
        }
      } ?: error("无法读取原始 PDF")
    }

  suspend fun imagesToPdf(context: Context, imageUris: List<Uri>, outputUri: Uri) =
    withContext(Dispatchers.IO) {
      ensureInit(context)
      require(imageUris.isNotEmpty()) { "请先选择图片" }
      PDDocument().use { doc ->
        imageUris.forEach { uri ->
          val bitmap = ImageEngine.loadOrientedBitmap(context, uri, maxDimension = 1800)
            ?: return@forEach
          val pageWidth = 595f
          val pageHeight = pageWidth * (bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1))
          val page = PDPage(PDRectangle(pageWidth, pageHeight))
          doc.addPage(page)
          val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.88f)
          PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)
          }
          bitmap.recycle()
        }
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
          doc.save(out)
        } ?: error("无法输出 PDF")
      }
    }

  suspend fun saveBitmapAsPng(context: Context, bitmap: Bitmap, outputUri: Uri) =
    withContext(Dispatchers.IO) {
      context.contentResolver.openOutputStream(outputUri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      } ?: error("无法保存图片")
    }
}
