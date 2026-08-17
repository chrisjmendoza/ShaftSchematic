package com.android.shaftschematic.settings

/**
 * App-wide theme selection (Settings → Appearance).
 *
 * LIGHT is the default: it matches the app's original always-light presentation, so an app
 * update never changes the look until the user opts in. SYSTEM follows the device dark-mode
 * setting.
 *
 * The theme governs Compose UI chrome only. Paper-sheet canvases (the undercut / wear /
 * runout drawing surfaces) and PDF output are theme-independent by contract — white sheet,
 * fixed black ink — so a dark theme can never put near-white ink on the white sheet or
 * darken a printed page. See `docs/contracts/Appearance.md`.
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun uiLabel(): String = when (this) {
        SYSTEM -> "System"
        LIGHT -> "Light"
        DARK -> "Dark"
    }

    companion object {
        /** Parses a persisted name; unknown or blank falls back to [LIGHT] (the historical look). */
        fun fromName(raw: String?): AppThemeMode =
            if (raw.isNullOrBlank()) LIGHT else runCatching { valueOf(raw) }.getOrDefault(LIGHT)
    }
}
