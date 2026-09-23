package com.android.shaftschematic.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins singular/plural/zero wording for every [SheetSemantics] spoken summary. */
class SheetSemanticsTest {

    // ── Undercut overview ──────────────────────────────────────────────────

    @Test
    fun `undercut overview zero`() {
        assertEquals(
            "Undercut drawing. 0 undercut sections. Edit undercuts from the list below.",
            SheetSemantics.undercutOverview(0),
        )
    }

    @Test
    fun `undercut overview singular`() {
        assertEquals(
            "Undercut drawing. 1 undercut section. Edit undercuts from the list below.",
            SheetSemantics.undercutOverview(1),
        )
    }

    @Test
    fun `undercut overview plural`() {
        assertEquals(
            "Undercut drawing. 3 undercut sections. Edit undercuts from the list below.",
            SheetSemantics.undercutOverview(3),
        )
    }

    // ── Undercut detail ────────────────────────────────────────────────────

    @Test
    fun `undercut detail zero`() {
        assertEquals(
            "Undercut detail window. 0 undercut sections in view.",
            SheetSemantics.undercutDetail(0),
        )
    }

    @Test
    fun `undercut detail singular`() {
        assertEquals(
            "Undercut detail window. 1 undercut section in view.",
            SheetSemantics.undercutDetail(1),
        )
    }

    @Test
    fun `undercut detail plural`() {
        assertEquals(
            "Undercut detail window. 2 undercut sections in view.",
            SheetSemantics.undercutDetail(2),
        )
    }

    // ── Wear overview ──────────────────────────────────────────────────────

    @Test
    fun `wear overview zero`() {
        assertEquals(
            "Wear drawing. 0 wear areas, 0 pits, 0 diameter readings. " +
                "Tap a body, taper, or liner to inspect wear and mark pits.",
            SheetSemantics.wearOverview(0, 0, 0),
        )
    }

    @Test
    fun `wear overview singular each`() {
        assertEquals(
            "Wear drawing. 1 wear area, 1 pit, 1 diameter reading. " +
                "Tap a body, taper, or liner to inspect wear and mark pits.",
            SheetSemantics.wearOverview(1, 1, 1),
        )
    }

    @Test
    fun `wear overview plural mixed counts`() {
        assertEquals(
            "Wear drawing. 2 wear areas, 5 pits, 3 diameter readings. " +
                "Tap a body, taper, or liner to inspect wear and mark pits.",
            SheetSemantics.wearOverview(2, 5, 3),
        )
    }

    // ── Wear detail ─────────────────────────────────────────────────────────

    @Test
    fun `wear detail zero`() {
        assertEquals(
            "Liner wear detail. 0 wear areas, 0 pits, 0 diameter readings in view.",
            SheetSemantics.wearDetail(0, 0, 0),
        )
    }

    @Test
    fun `wear detail singular each`() {
        assertEquals(
            "Liner wear detail. 1 wear area, 1 pit, 1 diameter reading in view.",
            SheetSemantics.wearDetail(1, 1, 1),
        )
    }

    @Test
    fun `wear detail plural mixed counts`() {
        assertEquals(
            "Liner wear detail. 4 wear areas, 2 pits, 6 diameter readings in view.",
            SheetSemantics.wearDetail(4, 2, 6),
        )
    }

    // ── Runout preview ─────────────────────────────────────────────────────

    @Test
    fun `runout preview zero`() {
        assertEquals(
            "Runout drawing. 0 stations, 0 readings entered. " +
                "Long-press a bubble to move it; tap to enter a reading.",
            SheetSemantics.runoutPreview(0, 0),
        )
    }

    @Test
    fun `runout preview singular`() {
        assertEquals(
            "Runout drawing. 1 station, 1 reading entered. " +
                "Long-press a bubble to move it; tap to enter a reading.",
            SheetSemantics.runoutPreview(1, 1),
        )
    }

    @Test
    fun `runout preview plural`() {
        assertEquals(
            "Runout drawing. 6 stations, 4 readings entered. " +
                "Long-press a bubble to move it; tap to enter a reading.",
            SheetSemantics.runoutPreview(6, 4),
        )
    }
}
