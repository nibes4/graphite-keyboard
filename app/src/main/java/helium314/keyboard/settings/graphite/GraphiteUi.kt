// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.graphite

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R

/** Graphite settings look, taken 1:1 from the approved mockups (Main.dc.html and siblings). */
object G {
    val Bg = Color(0xFFEEEFF1)
    val Card = Color.White
    val Ink = Color(0xFF111214)
    val Sub = Color(0xFF5E626A)
    val Tile = Color(0xFFF1F2F4)
    val Chevron = Color(0xFF9A9DA3)
    val Accent = Color(0xFF7C3AED)
    val Track = Color(0xFFD3D5DA)
    val Muted = Color(0xFFB7BAC0)
    val HeroBg = Color(0xFF2A2A2A)

    val Geist = FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
        Font(R.font.geist_semibold, FontWeight.SemiBold),
    )
    val title = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, color = Ink)
    val sub = TextStyle(fontFamily = Geist, fontSize = 13.5.sp, lineHeight = 17.5.sp, color = Sub)
    val group = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, color = Sub)
    val note = TextStyle(fontFamily = Geist, fontSize = 13.5.sp, lineHeight = 19.sp, color = Sub)
    val wordmark = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 30.sp,
        letterSpacing = (-0.035).em, color = Ink)
}

/** One stroke (or filled shape) of a mockup illustration, in the 32x32 SVG coordinates. */
class Part(val d: String, val color: Color = G.Ink, val width: Float = 1.6f, val fill: Boolean = false)

fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},$y H${x + w - r} A$r,$r 0 0 1 ${x + w},${y + r} V${y + h - r} A$r,$r 0 0 1 ${x + w - r},${y + h} " +
        "H${x + r} A$r,$r 0 0 1 $x,${y + h - r} V${y + r} A$r,$r 0 0 1 ${x + r},$y Z"

fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy A$r,$r 0 1 0 ${cx + r},$cy A$r,$r 0 1 0 ${cx - r},$cy Z"

fun illustration(name: String, vararg parts: Part, size: Float = 32f): ImageVector =
    ImageVector.Builder(name, size.dp, size.dp, size, size).apply {
        parts.forEach { p ->
            if (p.fill) addPath(addPathNodes(p.d), fill = SolidColor(p.color))
            else addPath(addPathNodes(p.d), stroke = SolidColor(p.color), strokeLineWidth = p.width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

/** Group label above a white card with rounded corners. */
@Composable
fun GGroup(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) Text(label, style = G.group, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.Card).padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
private fun RowBody(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(G.Tile), contentAlignment = Alignment.Center) {
            Image(icon, null, Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = G.title)
            if (subtitle != null) Text(subtitle, style = G.sub, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** A row that opens another screen. */
@Composable
fun GRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) =
    RowBody(icon, title, subtitle, Modifier.clickable(onClick = onClick)) { Chevron() }

/** A row with a switch; the whole row toggles it. */
@Composable
fun GSwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
    RowBody(icon, title, subtitle, Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange)) { GSwitch(checked) }

@Composable
fun GSwitch(checked: Boolean) {
    val track by animateColorAsState(if (checked) G.Accent else G.Track, label = "track")
    val knob by animateDpAsState(if (checked) 24.dp else 4.dp, label = "knob")
    Box(Modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(track)) {
        Box(Modifier.offset(x = knob, y = 4.dp).size(24.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))
    }
}

private val chevron = illustration("chevron", Part("M9 6l6 6-6 6", G.Chevron, 2f), size = 24f)

@Composable
fun Chevron() = Image(chevron, null, Modifier.size(20.dp))
