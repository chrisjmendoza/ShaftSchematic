package com.android.shaftschematic.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Persistable preview color selection.
 *
 * Uses [PreviewColorPreset] for user-friendly choices (Stainless/Steel/Bronze),
 * and supports a theme-based "Custom" via [customRole].
 */
data class PreviewColorSetting(
    val preset: PreviewColorPreset,
    val customRole: PreviewColorRole = PreviewColorRole.PRIMARY,
) {
    fun uiLabel(): String = preset.uiLabel()

    fun resolve(scheme: ColorScheme): Color = preset.resolve(scheme, customRole)
}
