package app.aaps.ui.compose.dashboard.aimibrasil

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.ui.UiStrings

internal enum class AimiBrVisualStyle {
    NEUTRAL,
    DYNAMIC_BG,
    LIQUID_GLASS;

    val titleRes get() = when (this) {
        NEUTRAL -> UiStrings.aimibr_style_neutral_title
        DYNAMIC_BG -> UiStrings.aimibr_style_dynamic_title
        LIQUID_GLASS -> UiStrings.aimibr_style_glass_title
    }

    val subtitleRes get() = when (this) {
        NEUTRAL -> UiStrings.aimibr_style_neutral_sub
        DYNAMIC_BG -> UiStrings.aimibr_style_dynamic_sub
        LIQUID_GLASS -> UiStrings.aimibr_style_glass_sub
    }

    fun next(): AimiBrVisualStyle = when (this) {
        NEUTRAL -> DYNAMIC_BG
        DYNAMIC_BG -> LIQUID_GLASS
        LIQUID_GLASS -> NEUTRAL
    }
}



internal fun Modifier.aimiBrSurface(
    isDark: Boolean,
    style: AimiBrVisualStyle,
    zoneAccent: Color,
    radius: Dp = 18.dp,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val neutralTint = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)

    if (style != AimiBrVisualStyle.LIQUID_GLASS) {
        val tint = if (style == AimiBrVisualStyle.DYNAMIC_BG) zoneAccent else neutralTint
        val bg = if (isDark) {
            listOf(tint.copy(alpha = 0.16f), Color(0xFF14171D).copy(alpha = 0.88f))
        } else {
            listOf(tint.copy(alpha = 0.14f), Color.White.copy(alpha = 0.90f))
        }
        val border = if (isDark) {
            listOf(tint.copy(alpha = 0.42f), Color.White.copy(alpha = 0.10f))
        } else {
            listOf(tint.copy(alpha = 0.48f), Color.White.copy(alpha = 0.80f))
        }
        return this
            .clip(shape)
            .background(Brush.verticalGradient(bg))
            .border(BorderStroke(1.dp, Brush.verticalGradient(border)), shape)
    }

    val glassFill = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.13f),
                zoneAccent.copy(alpha = 0.08f),
                Color(0xFF0F172A).copy(alpha = 0.42f),
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 500f)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.72f),
                zoneAccent.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.45f),
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 500f)
        )
    }

    val specularBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.52f else 0.95f),
            Color.White.copy(alpha = 0.08f),
            zoneAccent.copy(alpha = 0.35f),
        ),
        start = Offset(0f, 0f),
        end = Offset(500f, 500f)
    )

    return this
        .clip(shape)
        .background(glassFill)
        .drawWithContent {
            drawContent()
            val rPx = radius.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.14f else 0.40f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = size.height * 0.38f
                ),
                size = Size(size.width, size.height * 0.38f),
                cornerRadius = CornerRadius(rPx, rPx)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (isDark) 0.09f else 0.35f),
                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                cornerRadius = CornerRadius((rPx - 1.5.dp.toPx()).coerceAtLeast(0f)),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        .border(BorderStroke(1.2.dp, specularBorder), shape)
}

@Composable
internal fun AimiBrLiquidBackdrop(
    isDark: Boolean,
    zoneGlow: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquidOrbs")
    val drift by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbDrift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0EA5E9).copy(alpha = if (isDark) 0.28f else 0.22f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f + drift, size.height * 0.16f),
                radius = size.minDimension * 0.65f
            ),
            radius = size.minDimension * 0.65f,
            center = Offset(size.width * 0.18f + drift, size.height * 0.16f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    zoneGlow.copy(alpha = if (isDark) 0.24f else 0.18f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.85f - drift, size.height * 0.36f),
                radius = size.minDimension * 0.60f
            ),
            radius = size.minDimension * 0.60f,
            center = Offset(size.width * 0.85f - drift, size.height * 0.36f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6366F1).copy(alpha = if (isDark) 0.22f else 0.16f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.35f, size.height * 0.72f + drift),
                radius = size.minDimension * 0.70f
            ),
            radius = size.minDimension * 0.70f,
            center = Offset(size.width * 0.35f, size.height * 0.72f + drift)
        )
    }
}
