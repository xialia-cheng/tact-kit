package com.xialia.tactkit

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class TactKitApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(applicationContext)
  }
}
