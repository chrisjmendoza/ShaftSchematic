// file: app/src/main/java/com/android/shaftschematic/ui/screen/SheetSemantics.kt
package com.android.shaftschematic.ui.screen

/**
 * Spoken summaries for the app's five white-sheet canvases (undercut overview/detail, wear
 * overview/detail, runout preview). Each canvas draws printed-sheet ink with no accessible
 * structure of its own — a screen reader sees a blank surface — so every draw site attaches one
 * of these as its `semantics { contentDescription = … }`.
 *
 * Counts only, never geometry: a diameter or a length spoken here would be a measurement read
 * off a screen reader rather than off the print, and nothing may depend on that. Each summary
 * also names the real accessible path for editing — the list rows or the canvas gesture that
 * already exists on that tab — because the canvas's own tap/drag targets are not reachable by
 * TalkBack.
 *
 * Pure: no Android imports, so this file is plain-JUnit testable ([SheetSemanticsTest]).
 */
object SheetSemantics {

    /** "1 <singular>" or "N <singular>s" (irregular plurals take an explicit [plural]). */
    private fun count(n: Int, singular: String, plural: String = "${singular}s"): String =
        if (n == 1) "1 $singular" else "$n $plural"

    /** Undercut Drawing tab — overview canvas (all recorded cuts on the full shaft). */
    fun undercutOverview(undercutCount: Int): String =
        "Undercut drawing. ${count(undercutCount, "undercut section")}. " +
            "Edit undercuts from the list below."

    /** Undercut Drawing tab — detail window opened on one strip/cluster. */
    fun undercutDetail(undercutCount: Int): String =
        "Undercut detail window. ${count(undercutCount, "undercut section")} in view."

    /** Wear Document tab — overview canvas (tap a component to inspect/mark wear). */
    fun wearOverview(wearAreaCount: Int, pitCount: Int, diaReadingCount: Int): String =
        "Wear drawing. ${count(wearAreaCount, "wear area")}, ${count(pitCount, "pit")}, " +
            "${count(diaReadingCount, "diameter reading")}. " +
            "Tap a body, taper, or liner to inspect wear and mark pits."

    /**
     * Wear Document tab — one liner/body/taper's detail window. [kind] names the component
     * ("Liner", "Taper", "Body"), so a taper's page is not read out as a liner's.
     */
    fun wearDetail(kind: String, wearAreaCount: Int, pitCount: Int, diaReadingCount: Int): String =
        "$kind wear detail. ${count(wearAreaCount, "wear area")}, ${count(pitCount, "pit")}, " +
            "${count(diaReadingCount, "diameter reading")} in view."

    /**
     * Runout tab — preview canvas (draggable stations, tap-to-enter readings). [moveMode] swaps
     * the gesture sentence for Move mode's own — a tap there selects instead of opening the
     * reading dialog, so the plain instructions would read out the wrong gestures.
     */
    fun runoutPreview(stationCount: Int, readingCount: Int, moveMode: Boolean = false): String =
        "Runout drawing. ${count(stationCount, "station")}, " +
            "${count(readingCount, "reading")} entered. " +
            if (moveMode) {
                "Move mode on: drag a bubble to move it; tap a bubble or component to select it."
            } else {
                "Long-press a bubble to move it; tap to enter a reading."
            }
}
