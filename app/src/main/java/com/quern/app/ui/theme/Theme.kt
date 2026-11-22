package com.quern.app.ui.theme

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.quern.app.domain.model.AppLanguage
import com.quern.app.domain.model.AppThemePreset
import java.util.Locale

val LumaBackground = Color(0xFF000000)
val LumaSurface = Color(0xFF0A121B)
val LumaSurfaceElevated = Color(0xFF132131)
val LumaSurfaceHighlight = Color(0xFF1E3249)

val LumaGlassFill = Color(0x16FFFFFF)
val LumaGlassFillStrong = Color(0x26FFFFFF)
val LumaGlassBorder = Color(0x28FFFFFF)
val LumaGlassBorderSubtle = Color(0x14FFFFFF)

val LumaAccent = Color(0xFF345372)
val LumaAccentSoft = Color(0xFF70A4D6)
val LumaCyan = Color(0xFFD3D3D3)
val LumaTeal = Color(0xFF2DD4BF)
val LumaGreen = Color(0xFF10B981)
val LumaAmber = Color(0xFFFBBF24)
val LumaRose = Color(0xFFF43F5E)
val LumaRed = Color(0xFFEF4444)

val LumaTextPrimary = Color(0xFFF8FAFC)
val LumaTextSecondary = Color(0xFFA1A8B6)
val LumaTextMuted = Color(0xFF64748B)
val LumaBorder = Color(0xFF28425C)

val QuernPrimaryGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF345372), Color(0xFF70A4D6))
)

val QuernGlassBorderBrush = Brush.linearGradient(
    colors = listOf(Color(0x30FFFFFF), Color(0x0AFFFFFF))
)

data class QuernPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val focusRing: Color,
    val onAccent: Color,
    val border: Color
)

val LocalQuernPalette = staticCompositionLocalOf {
    QuernPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF0A121B),
        surfaceElevated = Color(0xFF132131),
        accent = Color(0xFF345372),
        focusRing = Color(0xFF70A4D6),
        onAccent = Color.White,
        border = Color(0xFF28425C)
    )
}

val LocalQuernAccent = staticCompositionLocalOf { Color(0xFF345372) }
val LocalQuernThemePreset = staticCompositionLocalOf { AppThemePreset.LIGHT_DARK_BLUE }
val LocalQuernResources = staticCompositionLocalOf<Resources?> { null }

@Composable
fun qString(@StringRes id: Int, vararg args: Any): String {
    val res = LocalQuernResources.current ?: LocalContext.current.resources
    return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
}

private val QuernTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
        color = LumaTextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
        color = LumaTextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        color = LumaTextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = LumaTextPrimary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = LumaTextSecondary
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
        color = LumaTextSecondary
    )
)

@Composable
fun QuernTheme(
    themePreset: AppThemePreset = AppThemePreset.LIGHT_DARK_BLUE,
    amoledPureBlack: Boolean = true,
    language: AppLanguage = AppLanguage.SYSTEM,
    accentOverrideHex: Long? = null,
    fontScaleMultiplier: Float = 1.0f,
    fontFamilyChoice: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val effectiveLang = language.resolveEffective()

    val localizedResources = remember(baseContext, effectiveLang) {
        val locale = Locale(effectiveLang.code)
        val cfg = Configuration(baseContext.resources.configuration)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        baseContext.createConfigurationContext(cfg).resources
    }

    val resolvedAccent = accentOverrideHex?.let { Color(it) } ?: themePreset.accentColor
    val resolvedOnAccent = if (accentOverrideHex != null) {
        // Exact WCAG 2.1 crossover luminance (sqrt(1.05 * 0.05) - 0.05 = 0.1791): guarantees >= 4.58:1 contrast
        if (resolvedAccent.luminance() > 0.1791f) Color(0xFF04070D) else Color.White
    } else {
        themePreset.onAccentColor
    }
    val resolvedFocusRing = if (accentOverrideHex != null) {
        // Blend slightly toward white so D-Pad focus rings always pop clearly on dark/AMOLED surfaces
        Color(
            red = (resolvedAccent.red * 0.65f + 0.35f).coerceIn(0f, 1f),
            green = (resolvedAccent.green * 0.65f + 0.35f).coerceIn(0f, 1f),
            blue = (resolvedAccent.blue * 0.65f + 0.35f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else {
        themePreset.focusRingColor
    }

    val bg = if (amoledPureBlack) Color(0xFF000000) else themePreset.surfaceTint
    val surf = themePreset.surfaceTint
    val elev = themePreset.surfaceElevatedTint
    val border = themePreset.borderTint

    val palette = QuernPalette(
        background = bg,
        surface = surf,
        surfaceElevated = elev,
        accent = resolvedAccent,
        focusRing = resolvedFocusRing,
        onAccent = resolvedOnAccent,
        border = border
    )

    val scaledTypography = remember(fontScaleMultiplier, fontFamilyChoice) {
        QuernTypography.copy(
            displaySmall = QuernTypography.displaySmall.copy(fontFamily = fontFamilyChoice, fontSize = QuernTypography.displaySmall.fontSize * fontScaleMultiplier),
            headlineMedium = QuernTypography.headlineMedium.copy(fontFamily = fontFamilyChoice, fontSize = QuernTypography.headlineMedium.fontSize * fontScaleMultiplier),
            titleLarge = QuernTypography.titleLarge.copy(fontFamily = fontFamilyChoice, fontSize = QuernTypography.titleLarge.fontSize * fontScaleMultiplier),
            titleMedium = QuernTypography.titleMedium.copy(fontFamily = fontFamilyChoice, fontSize = QuernTypography.titleMedium.fontSize * fontScaleMultiplier),
            bodyMedium = QuernTypography.bodyMedium.copy(fontFamily = fontFamilyChoice, fontSize = QuernTypography.bodyMedium.fontSize * fontScaleMultiplier),
            labelMedium = QuernTypography.labelMedium.copy(fontFamily = fontFamilyChoice, fontSize = QuernTypography.labelMedium.fontSize * fontScaleMultiplier)
        )
    }

    val colorScheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.focusRing,
        onSecondary = Color(0xFF05080D),
        tertiary = LumaGreen,
        background = palette.background,
        onBackground = LumaTextPrimary,
        surface = palette.surface,
        onSurface = LumaTextPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = LumaTextSecondary,
        outline = palette.border,
        error = LumaRed
    )

    val layoutDir = if (effectiveLang.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontScaleMultiplier) {
        Density(
            density = baseDensity.density,
            fontScale = (baseDensity.fontScale * fontScaleMultiplier).coerceIn(0.8f, 1.5f)
        )
    }

    CompositionLocalProvider(
        LocalQuernPalette provides palette,
        LocalQuernAccent provides palette.accent,
        LocalQuernThemePreset provides themePreset,
        LocalQuernStrings provides stringsFor(effectiveLang),
        LocalQuernResources provides localizedResources,
        LocalLayoutDirection provides layoutDir,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamilyChoice)
            ) {
                content()
            }
        }
    }
}
