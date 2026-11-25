# TactKit (简工坊) ProGuard / R8 Rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xialia.tactkit.**$$serializer { *; }
-keepclassmembers class com.xialia.tactkit.** {
    *** Companion;
}
-keepclasseswithmembers class com.xialia.tactkit.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# CommonMark
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# OkHttp & Okio
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.jsse.**

# Phase 3: Apache POI & XmlBeans
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.codec.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.xmlgraphics.**
-dontwarn org.apache.batik.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn org.w3c.dom.**
-dontwarn javax.xml.**
-dontwarn javax.swing.**
-dontwarn javax.activation.**
-dontwarn java.lang.invoke.**
-dontwarn com.zaxxer.sparsebits.**

# Phase 3: Commons Compress, XZ & zip4j
-keep class org.apache.commons.compress.** { *; }
-keep class net.lingala.zip4j.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn net.lingala.zip4j.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**

# Phase 3: SnakeYAML
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
-dontwarn java.beans.**

# Phase 4: ZXing, CameraX & Graphics Shapes
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.graphics.shapes.** { *; }
-dontwarn androidx.graphics.shapes.**
-dontwarn java.nio.file.**
-dontwarn javax.naming.**

