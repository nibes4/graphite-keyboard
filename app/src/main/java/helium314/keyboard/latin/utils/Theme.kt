// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import helium314.keyboard.settings.graphite.G

/** Graphite settings are light only: violet accent, Geist type, headings at SemiBold at most. */
@Composable
fun Theme(@Suppress("UNUSED_PARAMETER") dark: Boolean = false, content: @Composable () -> Unit) {
    val m = Typography()
    // Geist is drawn for zero tracking; Material's default letter spacing makes reused rows look foreign
    fun TextStyle.g(weight: FontWeight? = fontWeight) = copy(fontFamily = G.Geist, fontWeight = weight, letterSpacing = 0.sp)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = G.Accent,
            onSurface = G.Ink,
            onSurfaceVariant = G.Sub,
            background = G.Bg,
            surface = G.Bg,
            surfaceContainer = G.Card,
            surfaceContainerHigh = G.Card,
        ),
        typography = Typography(
            displayLarge = m.displayLarge.g(), displayMedium = m.displayMedium.g(), displaySmall = m.displaySmall.g(),
            headlineLarge = m.headlineLarge.g(), headlineMedium = m.headlineMedium.g(), headlineSmall = m.headlineSmall.g(),
            titleLarge = m.titleLarge.g(FontWeight.SemiBold), titleMedium = m.titleMedium.g(FontWeight.SemiBold),
            titleSmall = m.titleSmall.g(FontWeight.SemiBold),
            bodyLarge = m.bodyLarge.g(), bodyMedium = m.bodyMedium.g(), bodySmall = m.bodySmall.g(),
            labelLarge = m.labelLarge.g(), labelMedium = m.labelMedium.g(), labelSmall = m.labelSmall.g(),
        ),
        content = content
    )
}

const val previewDark = false
