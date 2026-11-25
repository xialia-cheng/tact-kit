package com.xialia.tactkit.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom 10-petal scalloped cookie Shape for M3 Expressive empty states.
 */
class ScallopedPetalShape(private val petals: Int = 10) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = min(size.width, size.height) / 2f
    val innerR = maxR * 0.84f
    val steps = petals * 8

    for (i in 0..steps) {
      val theta = (2.0 * PI * i) / steps
      val wave = 0.5 + 0.5 * cos(petals * theta)
      val r = innerR + (maxR - innerR) * wave
      val x = cx + (r * cos(theta)).toFloat()
      val y = cy + (r * sin(theta)).toFloat()
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    path.transform(Matrix())
    return Outline.Generic(path)
  }
}

/**
 * M3 Expressive dual-layer file-picker waiting screen:
 * LargeTopAppBar (surfaceContainer) + centered surfaceContainerLow card with petal container icon
 * + single short prompt "选择文件以开始" + bottom-centered ExtendedFloatingActionButton.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveEmptyFileScreen(
  title: String,
  icon: ImageVector,
  onPickFile: () -> Unit,
  onNavigateBack: () -> Unit,
  fabText: String = "选择文件",
  secondaryActionText: String? = null,
  onSecondaryAction: (() -> Unit)? = null,
) {
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
              text = title,
              fontWeight = FontWeight.Bold,
            )
          },
          navigationIcon = {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.testTag("tool_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
              )
            }
          },
        )
      },
      floatingActionButton = {},
    ) { innerPadding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        Surface(
          shape = RoundedCornerShape(28.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          modifier =
            Modifier.align(Alignment.Center)
              .padding(horizontal = 24.dp)
              .widthIn(max = 360.dp)
              .fillMaxWidth()
              .testTag("expressive_empty_card"),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(vertical = 36.dp, horizontal = 24.dp),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(96.dp)
                  .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = ScallopedPetalShape(petals = 10),
                  ),
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
              )
            }

            Text(
              text = "选择文件以开始",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
            )

            if (secondaryActionText != null && onSecondaryAction != null) {
              OutlinedButton(
                onClick = onSecondaryAction,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("expressive_secondary_action_btn"),
              ) {
                Text(secondaryActionText)
              }
            }
          }
        }

        ExtendedFloatingActionButton(
          onClick = onPickFile,
          icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
          text = { Text(fabText, fontWeight = FontWeight.SemiBold) },
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .navigationBarsPadding()
              .padding(bottom = 24.dp)
              .testTag("expressive_pick_file_fab"),
        )
      }
    }
  }
}

/**
 * Predictive Back depth wrapper: smoothly scales, rounds corners (28.dp), and shifts foreground
 * during active system back swipe gestures.
 */
@Composable
fun PredictiveBackContainer(
  onBack: () -> Unit,
  content: @Composable () -> Unit,
) {
  var backProgress by remember { mutableFloatStateOf(0f) }

  PredictiveBackHandler { progressFlow ->
    try {
      progressFlow.collect { event ->
        backProgress = event.progress.coerceIn(0f, 1f)
      }
      backProgress = 0f
      onBack()
    } catch (_: CancellationException) {
      backProgress = 0f
    }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black.copy(alpha = (0.18f * (1f - backProgress)).coerceAtLeast(0f)))
        .graphicsLayer {
          val scale = 1f - 0.08f * backProgress
          scaleX = scale
          scaleY = scale
          translationX = size.width * 0.12f * backProgress
          shape = RoundedCornerShape((28f * backProgress).dp)
          clip = backProgress > 0.01f
        },
  ) {
    content()
  }
}
