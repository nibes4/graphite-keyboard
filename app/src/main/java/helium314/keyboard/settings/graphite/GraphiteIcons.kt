// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.graphite

import androidx.compose.ui.graphics.vector.ImageVector

/** Row illustrations from the approved mockups: ink outline plus one violet accent, 32x32. */
object GIcons {
    private val A = G.Accent
    private val M = G.Muted

    val search = illustration("search", Part(circle(10f, 10f, 7f), width = 1.8f), Part("M21 21l-6-6", width = 1.8f), size = 24f)
    val close = illustration("close", Part("M18 6L6 18M6 6l12 12", width = 1.8f), size = 24f)
    /** Empty search: Tabler zoom-cancel, the cross in violet. */
    val nothing = illustration("nothing", Part(circle(14f, 14f, 9f)), Part("M27 27l-6.5-6.5"), Part("M11 11l6 6M17 11l-6 6", A, 2.2f))

    val languages = illustration("languages",
        Part(rect(3f, 11f, 26f, 10f, 3f)),
        Part("M11 16h10M18.5 13.5L21 16l-2.5 2.5M13.5 13.5L11 16l2.5 2.5", A, 2f))
    val swipe = illustration("swipe",
        *listOf(7f to 10f, 16f to 10f, 25f to 10f, 7f to 22f, 16f to 22f, 25f to 22f)
            .map { Part(circle(it.first, it.second, 1.7f), M, fill = true) }.toTypedArray(),
        Part("M7 22C9 9 15 8 16 16s8 6 9-6", A, 2.2f))
    val correction = illustration("correction",
        Part(rect(3f, 9f, 26f, 14f, 4f)), Part("M8 16h7"), Part("M18.5 16l2.6 2.6L25 13.5", A, 2.2f))
    val dictionaries = illustration("dictionaries",
        Part("M16 9.5C13 7.5 8.5 7 5 8v15c3.5-1 8-.5 11 1.5 3-2 7.5-2.5 11-1.5V8c-3.5-1-8-.5-11 1.5zM16 9.5v15"),
        Part("M20 8v6.5l2-1.6 2 1.6V7.6", A))
    val theme = illustration("theme",
        Part(rect(5f, 11f, 17f, 17f, 4f)), Part("M9 15.5h6", M), Part(circle(24f, 8f, 2.6f), A),
        Part("M24 2.5v1.2M29.5 8h-1.2M27.9 4.1l-.9.9M20.1 4.1l.9.9", A))
    val keys = illustration("keys",
        Part("M6 7h.01M11 7h.01M16 7h.01M21 7h.01M26 7h.01", A, 3f),
        *listOf(4f to 12f, 10.5f to 12f, 17f to 12f, 23.5f to 12f, 7f to 19f, 13.5f to 19f, 20f to 19f)
            .map { Part(rect(it.first, it.second, 4.5f, 4.5f, 1.2f), width = 1.4f) }.toTypedArray(),
        Part(rect(9f, 26f, 14f, 3f, 1.2f), width = 1.4f))
    val strip = illustration("strip",
        Part(rect(3f, 11f, 26f, 10f, 5f)), Part("M13 16h6", A, 2.4f), Part("M10.5 13.5v5M21.5 13.5v5", M))
    val feedback = illustration("feedback",
        Part(rect(10f, 10f, 12f, 12f, 3.2f)),
        Part("M6.5 11.5c-1.6 2.8-1.6 6.2 0 9M25.5 11.5c1.6 2.8 1.6 6.2 0 9M3 9c-2.6 4.4-2.6 9.6 0 14M29 9c2.6 4.4 2.6 9.6 0 14", A))
    val backup = illustration("backup",
        Part(rect(4f, 6f, 24f, 6f, 2f)), Part("M6 12v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V12"),
        Part("M16 23v-7M13 18.5l3-3 3 3", A, 1.8f))
    val about = illustration("about", Part(circle(16f, 16f, 12f)), Part("M16 15v7M16 10.5v.01", A, 2.2f))
    val advanced = illustration("advanced",
        Part("M5 9h13M24 9h3M5 16h4M15 16h12M5 23h9M20 23h7"),
        Part(circle(21f, 9f, 3f), A), Part(circle(12f, 16f, 3f), A), Part(circle(17f, 23f, 3f), A))

    private fun adv(name: String, ink: String, accent: String): ImageVector =
        illustration(name, Part(ink), Part(accent, A, 1.8f))
    val plain = adv("plain", "M5 10h22v12H5z", "M4 27L28 5")
    val colors = adv("colors", "M16 4a12 12 0 1 0 0 24c1.7 0 2.2-1.4 1.4-2.6-.9-1.3-.2-3 1.4-3H22a6 6 0 0 0 6-6C28 9.4 22.6 4 16 4z",
        "M10 15h.01M13 10h.01M19 10h.01")
    val size = adv("size", "M4 13h24v13H4z", "M16 3v7M13 6l3-3 3 3")
    val trail = adv("trail", "M5 25h22", "M6 19C9 7 14 7 16 13s7 5 10-6")
    val toolbar = adv("toolbar", "M3 11h26v10H3z", "M9 16h.01M16 16h.01M23 16h.01")
    val layouts = adv("layouts", "M8 6h20v13H8z", "M4 11v15h20")
    val developer = adv("developer", "M11 10l-6 6 6 6M21 10l6 6-6 6", "M18 7l-4 18")
}
