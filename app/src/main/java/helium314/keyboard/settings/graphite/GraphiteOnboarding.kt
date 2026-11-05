// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.graphite

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * First-run onboarding, built 1:1 from the approved mockups (Onb1..4 and OnbReturn).
 * Steps: 0 welcome, 1 turn the keyboard on in Android, 2 make it the main keyboard, 3 try it.
 * The Android calls are the same as in upstream's wizard: the settings screen is opened for a result, the input
 * method picker is shown, and the "is this the current keyboard" state is polled until it flips.
 */

private val DarkBg = Color(0xFF0D0E10)
private val Violet = Color(0xFF8B5CF6)
private val VioletSoft = Color(0xFFB9A2FF)
private val NoteBg = Color(0xFFF1ECFF)
private val DotOff = Color(0xFFB4B7BD)
private val Faint = Color(0xFF8A8D93)

private fun ts(size: Float, weight: FontWeight, lh: Float, color: Color, ls: Float = 0f) = TextStyle(
    fontFamily = G.Geist, fontWeight = weight, fontSize = size.sp, lineHeight = lh.sp, letterSpacing = ls.em, color = color,
)

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun GraphiteOnboarding(close: () -> Unit) {
    val ctx = LocalContext.current
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun determine(): Int = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm) -> 1
        !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm) -> 2
        else -> 3
    }
    var step by rememberSaveable { mutableIntStateOf(determine().let { if (it == 1) 0 else it }) }
    // true after the person came back from Android without doing what the step asked
    var returned by rememberSaveable { mutableStateOf(false) }
    var pickerOpened by rememberSaveable { mutableStateOf(false) }

    val settingsIntent = remember { Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val next = determine()
        if (next == 1) returned = true
        else {
            returned = false
            step = next
        }
    }

    // step 2 ends by itself the moment Graphite becomes the current keyboard
    LaunchedEffect(step) {
        if (step == 2) {
            withContext(Dispatchers.IO) {
                while (!UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) delay(50)
            }
            returned = false
            step = 3
        }
    }
    // the picker is a dialog: when our window loses focus and gets it back and Graphite is still not current, show the hint
    val focused = LocalWindowInfo.current.isWindowFocused
    var lostFocus by remember { mutableStateOf(false) }
    LaunchedEffect(focused, step) {
        if (step != 2 || !pickerOpened) return@LaunchedEffect
        if (!focused) lostFocus = true
        else if (lostFocus) {
            delay(400)
            lostFocus = false
            if (!UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) returned = true
        }
    }

    // the welcome screen is dark, so the status and navigation bar icons turn light while it is shown
    val view = LocalView.current
    DisposableEffect(step == 0) {
        val window = view.context.findActivity()?.window
        if (step == 0 && window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        } else onDispose { }
    }

    BackHandler(enabled = step == 1 || step == 2) {
        returned = false
        pickerOpened = false
        step -= 1
    }

    Surface(Modifier.fillMaxSize(), color = if (step == 0) DarkBg else G.Bg) {
        Crossfade(step, label = "onboarding") { s ->
            when (s) {
                0 -> WelcomeScreen { step = 1 }
                1 -> EnableScreen(returned, onBack = { returned = false; step = 0 }) { launcher.launch(settingsIntent) }
                2 -> PickScreen(returned, onBack = { returned = false; pickerOpened = false; step = 1 }) {
                    pickerOpened = true
                    imm.showInputMethodPicker()
                }
                else -> TryScreen(close)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Welcome (dark)

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DarkBg)) {
        // violet glow behind the hero, an ellipse 520 x 420 as in the mockup
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 390.dp.toPx()
            val r = 260.dp.toPx() * k
            val c = Offset(size.width / 2f - 65.dp.toPx() * k, size.height - 304.dp.toPx() * k)
            withTransform({ scale(1f, 420f / 520f, pivot = c) }) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFF7C3AED).copy(alpha = 0.55f), Color.Transparent), c, r), r, c)
            }
        }
        // dot matrix behind the hero figure
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(390f / 467f)) {
            val k = size.width / 390f
            OnbDots.data.forEach { d -> drawCircle(Violet.copy(alpha = d[3]), d[2] * k, Offset(d[0] * k, d[1] * k)) }
        }
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).widthIn(max = 520.dp).align(Alignment.TopCenter),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painterResource(R.drawable.graphite_mark), null,
                        Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                    )
                    Text("Graphite", style = G.wordmark.copy(color = Color.White))
                }
                val lines = stringResource(R.string.gonb_welcome_title).split("\n")
                Text(
                    buildAnnotatedString {
                        append(lines.first())
                        if (lines.size > 1) {
                            append("\n")
                            withStyle(SpanStyle(color = VioletSoft)) { append(lines.drop(1).joinToString("\n")) }
                        }
                    },
                    style = ts(35f, FontWeight.SemiBold, 38.5f, Color.White, -0.02f),
                    modifier = Modifier.padding(top = 40.dp),
                )
                Text(
                    stringResource(R.string.gonb_welcome_sub),
                    style = ts(17f, FontWeight.Normal, 24.65f, Color(0xFFA9ACB3)),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.BottomCenter) {
                val shape = RoundedCornerShape(24.dp)
                Box(
                    Modifier.padding(horizontal = 24.dp).fillMaxWidth().aspectRatio(900f / 717f)
                        .shadow(24.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(shape).background(G.HeroBg).border(1.dp, Color.White.copy(alpha = 0.06f), shape),
                ) {
                    Image(painterResource(R.drawable.onb_hero), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth)
                }
            }
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PrimaryButton(stringResource(R.string.gonb_start), onStart)
                Text(
                    stringResource(R.string.gonb_start_hint),
                    style = ts(14f, FontWeight.Normal, 18f, Color(0xFF8E9198)),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Steps 1..3 (light)

@Composable
private fun EnableScreen(returned: Boolean, onBack: () -> Unit, onOpen: () -> Unit) {
    LightScreen(
        onBack = onBack,
        cta = stringResource(if (returned) R.string.gonb_r1_cta else R.string.gonb_s1_cta),
        onCta = onOpen,
    ) {
        Progress(1)
        if (returned) {
            Heading(stringResource(R.string.gonb_r1_title), null)
            Note(stringResource(R.string.gonb_r1_note))
        } else Heading(stringResource(R.string.gonb_s1_title), stringResource(R.string.gonb_s1_sub))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Orb(if (returned) 272.dp else 296.dp) {
                OrbCard(stringResource(R.string.gonb_orb_keyboards)) {
                    OrbRow("Gboard", on = true, strong = false)
                    OrbRow("Graphite", on = !returned, strong = true, mark = true, highlight = returned)
                    OrbRow(stringResource(R.string.gonb_orb_voice), on = false, strong = false, small = true)
                }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(ShieldIcon, null, Modifier.size(24.dp))
            Text(stringResource(R.string.gonb_s1_security), style = ts(14.5f, FontWeight.Normal, 21f, Color(0xFF3A3D42)))
        }
    }
}

@Composable
private fun PickScreen(returned: Boolean, onBack: () -> Unit, onOpen: () -> Unit) {
    LightScreen(
        onBack = onBack,
        cta = stringResource(if (returned) R.string.gonb_r2_cta else R.string.gonb_s2_cta),
        onCta = onOpen,
    ) {
        Progress(2)
        if (returned) {
            Heading(stringResource(R.string.gonb_r2_title), null)
            Note(stringResource(R.string.gonb_r2_note))
        } else Heading(stringResource(R.string.gonb_s2_title), stringResource(R.string.gonb_s2_sub))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Orb(if (returned) 272.dp else 296.dp) {
                OrbCard(stringResource(R.string.gonb_orb_choose)) {
                    RadioRow("Gboard", selected = returned, highlight = false)
                    RadioRow("Graphite", selected = !returned, highlight = returned)
                }
            }
        }
        Text(
            stringResource(R.string.gonb_s2_note),
            style = G.note, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TryScreen(done: () -> Unit) {
    LightScreen(
        onBack = null,
        topEnd = {
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(G.Accent)
                    .clickable(role = Role.Button, onClick = done).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.gonb_s3_done), style = ts(15f, FontWeight.Medium, 20f, Color.White)) }
        },
        cta = null,
        scroll = false,
    ) {
        Progress(3)
        Heading(stringResource(R.string.gonb_s3_title), null)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Tip(SpaceSwipeIcon, stringResource(R.string.gonb_s3_tip_space))
            Tip(HoldNumberIcon, stringResource(R.string.gonb_s3_tip_number))
        }
        Spacer(Modifier.weight(1f))
        TryField()
    }
}

@Composable
private fun LightScreen(
    onBack: (() -> Unit)?,
    cta: String?,
    modifier: Modifier = Modifier,
    onCta: () -> Unit = {},
    topEnd: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(G.Bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) BackButton(onBack) else Spacer(Modifier.size(1.dp))
                topEnd?.invoke()
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(top = 28.dp, bottom = if (scroll) 16.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                content = body,
            )
            if (cta != null) PrimaryButton(cta, onCta)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pieces

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(G.Accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ts(17f, FontWeight.Medium, 22f, Color.White)) }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val label = stringResource(R.string.gonb_back)
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Image(ArrowLeftIcon, null, Modifier.size(22.dp)) }
}

@Composable
private fun Progress(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..3) Box(Modifier.size(8.dp).clip(CircleShape).background(if (i <= step) G.Accent else DotOff))
        Text(
            stringResource(R.string.gonb_step_of, step),
            style = ts(13f, FontWeight.Medium, 16f, Color(0xFF6B6F76)),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Heading(title: String, sub: String?) {
    Column {
        Text(title, style = ts(25f, FontWeight.SemiBold, 28.75f, G.Ink, -0.015f))
        if (sub != null) Text(sub, style = ts(16f, FontWeight.Normal, 23.2f, G.Sub), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Note(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NoteBg).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(InfoIcon, null, Modifier.size(24.dp))
        Text(text, style = ts(15f, FontWeight.Normal, 21f, Color(0xFF3A3D42)))
    }
}

@Composable
private fun Tip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Image(icon, null, Modifier.size(32.dp))
        }
        Text(text, style = ts(15.5f, FontWeight.Normal, 21f, G.Ink), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TryField() {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.gonb_s3_field)
    LaunchedEffect(Unit) {
        delay(300)
        focus.requestFocus()
        keyboard?.show()
    }
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.White)
            .border(2.dp, G.Accent, RoundedCornerShape(26.dp)).padding(horizontal = 22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Text(stringResource(R.string.gonb_s3_placeholder), style = ts(16f, FontWeight.Normal, 22f, Faint))
        BasicTextField(
            text, { text = it },
            Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
            textStyle = ts(16f, FontWeight.Normal, 22f, G.Ink),
            singleLine = true,
            cursorBrush = SolidColor(G.Accent),
        )
    }
}

/** The big grey disc with a dot pattern that fades out towards the edge, holding a picture of an Android screen. */
@Composable
private fun Orb(size: Dp, content: @Composable BoxScope.() -> Unit) {
    val alt = stringResource(R.string.gonb_orb_alt)
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFFE2E3E6))
            .drawBehind {
                val step = 12.dp.toPx()
                val radius = this.size.minDimension / 2f
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                var y = step / 2f
                while (y < this.size.height) {
                    var x = step / 2f
                    while (x < this.size.width) {
                        val d = (Offset(x, y) - centre).getDistance() / radius
                        if (d <= 1f) {
                            val fade = if (d <= 0.45f) 1f else 1f - 0.75f * ((d - 0.45f) / 0.55f)
                            drawCircle(G.Ink.copy(alpha = 0.26f * fade), 1.5.dp.toPx(), Offset(x, y))
                        }
                        x += step
                    }
                    y += step
                }
            }
            .clearAndSetSemantics { contentDescription = alt },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun OrbCard(label: String, rows: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.width(232.dp)
            .shadow(14.dp, shape, clip = false, ambientColor = G.Ink.copy(alpha = 0.10f), spotColor = G.Ink.copy(alpha = 0.10f))
            .clip(shape).background(Color.White).padding(top = 12.dp, bottom = 6.dp),
    ) {
        Text(label, style = ts(12.5f, FontWeight.Medium, 16f, G.Sub), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
        rows()
    }
}

@Composable
private fun OrbRow(
    name: String, on: Boolean, strong: Boolean, small: Boolean = false, mark: Boolean = false, highlight: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().background(if (highlight) NoteBg else Color.Transparent).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            name,
            style = if (strong) ts(14f, FontWeight.Medium, 18f, G.Ink) else ts(if (small) 13f else 14f, FontWeight.Normal, 18f, Faint),
            modifier = Modifier.weight(1f),
        )
        MiniSwitch(on, ring = mark)
    }
}

@Composable
private fun MiniSwitch(on: Boolean, ring: Boolean) {
    Box(
        Modifier.size(width = 32.dp, height = 19.2.dp).drawBehind {
            if (ring) {
                val r = CornerRadius(size.height)
                drawRoundRect(Violet, Offset(-4.dp.toPx(), -4.dp.toPx()), Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()), CornerRadius(size.height))
                drawRoundRect(Color.White, Offset(-2.4.dp.toPx(), -2.4.dp.toPx()), Size(size.width + 4.8.dp.toPx(), size.height + 4.8.dp.toPx()), r)
            }
            drawRoundRect(if (on) G.Ink else G.Track, cornerRadius = CornerRadius(size.height))
            val knob = 14.4.dp.toPx()
            val inset = 2.4.dp.toPx()
            drawCircle(Color.White, knob / 2f, Offset(if (on) size.width - inset - knob / 2f else inset + knob / 2f, size.height / 2f))
        },
    )
}

@Composable
private fun RadioRow(name: String, selected: Boolean, highlight: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(if (highlight) NoteBg else Color.Transparent).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(20.dp).drawBehind {
                if (highlight || (selected && name == "Graphite")) {
                    drawCircle(Violet, size.minDimension / 2f + 5.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                if (selected) drawCircle(G.Ink, size.minDimension / 2f - 3.dp.toPx(), style = Stroke(6.dp.toPx()))
                else drawCircle(G.Track, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            },
        )
        Text(name, style = if (selected) ts(14f, FontWeight.Medium, 18f, G.Ink) else ts(14f, FontWeight.Normal, 18f, Faint))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Tabler icons and the two tip pictures from the mockups (viewBox 24 / 32)

private val ArrowLeftIcon = illustration("arrow-left", Part("M5 12h14M5 12l6 6M5 12l6-6", G.Ink, 1.8f), size = 24f)
private val ShieldIcon = illustration(
    "shield-check",
    Part("M12 3a12 12 0 0 0 8.5 3A12 12 0 0 1 12 21 12 12 0 0 1 3.5 6 12 12 0 0 0 12 3", G.Accent, 1.7f),
    Part("M9 12l2 2 4-4", G.Accent, 1.7f),
    size = 24f,
)
private val InfoIcon = illustration(
    "info-circle",
    Part(circle(12f, 12f, 9f), G.Accent, 1.7f), Part("M12 9h.01", G.Accent, 1.7f), Part("M11 12h1v4h1", G.Accent, 1.7f),
    size = 24f,
)
private val SpaceSwipeIcon = illustration(
    "space-swipe",
    Part(rect(4f, 13f, 24f, 8f, 3f), G.Ink, 1.6f), Part("M10 9h12M19 6l3 3-3 3", G.Accent, 1.8f),
)
private val HoldNumberIcon = illustration(
    "hold-number",
    Part(rect(10f, 17f, 12f, 11f, 3f), G.Ink, 1.6f),
    Part(rect(9f, 4f, 14f, 10f, 3f), Violet, fill = true),
    Part("M14.3 7.2h3.4l-2 2.1a1.6 1.6 0 1 1-1.4 2.4", Color.White, 1.4f),
)
