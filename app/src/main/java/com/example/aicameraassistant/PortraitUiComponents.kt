package com.example.aicameraassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PortraitEffect(
    val key: String,
    val displayName: String,
    val symbol: String
) {
    Blur("blur", "Blur", "B"),
    Studio("studio", "Studio", "S"),
    Mono("mono", "Mono", "M"),
    Backdrop("backdrop", "Backdrop", "B"),
    LowKeyMono("low_key_mono", "Low key mono", "L"),
    HighKeyMono("high_key_mono", "High key mono", "H"),
    ColorPoint("color_point", "Color point", "C");

    companion object {
        val selectable: List<PortraitEffect> =
            listOf(Blur, Studio, Mono, Backdrop, LowKeyMono, HighKeyMono, ColorPoint)

        fun fromKey(key: String): PortraitEffect =
            selectable.firstOrNull { it.key == key } ?: Blur
    }
}

@Composable
fun PortraitPreviewOverlay(
    effectKey: String,
    strength: Int,
    status: String = "Portrait ready",
    faceLeft: Double = 0.0,
    faceTop: Double = 0.0,
    faceRight: Double = 0.0,
    faceBottom: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val effect = PortraitEffect.fromKey(effectKey)
    val clampedStrength = strength.coerceIn(1, 7)
    val strengthScale = clampedStrength / 7f
    val vignetteAlpha = 0.18f + (0.30f * strengthScale)
    val effectAlpha = 0.45f + (0.55f * strengthScale)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (effect) {
                PortraitEffect.Studio -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f * effectAlpha),
                                Color(0xFFFFD54F).copy(alpha = 0.10f * effectAlpha),
                                Color.Transparent
                            ),
                            center = center.copy(y = size.height * 0.42f),
                            radius = maxOf(size.width, size.height) * 0.42f
                        ),
                        radius = maxOf(size.width, size.height) * 0.58f,
                        center = center.copy(y = size.height * 0.42f)
                    )
                }

                PortraitEffect.Mono -> {
                    drawRect(Color.Black.copy(alpha = 0.16f * effectAlpha))
                    drawRect(Color.White.copy(alpha = 0.06f * effectAlpha))
                }

                PortraitEffect.LowKeyMono -> {
                    drawRect(Color.Black.copy(alpha = 0.34f * effectAlpha))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f * effectAlpha),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.42f * effectAlpha)
                            ),
                            center = center.copy(y = size.height * 0.44f),
                            radius = maxOf(size.width, size.height) * 0.42f
                        ),
                        radius = maxOf(size.width, size.height),
                        center = center.copy(y = size.height * 0.44f)
                    )
                }

                PortraitEffect.HighKeyMono -> {
                    drawRect(Color.White.copy(alpha = 0.13f * effectAlpha))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f * effectAlpha),
                                Color.White.copy(alpha = 0.10f * effectAlpha),
                                Color.Transparent
                            ),
                            center = center.copy(y = size.height * 0.40f),
                            radius = maxOf(size.width, size.height) * 0.46f
                        ),
                        radius = maxOf(size.width, size.height) * 0.68f,
                        center = center.copy(y = size.height * 0.40f)
                    )
                }

                PortraitEffect.ColorPoint -> {
                    drawRect(Color.Black.copy(alpha = 0.18f * effectAlpha))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.20f * effectAlpha)
                            ),
                            center = center,
                            radius = maxOf(size.width, size.height) * 0.34f
                        ),
                        radius = maxOf(size.width, size.height),
                        center = center
                    )
                }

                PortraitEffect.Backdrop -> {
                    drawRect(Color.Black.copy(alpha = 0.22f * effectAlpha))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.28f * effectAlpha)
                            ),
                            center = center,
                            radius = maxOf(size.width, size.height) * 0.38f
                        ),
                        radius = maxOf(size.width, size.height),
                        center = center
                    )
                }

                PortraitEffect.Blur -> Unit
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = vignetteAlpha)
                    ),
                    center = center,
                    radius = maxOf(size.width, size.height) * 0.72f
                ),
                radius = maxOf(size.width, size.height),
                center = center
            )
        }

        val hasFaceFrame =
            faceRight > faceLeft &&
                faceBottom > faceTop &&
                faceLeft in 0.0..1.0 &&
                faceTop in 0.0..1.0

        if (hasFaceFrame) {
            BoxWithNormalizedBounds(
                left = faceLeft,
                top = faceTop,
                right = faceRight,
                bottom = faceBottom,
                borderColor = portraitFrameColor(effect)
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.58f)
                    .height(220.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .border(2.dp, portraitFrameColor(effect), RoundedCornerShape(34.dp))
                    .background(Color.White.copy(alpha = 0.035f))
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 116.dp)
                .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = status,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${effect.displayName} / Strength $clampedStrength",
                color = Color(0xFFFFD54F),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BoxWithNormalizedBounds(
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
    borderColor: Color
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        val boxWidth = (width * (right - left).toFloat()).coerceAtLeast(96.dp)
        val boxHeight = (height * (bottom - top).toFloat()).coerceAtLeast(128.dp)
        val x = (width * left.toFloat()).coerceIn(0.dp, width - boxWidth)
        val y = (height * top.toFloat()).coerceIn(0.dp, height - boxHeight)

        Box(
            modifier = Modifier
                .padding(start = x, top = y)
                .width(boxWidth)
                .height(boxHeight)
                .clip(RoundedCornerShape(34.dp))
                .border(2.dp, borderColor, RoundedCornerShape(34.dp))
                .background(Color.White.copy(alpha = 0.035f))
        )
    }
}

fun portraitEffectLabel(effectKey: String): String =
    PortraitEffect.fromKey(effectKey).displayName

fun portraitEffectSymbol(effectKey: String): String =
    PortraitEffect.fromKey(effectKey).symbol

private fun portraitFrameColor(effect: PortraitEffect): Color =
    when (effect) {
        PortraitEffect.Studio -> Color(0xFFFFD54F).copy(alpha = 0.72f)
        PortraitEffect.Mono -> Color.White.copy(alpha = 0.72f)
        PortraitEffect.LowKeyMono -> Color.White.copy(alpha = 0.62f)
        PortraitEffect.HighKeyMono -> Color.White.copy(alpha = 0.82f)
        PortraitEffect.ColorPoint -> Color(0xFFFFD54F).copy(alpha = 0.70f)
        PortraitEffect.Backdrop -> Color(0xFFFFD54F).copy(alpha = 0.62f)
        PortraitEffect.Blur -> Color.White.copy(alpha = 0.58f)
    }
