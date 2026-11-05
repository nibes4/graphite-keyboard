// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.runtime.Composable
import helium314.keyboard.settings.graphite.GraphiteOnboarding

/** First-run setup. Graphite replaces upstream's wizard with its own screens; the call site stays the same. */
@Composable
fun WelcomeWizard(
    close: () -> Unit,
    @Suppress("UNUSED_PARAMETER") finish: () -> Unit,
) = GraphiteOnboarding(close)
