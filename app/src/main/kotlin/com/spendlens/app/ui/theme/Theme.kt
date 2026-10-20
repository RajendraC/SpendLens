package com.spendlens.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.spendlens.core.BudgetState

val Brand = Color(0xFF0F6B4F)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBDF),
    onPrimaryContainer = Color(0xFF0B4D38),
    secondaryContainer = Color(0xFFD3EBDF),
    onSecondaryContainer = Color(0xFF0B5A41),
    background = Color(0xFFF4F6F3),
    onBackground = Color(0xFF16211C),
    surface = Color(0xFFF4F6F3),
    onSurface = Color(0xFF16211C),
    surfaceVariant = Color(0xFFE7ECE8),
    onSurfaceVariant = Color(0xFF46524C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF1EE),
    outline = Color(0xFF76817B),
    outlineVariant = Color(0xFFE1E7E2),
    error = Color(0xFFA3271F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3AE),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0F5139),
    onPrimaryContainer = Color(0xFFB8F0D3),
    secondaryContainer = Color(0xFF1E3A2F),
    onSecondaryContainer = Color(0xFFB8F0D3),
    background = Color(0xFF101512),
    onBackground = Color(0xFFE1E6E2),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFE1E6E2),
    surfaceVariant = Color(0xFF26302B),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF0B0F0D),
    surfaceContainerLow = Color(0xFF181E1B),
    surfaceContainer = Color(0xFF1C2420),
    surfaceContainerHigh = Color(0xFF262E2A),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF353F3A),
    error = Color(0xFFFFB4AB),
)

/** Budget state colours. Fixed, not wallpaper-derived, so their meaning never changes. Always paired with an icon and words. */
@Immutable
data class StatusColors(
    val okFg: Color, val okBg: Color,
    val warnFg: Color, val warnBg: Color, val warnRing: Color,
    val overFg: Color, val overBg: Color, val overRing: Color,
    val track: Color,
) {
    fun fg(s: BudgetState) = when (s) { BudgetState.ON_TRACK -> okFg; BudgetState.HEADS_UP -> warnFg; BudgetState.OVER -> overFg }
    fun bg(s: BudgetState) = when (s) { BudgetState.ON_TRACK -> okBg; BudgetState.HEADS_UP -> warnBg; BudgetState.OVER -> overBg }
    fun ring(s: BudgetState, ok: Color) = when (s) { BudgetState.ON_TRACK -> ok; BudgetState.HEADS_UP -> warnRing; BudgetState.OVER -> overRing }
}

private val LightStatus = StatusColors(
    okFg = Color(0xFF0F6B4F), okBg = Color(0xFFDCEFE5),
    warnFg = Color(0xFF7A4A00), warnBg = Color(0xFFFBEBD2), warnRing = Color(0xFFC27A0E),
    overFg = Color(0xFFA3271F), overBg = Color(0xFFF9DEDA), overRing = Color(0xFFC0362C),
    track = Color(0xFFE6ECE7),
)
private val DarkStatus = StatusColors(
    okFg = Color(0xFF8FE0BC), okBg = Color(0xFF173A2C),
    warnFg = Color(0xFFFFD08A), warnBg = Color(0xFF3D2C10), warnRing = Color(0xFFE59B2E),
    overFg = Color(0xFFFFB4AB), overBg = Color(0xFF4A1E1A), overRing = Color(0xFFF2675B),
    track = Color(0xFF2A332E),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

val Numeric = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SpendLensTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}
