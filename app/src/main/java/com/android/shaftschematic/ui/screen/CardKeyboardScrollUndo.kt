package com.android.shaftschematic.ui.screen

import kotlin.math.max
import kotlin.math.min

/**
 * Gives back a card's scroll that the keyboard took.
 *
 * A carousel card scrolls inside itself (`ComponentCard`). When the keyboard opens, the card's
 * viewport shrinks and the focused field is brought into view by scrolling the card — on a tall
 * card (the start card on a tablet) that pushes the title off the top. Compose never scrolls it
 * back when the keyboard closes: the card is still taller than its viewport, so the offset stays
 * legal, and the title stays cut off — on every later visit to the card (on-device QA). This
 * undoes it: while the viewport grows back, the card scrolls up by the height it regained, never
 * above the offset it had before the viewport first shrank, and never down.
 *
 * Scrolling up by no more than the regained height keeps everything that was visible in the small
 * viewport visible in the larger one (the viewport's bottom moved down by the same amount), so a
 * focused field reached with Next while the keyboard was up stays on screen. A scroll the user
 * made in the meantime is respected: the card only ever moves toward the pre-keyboard offset.
 * A viewport that grows without having shrunk first (first layout, a resize) moves nothing.
 *
 * Pure: the caller feeds every viewport height with the scroll offset at that moment and applies
 * the returned delta (≤ 0).
 */
internal class CardKeyboardScrollUndo {
    private var lastHeight = -1
    private var anchorHeight = -1
    private var anchorValue = 0

    /** The scroll delta (≤ 0) to apply now that the viewport is [height] px with the card at [value]. */
    fun onViewport(height: Int, value: Int): Int {
        val prev = lastHeight
        lastHeight = height
        if (prev < 0 || height == prev) return 0
        if (height < prev) {
            if (anchorHeight < 0) {
                anchorHeight = prev
                anchorValue = value
            }
            return 0
        }
        if (anchorHeight < 0) return 0
        val target = min(value, max(anchorValue, value - (height - prev)))
        if (height >= anchorHeight) anchorHeight = -1
        return target - value
    }
}
