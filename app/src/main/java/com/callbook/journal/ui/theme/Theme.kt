package com.callbook.journal.ui.theme

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.R

private const val THEME_PREFS = "callbook_theme"
private const val THEME_DARK = "dark"

private data class Palette(
    val dark: Boolean,
    val canvas: Color,
    val ink: Color,
    val inkRaised: Color,
    val inkCard: Color,
    val line: Color,
    val paper: Color,
    val profit: Color,
    val loss: Color,
    val amber: Color,
    val mist: Color,
    val faint: Color,
    val wash: Color,
)

private val LightPalette = Palette(
    dark = false,
    canvas = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1A1A),
    inkRaised = Color(0xFFFFFFFF),
    inkCard = Color(0xFFF6F6F7),
    line = Color(0xFFE8E8ED),
    paper = Color(0xFF44475B),
    profit = Color(0xFF00B386),
    loss = Color(0xFFEB5B3C),
    amber = Color(0xFFE39B2B),
    mist = Color(0xFF7C7E8C),
    faint = Color(0xFFA1A3AD),
    wash = Color(0xFFF2F2F4),
)

private val DarkPalette = Palette(
    dark = true,
    canvas = Color(0xFF101012),
    ink = Color(0xFFF4F4F5),
    inkRaised = Color(0xFF161618),
    inkCard = Color(0xFF1C1C20),
    line = Color(0xFF2C2C32),
    paper = Color(0xFFE4E4E8),
    profit = Color(0xFF00B386),
    loss = Color(0xFFEB5B3C),
    amber = Color(0xFFE39B2B),
    mist = Color(0xFF9B9DA8),
    faint = Color(0xFF6F717C),
    wash = Color(0xFF1A1A1E),
)

private var palette by mutableStateOf(LightPalette)

fun loadTheme(context: Context) {
    val dark = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).getBoolean(THEME_DARK, false)
    palette = if (dark) DarkPalette else LightPalette
}

fun themeIsDark(): Boolean = palette.dark

fun setThemeDark(context: Context, dark: Boolean) {
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).edit().putBoolean(THEME_DARK, dark).commit()
    palette = if (dark) DarkPalette else LightPalette
}

fun applySystemBars(activity: ComponentActivity) {
    val style = if (palette.dark) {
        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    }
    activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    activity.window.decorView.setBackgroundColor(
        if (palette.dark) android.graphics.Color.parseColor("#101012") else android.graphics.Color.WHITE,
    )
}

val Canvas: Color get() = palette.canvas
val Ink: Color get() = palette.ink
val InkRaised: Color get() = palette.inkRaised
val InkCard: Color get() = palette.inkCard
val Line: Color get() = palette.line
val Paper: Color get() = palette.paper
val Chartreuse: Color get() = palette.ink
val Accent: Color get() = palette.ink
val Profit: Color get() = palette.profit
val Loss: Color get() = palette.loss
val Amber: Color get() = palette.amber
val Mist: Color get() = palette.mist
val Faint: Color get() = palette.faint
val Wash: Color get() = palette.wash

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_semibold, FontWeight.Bold),
)

val Fraunces = Outfit

fun pnlColor(value: Double): Color = when {
    value > 0.005 -> Profit
    value < -0.005 -> Loss
    else -> Mist
}

private fun materialColors(page: Palette) = if (page.dark) {
    darkColorScheme(
        primary = page.ink,
        onPrimary = page.canvas,
        primaryContainer = page.inkCard,
        onPrimaryContainer = page.ink,
        secondary = page.profit,
        onSecondary = page.canvas,
        background = page.canvas,
        onBackground = page.paper,
        surface = page.canvas,
        onSurface = page.paper,
        surfaceVariant = page.inkCard,
        onSurfaceVariant = page.mist,
        error = page.loss,
        onError = page.canvas,
        outline = page.line,
        outlineVariant = page.line,
    )
} else {
    lightColorScheme(
        primary = page.ink,
        onPrimary = page.canvas,
        primaryContainer = page.wash,
        onPrimaryContainer = page.ink,
        secondary = page.profit,
        onSecondary = page.canvas,
        background = page.canvas,
        onBackground = page.paper,
        surface = page.canvas,
        onSurface = page.paper,
        surfaceVariant = page.inkCard,
        onSurfaceVariant = page.mist,
        surfaceContainerLowest = page.canvas,
        surfaceContainerLow = page.inkCard,
        surfaceContainer = page.inkCard,
        surfaceContainerHigh = Color(0xFFF0F0F3),
        surfaceContainerHighest = page.line,
        error = page.loss,
        onError = page.canvas,
        outline = page.line,
        outlineVariant = page.line,
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val AppType = Typography(
    headlineLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun StockCallsTheme(content: @Composable () -> Unit) {
    val dark = themeIsDark()
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? ComponentActivity
        if (activity != null) {
            SideEffect { applySystemBars(activity) }
        }
    }
    MaterialTheme(
        colorScheme = materialColors(if (dark) DarkPalette else LightPalette),
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}
