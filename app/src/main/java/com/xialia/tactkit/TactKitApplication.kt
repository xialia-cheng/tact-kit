package com.xialia.tactkit

import android.app.Application
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class TactKitApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    installCrashLogger(applicationContext)
    PDFBoxResourceLoader.init(applicationContext)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_BACKGROUND) {
      com.xialia.tactkit.ui.components.ThumbnailCache.clear()
    }
  }

  companion object {
    private const val CRASH_FILE_NAME = "last_crash.txt"

    fun crashFile(context: Context): File = File(context.filesDir, CRASH_FILE_NAME)

    fun readLastCrash(context: Context): String? =
      runCatching {
        val file = crashFile(context)
        if (file.exists() && file.length() > 0L) file.readText() else null
      }.getOrNull()

    fun clearLastCrash(context: Context) {
      runCatching { crashFile(context).delete() }
    }

    private fun installCrashLogger(context: Context) {
      val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
          val sw = StringWriter()
          PrintWriter(sw).use { pw ->
            pw.println("Thread: ${thread.name}")
            throwable.printStackTrace(pw)
          }
          crashFile(context).writeText(sw.toString())
        }
        previousHandler?.uncaughtException(thread, throwable)
      }
    }
  }
}
