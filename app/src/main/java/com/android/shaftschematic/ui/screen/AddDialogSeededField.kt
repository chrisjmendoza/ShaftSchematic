package com.android.shaftschematic.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.toMmOrNull
import com.android.shaftschematic.util.valueForSave

/**
 * One Add-dialog number field that opens on a value the dialog computed in millimetres — a Start
 * seeded flush against its neighbour, the default length, a copied Ø, a blend or relief preset.
 *
 * [text] shows that value rounded for display, and parsing the display back moves the value by
 * the rounding: a Start seeded against a liner ending at 35.42422" displays 35.424 and opened on a
 * false "Overlaps AFT Liner", and rounding the other way left a hairline auto body (emulator QA).
 * Until the user edits the text, [mm] is therefore the exact seed — for the inline overlap check,
 * the Add gate and the submitted value alike; once edited, the text is parsed as typed (the
 * [valueForSave] posture of the edit dialogs). A blur or Done that hands back the text unchanged
 * is not an edit. A field that opened empty ([seedMm] null) always parses.
 */
@Stable
internal class SeededMmField(seedMm: Float?, text: String, edited: Boolean = false) {
    var seedMm: Float? by mutableStateOf(seedMm)
        private set
    var text: String by mutableStateOf(text)
        private set
    var edited: Boolean by mutableStateOf(edited)
        private set

    /** The field's text callback: a different text is an edit; the same text changes nothing. */
    fun onText(newText: String) {
        if (newText == text) return
        text = newText
        edited = true
    }

    /** Seed the field with [mm] shown as [display] — a preset filled in when a section opens. */
    fun reseed(mm: Float, display: String) {
        seedMm = mm
        text = display
        edited = false
    }

    /** The field's value in mm: the exact seed while unedited, else [text] parsed in [unit]. */
    fun mm(unit: UnitSystem): Float? = valueForSave(text, edited, seedMm) { toMmOrNull(it, unit) }

    companion object {
        /** An empty field — nothing seeded, so the text always parses. */
        fun empty(): SeededMmField = SeededMmField(null, "")

        /** Survives a configuration change (the Add Thread dialog keeps its fields saveable). */
        val Saver: Saver<SeededMmField, Any> = listSaver(
            save = { listOf(it.seedMm ?: Float.NaN, it.text, it.edited) },
            restore = {
                val seed = it[0] as Float
                SeededMmField(seed.takeUnless { s -> s.isNaN() }, it[1] as String, it[2] as Boolean)
            },
        )
    }
}
