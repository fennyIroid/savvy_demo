package com.iroid.savvy.rd.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.iroid.savvy.rd.ui.theme.FrozenLake
import com.iroid.savvy.rd.ui.theme.Outfit
import com.iroid.savvy.rd.ui.theme.savvyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class CardLook { IDLE, ACTIVE, LISTENING, SUCCESS, ERROR }

/**
 * The Savvy card as the hero object (Brick shows its puck here): a soft, embossed card
 * with scan-frame corners. ACTIVE glows icy, LISTENING pulses NFC rings.
 */
@Composable
fun SavvyCardHero(look: CardLook, modifier: Modifier = Modifier, width: Dp = 250.dp) {
    val c = savvyColors
    val height = width * 0.63f
    val shape = RoundedCornerShape(width * 0.12f)
    val t = rememberInfiniteTransition(label = "card")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ring")

    val (top, bottom, ink) = when (look) {
        CardLook.IDLE -> if (c.isDark) Triple(Color(0xFF232C58), Color(0xFF151C40), FrozenLake.Slate)
            else Triple(Color(0xFFFFFFFF), Color(0xFFE3EAF0), Color(0xFFB7C3CF))
        CardLook.ACTIVE, CardLook.LISTENING -> Triple(Color(0xFFD4ECF4), FrozenLake.Icy, Color(0xFF7FB3C6))
        CardLook.SUCCESS -> Triple(Color(0xFFD9F0EA), Color(0xFFA9DCCD), Color(0xFF6FB5A1))
        CardLook.ERROR -> Triple(Color(0xFFF7E6E7), Color(0xFFEBC6C9), Color(0xFFC98E93))
    }
    val glow = when (look) {
        CardLook.ACTIVE, CardLook.LISTENING -> FrozenLake.Icy
        CardLook.SUCCESS -> Color(0xFF8FD3BE)
        CardLook.ERROR -> Color(0xFFE3A1A6)
        CardLook.IDLE -> Color.Transparent
    }

    Box(modifier.size(width * 1.45f, height * 1.9f), contentAlignment = Alignment.Center) {
        // Halo / NFC rings behind the card.
        Canvas(Modifier.fillMaxSize()) {
            if (glow != Color.Transparent) {
                val r = size.minDimension * (0.42f + 0.04f * breathe)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0f)), center, r * 1.35f), r * 1.35f)
            }
            if (look == CardLook.LISTENING) {
                for (i in 0..2) {
                    val p = (ring + i / 3f) % 1f
                    drawRoundRect(
                        color = FrozenLake.Icy.copy(alpha = (1f - p) * 0.8f),
                        topLeft = Offset(center.x - (width.toPx() / 2) * (1f + p * 0.35f), center.y - (height.toPx() / 2) * (1f + p * 0.55f)),
                        size = Size(width.toPx() * (1f + p * 0.35f), height.toPx() * (1f + p * 0.55f)),
                        cornerRadius = CornerRadius(width.toPx() * 0.14f),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
        Box(
            Modifier.size(width, height)
                .shadow(if (c.isDark) 10.dp else 22.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
                .clip(shape)
                .background(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(0f, Float.POSITIVE_INFINITY)))
                .drawBehind { cornerBrackets(ink.copy(alpha = 0.9f)) },
        ) {
            CardFace(ink, emboss = !(c.isDark && look == CardLook.IDLE))
        }
    }
}

@Composable
private fun BoxScope.CardFace(ink: Color, emboss: Boolean) {
    // Embossed word mark, like the "Brick" logo pressed into the puck.
    Text(
        "savvy",
        modifier = Modifier.align(Alignment.Center),
        fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-1).sp,
        color = ink,
        style = androidx.compose.ui.text.TextStyle(shadow = if (emboss) Shadow(Color.White.copy(alpha = 0.7f), Offset(0f, 2f), 1f) else null),
    )
    Icon(Icons.Rounded.Contactless, null, tint = ink, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(20.dp))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cornerBrackets(color: Color) {
    val inset = size.minDimension * 0.12f
    val len = size.minDimension * 0.16f
    val stroke = 3.dp.toPx()
    val r = size.minDimension * 0.08f
    val w = size.width; val h = size.height
    // Each corner: a short L with a rounded knee, the scan-frame motif from the Brick puck.
    fun corner(x: Float, y: Float, sx: Float, sy: Float) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x, y + sy * len)
            lineTo(x, y + sy * r)
            quadraticTo(x, y, x + sx * r, y)
            lineTo(x + sx * len, y)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    corner(inset, inset, 1f, 1f)
    corner(w - inset, inset, -1f, 1f)
    corner(inset, h - inset, 1f, -1f)
    corner(w - inset, h - inset, -1f, -1f)
}

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

/** Launcher icon of an installed app, loaded off the main thread and cached. */
@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    var bmp by remember(pkg) { mutableStateOf(iconCache[pkg]) }
    LaunchedEffect(pkg) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) { loadIcon(context, pkg) }
    }
    val c = savvyColors
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(c.cardStrong), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

private fun loadIcon(context: Context, pkg: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap().also { iconCache[pkg] = it }
}.getOrNull()

fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
}.getOrDefault(pkg)
