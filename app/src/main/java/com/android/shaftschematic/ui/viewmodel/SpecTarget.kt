package com.android.shaftschematic.ui.viewmodel

/**
 * File: SpecTarget.kt
 * Layer: ViewModel
 *
 * Which of the document's two geometries a mutation lands on: the [ORIGINAL] schematic — the
 * record of the shaft as it came in — or the [FINAL] one, the drawing of the shaft as it
 * leaves. Both are whole `ShaftSpec`s, independent under the golden rule: no edit on one ever
 * reaches the other.
 *
 * It is an explicit parameter on every mutator (last position, defaulting to [ORIGINAL]), never
 * hidden ViewModel state. A "current target" flag would put the wrong drawing one tab-switch
 * away from every edit, and the drawing that must never change is the one the shop compares
 * against. The default is what keeps every original-schematic call site byte-identical.
 *
 * A [FINAL] write while the document carries no final spec is a no-op — the editor for it is
 * not reachable then. See `docs/archive/FinalSchematic_PLAN.md` §3.
 */
enum class SpecTarget { ORIGINAL, FINAL }
