package com.android.shaftschematic.ui.viewmodel

/**
 * ShaftViewModelFinal — the final drawing's lifecycle: start it, reset it, drop it.
 *
 * Everything that EDITS a final drawing goes through the ordinary component mutators with
 * `target = SpecTarget.FINAL`; only these three own the existence of the second geometry.
 * Each one is a plain write to `_finalSpec`, so it rides the same undo recorder as any
 * drawing edit and the same autosave combine as any spec change.
 *
 * Extracted from ShaftViewModel to keep the final drawing's boundary actions grouped by
 * concern. All functions are extensions on ShaftViewModel and access internal-visibility
 * backing fields declared in the primary class file.
 */

/**
 * Begin the final drawing as a structural copy of the original — component ids INCLUDED, so
 * per-component unit overrides (keyed by resolved id) and a future before/after comparison
 * line the two drawings up component for component.
 *
 * No-op when a final drawing already exists: this is the "Start from original" action, and
 * silently replacing work already done on the final is [resetFinalSpec]'s job, behind its own
 * confirmation.
 */
fun ShaftViewModel.startFinalSpec() {
    if (_finalSpec.value != null) return
    _finalSpec.value = _spec.value
}

/**
 * Replace the final drawing with a fresh copy of the original ("Reset to original"). Unlike
 * [startFinalSpec] this always overwrites — it is the deliberate throw-away — and, like every
 * other write here, it is undoable.
 */
fun ShaftViewModel.resetFinalSpec() {
    _finalSpec.value = _spec.value
}

/** Drop the final drawing entirely; the document is back to having only its original. */
fun ShaftViewModel.discardFinalSpec() {
    _finalSpec.value = null
}
