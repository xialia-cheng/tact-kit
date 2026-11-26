package com.xialia.tactkit.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import com.xialia.tactkit.data.datastore.AppThemeStyle
import com.xialia.tactkit.data.datastore.ThemeMode

val LocalAppThemeStyle = staticCompositionLocalOf { AppThemeStyle.CLASSIC_DEFAULT }

private val DarkColorScheme =
  darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = OnIndigoPrimaryDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = OnIndigoContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = OnSlateSecondaryDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = VioletTertiaryDark,
    onTertiary = OnVioletTertiaryDark,
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = OnVioletContainerDark,
    background = BackgroundDark,
    surface = SurfaceContainerLowestDark,
    surfaceVariant = SurfaceContainerHighDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = OnIndigoPrimaryLight,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = OnIndigoContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = OnSlateSecondaryLight,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = OnSlateContainerLight,
    tertiary = VioletTertiaryLight,
    onTertiary = OnVioletTertiaryLight,
    tertiaryContainer = VioletContainerLight,
    onTertiaryContainer = OnVioletContainerLight,
    background = BackgroundLight,
    surface = SurfaceContainerLowestLight,
    surfaceVariant = SurfaceContainerLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
  )

private val GlacierLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBF6FF),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFF4F46E5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF312E81),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFEEF4FA),
    surfaceContainerLowest = Color(0xFFF7F9FC),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF4FA),
    surfaceContainerHigh = Color(0xFFE2ECF6),
    surfaceContainerHighest = Color(0xFFD6E4F2),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
  )

private val GlacierDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF818CF8),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF312E81),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = Color(0xFF0A0E17),
    surface = Color(0xFF0A0E17),
    surfaceVariant = Color(0xFF172033),
    surfaceContainerLowest = Color(0xFF0A0E17),
    surfaceContainerLow = Color(0xFF111827),
    surfaceContainer = Color(0xFF172033),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF283548),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF283548),
  )

private val SwissLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF2B59C3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF122C6B),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBE9E1),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFFD95D39),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1D6),
    onTertiaryContainer = Color(0xFF6E2410),
    background = Color(0xFFF6F5F1),
    surface = Color(0xFFF6F5F1),
    surfaceVariant = Color(0xFFECEAE2),
    surfaceContainerLowest = Color(0xFFF6F5F1),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFECEAE2),
    surfaceContainerHigh = Color(0xFFE2DFD5),
    surfaceContainerHighest = Color(0xFFD7D3C7),
    onBackground = Color(0xFF18191C),
    onSurface = Color(0xFF18191C),
    onSurfaceVariant = Color(0xFF5A5D65),
    outline = Color(0xFF9EA0A8),
    outlineVariant = Color(0xFFD5D3CA),
  )

private val SwissDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF6B93F5),
    onPrimary = Color(0xFF0C1D4A),
    primaryContainer = Color(0xFF1D3B82),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF262930),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFF47B56),
    onTertiary = Color(0xFF4A1608),
    tertiaryContainer = Color(0xFF7C2E17),
    onTertiaryContainer = Color(0xFFFFE1D6),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF22252C),
    surfaceContainerLowest = Color(0xFF121316),
    surfaceContainerLow = Color(0xFF1A1C21),
    surfaceContainer = Color(0xFF22252C),
    surfaceContainerHigh = Color(0xFF2B2F38),
    surfaceContainerHighest = Color(0xFF353A45),
    onBackground = Color(0xFFF1F0EC),
    onSurface = Color(0xFFF1F0EC),
    onSurfaceVariant = Color(0xFFA0A3AB),
    outline = Color(0xFF4A4E58),
    outlineVariant = Color(0xFF30343E),
  )

private val PrecisionLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF1B6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF2EA),
    onPrimaryContainer = Color(0xFF0A3D34),
    secondary = Color(0xFF2D8A7B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EFEA),
    onSecondaryContainer = Color(0xFF12463D),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF134E4A),
    background = Color(0xFFF3F6F4),
    surface = Color(0xFFF3F6F4),
    surfaceVariant = Color(0xFFE7EEEA),
    surfaceContainerLowest = Color(0xFFF3F6F4),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFE7EEEA),
    surfaceContainerHigh = Color(0xFFDCE6E0),
    surfaceContainerHighest = Color(0xFFD0DDD6),
    onBackground = Color(0xFF111C18),
    onSurface = Color(0xFF111C18),
    onSurfaceVariant = Color(0xFF475B54),
    outline = Color(0xFF8CA39A),
    outlineVariant = Color(0xFFC4D4CC),
  )

private val PrecisionDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF062E28),
    primaryContainer = Color(0xFF114D43),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF07332C),
    secondaryContainer = Color(0xFF1B2B26),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF063322),
    tertiaryContainer = Color(0xFF124734),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF0E1412),
    surface = Color(0xFF0E1412),
    surfaceVariant = Color(0xFF1C2824),
    surfaceContainerLowest = Color(0xFF0E1412),
    surfaceContainerLow = Color(0xFF151E1B),
    surfaceContainer = Color(0xFF1C2824),
    surfaceContainerHigh = Color(0xFF24332E),
    surfaceContainerHighest = Color(0xFF2D3F39),
    onBackground = Color(0xFFE9F2EE),
    onSurface = Color(0xFFE9F2EE),
    onSurfaceVariant = Color(0xFF95ACA3),
    outline = Color(0xFF465C54),
    outlineVariant = Color(0xFF293A34),
  )

private val MinecraftLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF5B8731),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5B8731),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF866043),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4C4B4),
    onSecondaryContainer = Color(0xFF3A281A),
    tertiary = Color(0xFF2CCDB1),
    onTertiary = Color(0xFF082E27),
    tertiaryContainer = Color(0xFFBDF5EA),
    onTertiaryContainer = Color(0xFF0A3D34),
    background = Color(0xFFC6C6C6),
    surface = Color(0xFFC6C6C6),
    surfaceVariant = Color(0xFFD4D4D4),
    surfaceContainerLowest = Color(0xFFC6C6C6),
    surfaceContainerLow = Color(0xFFE0E0E0),
    surfaceContainer = Color(0xFFD4D4D4),
    surfaceContainerHigh = Color(0xFFBABABA),
    surfaceContainerHighest = Color(0xFFA6A6A6),
    onBackground = Color(0xFF1E1E1E),
    onSurface = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF7A7A7A),
  )

private val MinecraftDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF2CCDB1),
    onPrimary = Color(0xFF072B24),
    primaryContainer = Color(0xFF3B2D54),
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = Color(0xFFA855F7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF383842),
    onSecondaryContainer = Color(0xFFE2E2EA),
    tertiary = Color(0xFF5B8731),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2F4719),
    onTertiaryContainer = Color(0xFFDDF5C4),
    background = Color(0xFF1D1D21),
    surface = Color(0xFF1D1D21),
    surfaceVariant = Color(0xFF36363D),
    surfaceContainerLowest = Color(0xFF1D1D21),
    surfaceContainerLow = Color(0xFF2B2B30),
    surfaceContainer = Color(0xFF36363D),
    surfaceContainerHigh = Color(0xFF42424A),
    surfaceContainerHighest = Color(0xFF50505A),
    onBackground = Color(0xFFF0F0F4),
    onSurface = Color(0xFFF0F0F4),
    onSurfaceVariant = Color(0xFFB5B5C0),
    outline = Color(0xFF646470),
    outlineVariant = Color(0xFF45454F),
  )

val TactKitShapes =
  Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

val PrecisionLabShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
  )

val MinecraftPixelShapes =
  Shapes(
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
  )

private val McPixelShadow =
  Shadow(
    color = Color(0xFF3F3F3F).copy(alpha = 0.55f),
    offset = Offset(2f, 2f),
    blurRadius = 0f,
  )

val MinecraftTypography =
  Typography(
    headlineSmall =
      TactKitTypography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
        shadow = McPixelShadow,
      ),
    titleLarge =
      TactKitTypography.titleLarge.copy(
        fontFamily = FontFamily.Monospace,
        shadow = McPixelShadow,
      ),
    titleMedium =
      TactKitTypography.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
        shadow = McPixelShadow,
      ),
    titleSmall =
      TactKitTypography.titleSmall.copy(
        fontFamily = FontFamily.Monospace,
      ),
    bodyLarge = TactKitTypography.bodyLarge,
    bodyMedium = TactKitTypography.bodyMedium,
    bodySmall = TactKitTypography.bodySmall,
    labelMedium = TactKitTypography.labelMedium.copy(fontFamily = FontFamily.Monospace),
    labelSmall = TactKitTypography.labelSmall.copy(fontFamily = FontFamily.Monospace),
  )

fun themeCardBorder(
  style: AppThemeStyle,
  isDark: Boolean,
  outlineVariant: Color,
): BorderStroke? =
  when (style) {
    AppThemeStyle.CLASSIC_DEFAULT -> null
    AppThemeStyle.GLACIER_BENTO ->
      BorderStroke(
        width = 1.dp,
        brush =
          Brush.verticalGradient(
            colors =
              listOf(
                Color.White.copy(alpha = if (isDark) 0.28f else 0.90f),
                Color.White.copy(alpha = if (isDark) 0.05f else 0.18f),
              ),
          ),
      )
    AppThemeStyle.SWISS_CRAFT -> BorderStroke(1.dp, outlineVariant)
    AppThemeStyle.PRECISION_LAB -> BorderStroke(1.dp, outlineVariant.copy(alpha = 0.85f))
    AppThemeStyle.MINECRAFT_PIXEL -> null
  }

@Composable
fun isTactKitDarkTheme(themeMode: ThemeMode): Boolean {
  val systemDark = isSystemInDarkTheme()
  return when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
}

@Composable
fun TactKitTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = true,
  appThemeStyle: AppThemeStyle = AppThemeStyle.CLASSIC_DEFAULT,
  content: @Composable () -> Unit,
) {
  val isDarkTheme = isTactKitDarkTheme(themeMode)

  val colorScheme =
    when (appThemeStyle) {
      AppThemeStyle.CLASSIC_DEFAULT -> {
        when {
          dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic =
              if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (isDarkTheme) {
              dynamic.copy(
                background = BackgroundDark,
                surface = SurfaceContainerLowestDark,
                surfaceVariant = SurfaceContainerHighDark,
                surfaceContainerLowest = SurfaceContainerLowestDark,
                surfaceContainerLow = SurfaceContainerLowDark,
                surfaceContainer = SurfaceContainerDark,
                surfaceContainerHigh = SurfaceContainerHighDark,
                surfaceContainerHighest = SurfaceContainerHighestDark,
                outlineVariant = OutlineVariantDark,
              )
            } else {
              dynamic.copy(
                background = BackgroundLight,
                surface = SurfaceContainerLowestLight,
                surfaceVariant = SurfaceContainerLight,
                surfaceContainerLowest = SurfaceContainerLowestLight,
                surfaceContainerLow = SurfaceContainerLowLight,
                surfaceContainer = SurfaceContainerLight,
                surfaceContainerHigh = SurfaceContainerHighLight,
                surfaceContainerHighest = SurfaceContainerHighestLight,
                outlineVariant = OutlineVariantLight,
              )
            }
          }
          isDarkTheme -> DarkColorScheme
          else -> LightColorScheme
        }
      }
      AppThemeStyle.GLACIER_BENTO -> if (isDarkTheme) GlacierDarkColorScheme else GlacierLightColorScheme
      AppThemeStyle.SWISS_CRAFT -> if (isDarkTheme) SwissDarkColorScheme else SwissLightColorScheme
      AppThemeStyle.PRECISION_LAB -> if (isDarkTheme) PrecisionDarkColorScheme else PrecisionLightColorScheme
      AppThemeStyle.MINECRAFT_PIXEL -> if (isDarkTheme) MinecraftDarkColorScheme else MinecraftLightColorScheme
    }

  val shapes =
    when (appThemeStyle) {
      AppThemeStyle.PRECISION_LAB -> PrecisionLabShapes
      AppThemeStyle.MINECRAFT_PIXEL -> MinecraftPixelShapes
      else -> TactKitShapes
    }

  val typography =
    if (appThemeStyle == AppThemeStyle.MINECRAFT_PIXEL) MinecraftTypography else TactKitTypography

  CompositionLocalProvider(LocalAppThemeStyle provides appThemeStyle) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = shapes,
      content = content,
    )
  }
}
