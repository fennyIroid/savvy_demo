package com.iroid.savvy.rd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.iroid.savvy.rd.R

/** "Frozen lake" palette: slate gray, icy blue, snow white, navy. Every other tone is a tint of these. */
object FrozenLake {
    val Slate = Color(0xFF6D8196)
    val Icy = Color(0xFFADD8E6)
    val Snow = Color(0xFFFFFAFA)
    val Navy = Color(0xFF000080)
}

@Immutable
data class SavvyColors(
    /** Behind the curved sheet and under the text nav bar. */
    val canvas: Color,
    /** The main curved sheet. */
    val sheet: Color,
    /** Grouped cards on the sheet. */
    val card: Color,
    /** Inputs, day circles, pressed cards. */
    val cardStrong: Color,
    val line: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    /** Solid primary button. */
    val primary: Color,
    val onPrimary: Color,
    val icy: Color,
    val icySoft: Color,
    val onIcy: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val shadow: Color,
    val isDark: Boolean,
)

val LightSavvy = SavvyColors(
    canvas = Color(0xFFD6DFE7),
    sheet = FrozenLake.Snow,
    card = Color(0xFFEEF2F6),
    cardStrong = Color(0xFFE1E8EE),
    line = Color(0xFFD6DEE5),
    ink = Color(0xFF0B1233),
    inkSoft = FrozenLake.Slate,
    inkFaint = Color(0xFFA3B1BF),
    primary = FrozenLake.Navy,
    onPrimary = FrozenLake.Snow,
    icy = FrozenLake.Icy,
    icySoft = Color(0xFFDDEFF5),
    onIcy = FrozenLake.Navy,
    danger = Color(0xFFB0414A),
    dangerSoft = Color(0xFFF6E3E4),
    success = Color(0xFF2F7D6B),
    shadow = Color(0xFF3B4A5C),
    isDark = false,
)

val DarkSavvy = SavvyColors(
    canvas = Color(0xFF02041A),
    sheet = Color(0xFF10173F),
    card = Color(0xFF1A2250),
    cardStrong = Color(0xFF242D62),
    line = Color(0xFF252E5C),
    ink = FrozenLake.Snow,
    inkSoft = Color(0xFF9EB0C2),
    inkFaint = Color(0xFF5B6A86),
    primary = FrozenLake.Icy,
    onPrimary = FrozenLake.Navy,
    icy = FrozenLake.Icy,
    icySoft = Color(0xFF1C3552),
    onIcy = FrozenLake.Navy,
    danger = Color(0xFFE38A90),
    dangerSoft = Color(0xFF3A1C2A),
    success = Color(0xFF7CC9B5),
    shadow = Color(0xFF000000),
    isDark = true,
)

val LocalSavvyColors = staticCompositionLocalOf { LightSavvy }

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: Int) = Font(R.font.outfit, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Outfit = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700))

object SavvyType {
    val display = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp)
    val headline = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val nav = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 17.sp)
}

enum class Appearance { AUTO, LIGHT, DARK }

@Composable
fun SavvyTheme(appearance: Appearance = Appearance.AUTO, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.AUTO -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val c = if (dark) DarkSavvy else LightSavvy
    // Material components (dialogs, text fields, snackbars) pick up the same palette.
    val scheme = if (dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.sheet, surface = c.sheet, onSurface = c.ink,
        onBackground = c.ink, surfaceVariant = c.card, onSurfaceVariant = c.inkSoft, outline = c.line, error = c.danger,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.cardStrong, inverseSurface = c.ink, inverseOnSurface = c.sheet,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, background = c.sheet, surface = c.sheet, onSurface = c.ink,
        onBackground = c.ink, surfaceVariant = c.card, onSurfaceVariant = c.inkSoft, outline = c.line, error = c.danger,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.cardStrong, inverseSurface = c.ink, inverseOnSurface = c.sheet,
    )
    // Status / nav bar icons follow the app's own light or dark choice, not only the system's.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalSavvyColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.let { t ->
            t.copy(
                bodyLarge = t.bodyLarge.copy(fontFamily = Outfit), bodyMedium = t.bodyMedium.copy(fontFamily = Outfit),
                bodySmall = t.bodySmall.copy(fontFamily = Outfit), titleLarge = t.titleLarge.copy(fontFamily = Outfit),
                titleMedium = t.titleMedium.copy(fontFamily = Outfit), labelLarge = t.labelLarge.copy(fontFamily = Outfit),
                headlineSmall = t.headlineSmall.copy(fontFamily = Outfit),
            )
        }, content = content)
    }
}

val savvyColors: SavvyColors @Composable get() = LocalSavvyColors.current
