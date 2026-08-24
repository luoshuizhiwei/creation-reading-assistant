package com.creationreadingassistant.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.creationreadingassistant.ui.components.PaperNoise

/** Immutable paper rendering contract shared by the settled and animated reader layers. */
data class ReaderPaperSurfaceSpec(
    val base: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val textureEnabled: Boolean,
) {
    companion object {
        fun solid(color: Color): ReaderPaperSurfaceSpec = ReaderPaperSurfaceSpec(
            base = color,
            gradientTop = color,
            gradientBottom = color,
            textureEnabled = false,
        )
    }
}

fun ReaderPaperPalette.surfaceSpec(textureEnabled: Boolean): ReaderPaperSurfaceSpec =
    ReaderPaperSurfaceSpec(
        base = bg,
        gradientTop = lerp(bg, Color.White, 0.04f),
        gradientBottom = lerp(bg, Color.Black, 0.03f),
        textureEnabled = textureEnabled,
    )

fun Modifier.readerPaperSurface(spec: ReaderPaperSurfaceSpec): Modifier =
    background(spec.base)
        .background(Brush.verticalGradient(listOf(spec.gradientTop, spec.gradientBottom)))
        .then(
            if (spec.textureEnabled) {
                Modifier.background(PaperNoise.brush(), alpha = 0.04f)
            } else {
                Modifier
            },
        )
