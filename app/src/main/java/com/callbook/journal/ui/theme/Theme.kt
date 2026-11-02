package com.callbook.journal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.callbook.journal.R

val Canvas = Color(0xFFFFFFFF)
val Ink = Color(0xFF1A1A1A)
val InkRaised = Color(0xFFFFFFFF)
val InkCard = Color(0xFFF6F6F7)
val Line = Color(0xFFE8E8ED)
val Paper = Color(0xFF44475B)
val Chartreuse = Color(0xFF1A1A1A)
val Accent = Color(0xFF1A1A1A)
val Profit = Color(0xFF00B386)
val Loss = Color(0xFFEB5B3C)
val Amber = Color(0xFFE39B2B)
val Mist = Color(0xFF7C7E8C)
val Faint = Color(0xFFA1A3AD)
val Wash = Color(0xFFF2F2F4)

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

private val Colors = lightColorScheme(
    primary = Accent,
    onPrimary = Canvas,
    primaryContainer = Color(0xFFF2F2F4),
    onPrimaryContainer = Accent,
    secondary = Profit,
    onSecondary = Canvas,
    background = Canvas,
    onBackground = Paper,
    surface = Canvas,
    onSurface = Paper,
    surfaceVariant = InkCard,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Canvas,
    surfaceContainerLow = InkCard,
    surfaceContainer = InkCard,
    surfaceContainerHigh = Color(0xFFF0F0F3),
    surfaceContainerHighest = Color(0xFFE8E8ED),
    error = Loss,
    onError = Canvas,
    outline = Line,
    outlineVariant = Line,
)

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
    MaterialTheme(
        colorScheme = Colors,
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}
