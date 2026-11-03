// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

/** Ready-made primary colours; the first one is the default violet. */
val PRIMARY_PRESETS = intArrayOf(
    Defaults.PREF_PRIMARY_COLOR,
    0xFF3D7BFF.toInt(), // blue
    0xFF39BCDB.toInt(), // cyan, the colour the Enter skin is painted in
    0xFFEC4899.toInt(), // pink
    0xFFEF4444.toInt(), // red
    0xFFF97316.toInt(), // orange
    0xFFEAB308.toInt(), // yellow
    0xFF22C55E.toInt(), // green
    0xFF38FFA5.toInt(), // mint
)

/** One colour for Enter, the long-press popups and the mic glow: preset swatches plus a custom colour picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrimaryColorPreference(setting: Setting) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val current = prefs.getInt(Settings.PREF_PRIMARY_COLOR, Defaults.PREF_PRIMARY_COLOR)
    // 0 = closed, 1 = presets, 2 = custom picker
    var dialog by remember { mutableIntStateOf(0) }
    fun save(color: Int) {
        prefs.edit { putInt(Settings.PREF_PRIMARY_COLOR, color) }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    Preference(
        name = setting.title,
        description = setting.description,
        onClick = { dialog = 1 },
    ) { Swatch(current, selected = false, size = 28) }

    if (dialog == 1)
        ThreeButtonAlertDialog(
            onDismissRequest = { dialog = 0 },
            onConfirmed = { },
            confirmButtonText = null,
            title = { Text(setting.title) },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PRIMARY_PRESETS.forEach { c ->
                            Swatch(c, selected = c == current, size = 44,
                                modifier = Modifier.clickable { save(c); dialog = 0 })
                        }
                    }
                    TextButton(onClick = { dialog = 2 }) { Text(stringResource(R.string.primary_color_custom)) }
                }
            },
        )
    if (dialog == 2)
        ColorPickerDialog(
            onDismissRequest = { dialog = 0 },
            initialColor = current,
            title = setting.title,
            showDefault = current != Defaults.PREF_PRIMARY_COLOR,
            onDefault = { save(Defaults.PREF_PRIMARY_COLOR) },
        ) { save(it or 0xFF000000.toInt()) } // the Enter cap can't be see-through
}

@Composable
private fun Swatch(color: Int, selected: Boolean, size: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(size.dp)
            .border(if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(if (selected) 5.dp else 0.dp)
            .background(Color(color), shape)
    )
}
