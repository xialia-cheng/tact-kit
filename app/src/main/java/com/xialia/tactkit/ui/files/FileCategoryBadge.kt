package com.xialia.tactkit.ui.files

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xialia.tactkit.engine.FileCategory
import com.xialia.tactkit.engine.ManagedFileEntry
import com.xialia.tactkit.ui.components.TactKitImageThumbCache
import com.xialia.tactkit.ui.components.ThumbnailCache
import com.xialia.tactkit.ui.components.tactKitSharedElement
import com.xialia.tactkit.ui.theme.TactKitChip
import com.xialia.tactkit.ui.theme.TactKitMono
import com.xialia.tactkit.ui.theme.isMcPixelTheme
import com.xialia.tactkit.ui.theme.mcInventorySlotBox
import com.xialia.tactkit.ui.theme.themeShape

@Composable
fun categoryTonalColors(category: FileCategory): Pair<Color, Color> {
  val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.2f
  return when (category) {
    FileCategory.DOCUMENTS ->
      MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    FileCategory.CODE_CONFIG ->
      MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    FileCategory.IMAGES ->
      MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    FileCategory.AUDIO_VIDEO ->
      if (isDark) Color(0xFF33273D) to Color(0xFFE0C7F2)
      else Color(0xFFF0E6FA) to Color(0xFF53346E)
    FileCategory.ARCHIVES ->
      if (isDark) Color(0xFF362D20) to Color(0xFFEBD2A9)
      else Color(0xFFF7EEDB) to Color(0xFF6B4F1D)
    FileCategory.PACKAGES ->
      if (isDark) Color(0xFF21332B) to Color(0xFFB5E0CB)
      else Color(0xFFE2F3EA) to Color(0xFF1F573C)
    FileCategory.KEYS_CERTS_CALENDAR ->
      if (isDark) Color(0xFF3B2428) to Color(0xFFF2C2C9)
      else Color(0xFFFAE6E9) to Color(0xFF742A37)
    FileCategory.OTHER ->
      MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
  }
}

@Composable
fun FileEntryThumbnailOrBadge(
  entry: ManagedFileEntry,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  sharedElementKey: String? = null,
) {
  val context = LocalContext.current
  val isMc = isMcPixelTheme()
  val shape = themeShape(14.dp)

  if (entry.isDirectory) {
    val bg =
      if (entry.isNoiseDirectory) MaterialTheme.colorScheme.surfaceContainerHigh
      else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    val fg =
      if (entry.isNoiseDirectory) MaterialTheme.colorScheme.onSurfaceVariant
      else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        modifier
          .size(size)
          .clip(shape)
          .background(bg)
          .then(if (isMc) Modifier.mcInventorySlotBox() else Modifier),
    ) {
      Icon(
        imageVector = if (entry.isNoiseDirectory) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(size * 0.5f),
      )
    }
    return
  }

  val extLower = remember(entry.extension) { entry.extension.lowercase() }
  val isVideoFile =
    entry.category == FileCategory.AUDIO_VIDEO &&
      extLower in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v")
  val shouldLoadThumb =
    (entry.category == FileCategory.IMAGES && extLower !in setOf("svg", "raw", "dng")) ||
      isVideoFile

  val cacheKey =
    remember(entry.id, entry.lastModifiedMs) {
      ThumbnailCache.buildKey(
        entryId = entry.id,
        lastModifiedMs = entry.lastModifiedMs,
        targetPx = ThumbnailCache.DEFAULT_TARGET_PX,
      )
    }
  val cachedInitial =
    remember(cacheKey, shouldLoadThumb) {
      if (shouldLoadThumb) ThumbnailCache.getCached(cacheKey) else null
    }

  val thumbnail by
    produceState<Bitmap?>(initialValue = cachedInitial, key1 = cacheKey, key2 = shouldLoadThumb) {
      if (!shouldLoadThumb) {
        value = null
      } else if (value == null) {
        value =
          if (isVideoFile) {
            ThumbnailCache.getOrLoadVideoThumbnail(
              context = context,
              cacheKey = cacheKey,
              absolutePath = entry.absolutePath,
              safUriString = entry.safUriString,
              targetPx = ThumbnailCache.DEFAULT_TARGET_PX,
            )
          } else {
            ThumbnailCache.getOrLoadImageThumbnail(
              context = context,
              cacheKey = cacheKey,
              absolutePath = entry.absolutePath,
              safUriString = entry.safUriString,
              extension = extLower,
              targetPx = ThumbnailCache.DEFAULT_TARGET_PX,
            )
          }
      }
    }

  val sizedModifier =
    if (size != Dp.Unspecified) modifier.size(size).clip(shape)
    else modifier.clip(shape)

  val loadedBitmap = thumbnail
  val imageBitmap = remember(loadedBitmap) { loadedBitmap?.asImageBitmap() }
  if (loadedBitmap != null && imageBitmap != null) {
    if (sharedElementKey != null) {
      TactKitImageThumbCache.put(sharedElementKey, loadedBitmap)
    }
    Box(
      modifier =
        sizedModifier.then(
          if (sharedElementKey != null) Modifier.tactKitSharedElement(sharedElementKey)
          else Modifier
        )
    ) {
      Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
      if (isVideoFile) {
        Surface(
          shape = themeShape(999.dp),
          color = Color.Black.copy(alpha = 0.55f),
          modifier = Modifier.align(Alignment.Center).size(26.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(4.dp),
          )
        }
      }
    }
  } else {
    val (bg, fg) = categoryTonalColors(entry.category)
    val fallbackIconSize = if (size != Dp.Unspecified) size * 0.46f else 28.dp
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        sizedModifier
          .background(bg)
          .then(if (isMc) Modifier.mcInventorySlotBox() else Modifier),
    ) {
      Icon(
        imageVector = entry.category.icon,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(fallbackIconSize),
      )
    }
  }
}

@Composable
fun FileExtensionPill(
  extension: String,
  modifier: Modifier = Modifier,
) {
  if (extension.isBlank()) return
  TactKitChip(
    corner = 6.dp,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier,
  ) {
    Text(
      text = extension.uppercase(),
      fontFamily = TactKitMono,
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
    )
  }
}
