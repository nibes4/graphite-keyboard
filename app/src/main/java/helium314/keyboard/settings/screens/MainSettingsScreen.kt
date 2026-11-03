// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.graphite.G
import helium314.keyboard.settings.graphite.GGroup
import helium314.keyboard.settings.graphite.GIcons
import helium314.keyboard.settings.graphite.GRow
import helium314.keyboard.settings.graphite.GSwitchRow
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.PRIMARY_PRESETS

/** Real keyboard captures, one per preset in [PRIMARY_PRESETS] order. */
private val HERO = intArrayOf(
    R.drawable.hero_kb_violet, R.drawable.hero_kb_blue, R.drawable.hero_kb_cyan,
    R.drawable.hero_kb_pink, R.drawable.hero_kb_red, R.drawable.hero_kb_orange,
    R.drawable.hero_kb_yellow, R.drawable.hero_kb_green, R.drawable.hero_kb_mint,
)

/** The capture whose Enter key is closest to [color]; a custom colour gets the nearest preset. */
private fun heroFor(color: Int): Int {
    fun dist(a: Int, b: Int): Int {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }
    return HERO[PRIMARY_PRESETS.indices.minBy { dist(PRIMARY_PRESETS[it], color) }]
}

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onClickDataGathering: () -> Unit, // HeliBoard's gesture data drive, not shown in Graphite
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(TextFieldValue()) }
    var advanced by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_SHOW_ADVANCED_SETTINGS, Defaults.PREF_SHOW_ADVANCED_SETTINGS)) }
    fun closeSearch() { showSearch = false; searchText = TextFieldValue() }
    BackHandler { if (showSearch) closeSearch() else onClickBack() }

    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        // insets outside the scroll, so rows never slide under the clock
        Modifier.fillMaxSize().background(G.Bg).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(painterResource(R.drawable.graphite_mark), null, Modifier.size(40.dp).clip(CircleShape))
                Text("Graphite", style = G.wordmark, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(G.Card)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.g_search)) {
                            if (showSearch) closeSearch() else showSearch = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // while searching the same circle closes it, so there is only one magnifier on screen
                    Image(
                        if (showSearch) GIcons.close else GIcons.search,
                        stringResource(if (showSearch) R.string.g_search_close else R.string.g_search),
                        Modifier.size(22.dp)
                    )
                }
            }
            AnimatedVisibility(showSearch) {
                GSearchField(searchText, { searchText = it }, Modifier.padding(top = 28.dp))
            }
        }

        if (searchText.text.isNotBlank()) {
            val results = SettingsActivity.settingsContainer.filter(searchText.text)
            if (results.isEmpty()) SearchEmpty()
            // HeliBoard's own rows inset 12dp; 4dp more lines their text up with the icon tiles of the main list
            else GGroup(null) { Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) { results.forEach { it.Preference() } } }
            return@Column
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(G.HeroBg).padding(18.dp)) {
            // Nick 2026-11-03: the whole board with some air around it; inner radius = outer minus padding
            Image(
                painterResource(heroFor(prefs.getInt(Settings.PREF_PRIMARY_COLOR, Defaults.PREF_PRIMARY_COLOR))),
                stringResource(R.string.g_hero_description),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.FillWidth
            )
        }

        GGroup(stringResource(R.string.g_group_typing)) {
            GRow(GIcons.languages, stringResource(R.string.g_languages), stringResource(R.string.g_languages_sub), onClickLanguage)
            if (JniUtils.sHaveGestureLib)
                GRow(GIcons.swipe, stringResource(R.string.g_swipe), onClick = onClickGestureTyping)
            GRow(GIcons.correction, stringResource(R.string.g_correction), onClick = onClickTextCorrection)
            GRow(GIcons.dictionaries, stringResource(R.string.g_dictionaries), onClick = onClickDictionaries)
        }
        GGroup(stringResource(R.string.g_group_look)) {
            GRow(GIcons.theme, stringResource(R.string.g_theme), stringResource(R.string.g_theme_sub), onClickAppearance)
            GRow(GIcons.keys, stringResource(R.string.g_keys), stringResource(R.string.g_keys_sub), onClickPreferences)
            GRow(GIcons.strip, stringResource(R.string.g_strip), onClick = onClickToolbar)
            GRow(GIcons.feedback, stringResource(R.string.g_feedback), onClick = onClickPreferences)
        }
        GGroup(stringResource(R.string.g_group_more)) {
            GRow(GIcons.backup, stringResource(R.string.g_backup), stringResource(R.string.g_backup_sub), onClickAdvanced)
            GRow(GIcons.about, stringResource(R.string.g_about), stringResource(R.string.g_version, version), onClickAbout)
        }
        GGroup(null) {
            GSwitchRow(
                GIcons.advanced, stringResource(R.string.g_advanced),
                stringResource(if (advanced) R.string.g_advanced_sub_on else R.string.g_advanced_sub_off),
                advanced
            ) {
                advanced = it
                prefs.edit { putBoolean(Settings.PREF_SHOW_ADVANCED_SETTINGS, it) }
            }
        }
        AnimatedVisibility(advanced) {
            Column {
                GGroup(stringResource(R.string.g_group_advanced)) {
                    GRow(GIcons.plain, stringResource(R.string.g_adv_plain), stringResource(R.string.g_adv_plain_sub), onClickAppearance)
                    GRow(GIcons.colors, stringResource(R.string.g_adv_colors), stringResource(R.string.g_adv_colors_sub), onClickAppearance)
                    GRow(GIcons.size, stringResource(R.string.g_adv_size), stringResource(R.string.g_adv_size_sub), onClickAppearance)
                    if (JniUtils.sHaveGestureLib)
                        GRow(GIcons.trail, stringResource(R.string.g_adv_trail), stringResource(R.string.g_adv_trail_sub), onClickGestureTyping)
                    GRow(GIcons.toolbar, stringResource(R.string.g_adv_toolbar), stringResource(R.string.g_adv_toolbar_sub), onClickToolbar)
                    GRow(GIcons.layouts, stringResource(R.string.g_adv_layouts), stringResource(R.string.g_adv_layouts_sub), onClickLayouts)
                    GRow(GIcons.developer, stringResource(R.string.g_adv_developer), stringResource(R.string.g_adv_developer_sub), onClickAdvanced)
                }
                Text(stringResource(R.string.g_advanced_note), style = G.note, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            }
        }
    }
}

/** Pill field in the card colour: muted magnifier, hint text, violet cursor, keyboard opens right away. */
@Composable
private fun GSearchField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val style = G.title.copy(fontWeight = FontWeight.Normal)
    BasicTextField(
        value, onChange,
        modifier.fillMaxWidth().focusRequester(focus),
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(G.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { inner ->
            Row(
                Modifier.clip(RoundedCornerShape(28.dp)).background(G.Card).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(GIcons.search, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(G.Sub))
                Box(Modifier.weight(1f)) {
                    if (value.text.isEmpty()) Text(stringResource(R.string.g_search), style = style.copy(color = G.Sub))
                    inner()
                }
            }
        }
    )
}

@Composable
private fun SearchEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.padding(bottom = 8.dp).size(88.dp).clip(CircleShape).background(G.Card), contentAlignment = Alignment.Center) {
            Image(GIcons.nothing, null, Modifier.size(40.dp))
        }
        Text(stringResource(R.string.g_search_empty), style = G.title)
        Text(stringResource(R.string.g_search_empty_sub), style = G.note, textAlign = TextAlign.Center)
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(false) {
        MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
