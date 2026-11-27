package com.xialia.tactkit.ui.image

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.engine.ImageEngine
import com.xialia.tactkit.ui.components.PredictiveBackContainer
import com.xialia.tactkit.ui.components.TactKitImageThumbCache
import com.xialia.tactkit.ui.components.TactKitMorphLoadingIndicator
import com.xialia.tactkit.ui.components.TactileActionButton
import com.xialia.tactkit.ui.components.tactKitSharedElement
import com.xialia.tactkit.ui.quicklook.QuickLookActivity
import com.xialia.tactkit.ui.theme.TactKitCard
import com.xialia.tactkit.ui.theme.themeShape
import com.xialia.tactkit.util.rememberAppHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewScreen(
  imageUriString: String,
  title: String,
  sharedKey: String,
  onOpenImageToolkit: (String) -> Unit,
  onOpenAiVision: (String) -> Unit,
  onNavigateBack: () -> Unit,
) {
  val context = LocalContext.current
  val haptics = rememberAppHaptics()

  val cachedSeed = remember(sharedKey, imageUriString) {
    TactKitImageThumbCache.get(sharedKey) ?: TactKitImageThumbCache.get(imageUriString)
  }
  var fullBitmap by remember(imageUriString) { mutableStateOf<Bitmap?>(cachedSeed) }
  var isLoading by remember(imageUriString) { mutableStateOf(true) }

  LaunchedEffect(imageUriString) {
    isLoading = true
    val loaded =
      runCatching {
        ImageEngine.loadOrientedBitmap(context, Uri.parse(imageUriString), maxDimension = 2048)
      }.getOrNull()
    if (loaded != null) {
      fullBitmap = loaded
    }
    isLoading = false
  }

  var zoomScale by remember { mutableFloatStateOf(1f) }
  var zoomOffset by remember { mutableStateOf(Offset.Zero) }
  val animatedZoom by
    animateFloatAsState(
      targetValue = zoomScale,
      animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
      label = "preview_zoom_scale",
    )

  PredictiveBackContainer(onBack = onNavigateBack) {
    Scaffold(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
          title = {
            Text(
              text = title.ifBlank { "图片大图预览" },
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(
              onClick = {
                haptics.tick()
                val intent =
                  Intent(context, QuickLookActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(imageUriString)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                context.startActivity(intent)
              },
            ) {
              Icon(Icons.Rounded.Preview, contentDescription = "Quick Look 体检")
            }
          },
        )
      },
      bottomBar = {
        TactKitCard(
          corner = 22.dp,
          containerColor = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
          ) {
            TactileActionButton(
              onClick = { onOpenImageToolkit(imageUriString) },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.HideImage, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("隐私打码 / 裁剪", maxLines = 1)
            }
            TactileActionButton(
              onClick = { onOpenAiVision(imageUriString) },
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("AI 识图提取", maxLines = 1)
            }
          }
        }
      },
    ) { innerPadding ->
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = {
                  haptics.tick()
                  if (zoomScale > 1.05f) {
                    zoomScale = 1f
                    zoomOffset = Offset.Zero
                  } else {
                    zoomScale = 2.2f
                  }
                },
              )
            }
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                val next = (zoomScale * zoom).coerceIn(1f, 5f)
                zoomScale = next
                zoomOffset = if (next > 1.03f) zoomOffset + pan else Offset.Zero
              }
            },
      ) {
        val bmp = fullBitmap
        if (bmp != null) {
          val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4f)
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.padding(16.dp)
                .fillMaxWidth()
                .aspectRatio(ratio, matchHeightConstraintsFirst = false)
                .graphicsLayer {
                  scaleX = animatedZoom
                  scaleY = animatedZoom
                  translationX = zoomOffset.x
                  translationY = zoomOffset.y
                }
                .tactKitSharedElement(key = sharedKey)
                .clip(themeShape(16.dp)),
          ) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
        } else {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.padding(24.dp)
                .size(220.dp)
                .tactKitSharedElement(key = sharedKey)
                .clip(themeShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              if (isLoading) {
                TactKitMorphLoadingIndicator(size = 46.dp)
                Text(
                  text = "正在解码高清图像...",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                Text(
                  text = "无法解析此图片格式",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
