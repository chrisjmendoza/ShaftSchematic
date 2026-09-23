// file: app/src/main/java/com/android/shaftschematic/ui/adaptive/WindowSize.kt
package com.android.shaftschematic.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The window's width class — the ONE axis every adaptive layout decision in the app keys on.
 * Thresholds are Material's: under 600 dp a phone (or a tablet in split screen), 600–839 dp a
 * small tablet or a large phone in landscape, 840 dp and up a tablet with room for two panes.
 *
 * Height is deliberately not a class: the layouts that care about it (the preview tuning
 * sheet's page strip) read the real height in dp at their own site.
 *
 * See `docs/contracts/Adaptive.md`.
 */
enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Material breakpoint: at or above this width the window is at least MEDIUM. */
const val WINDOW_WIDTH_MEDIUM_DP = 600

/** Material breakpoint: at or above this width the window is EXPANDED. */
const val WINDOW_WIDTH_EXPANDED_DP = 840

/** Pure classification of a window width in dp — unit-tested; the composable below is a reader. */
fun windowWidthClassFor(widthDp: Int): WindowWidthClass = when {
    widthDp >= WINDOW_WIDTH_EXPANDED_DP -> WindowWidthClass.EXPANDED
    widthDp >= WINDOW_WIDTH_MEDIUM_DP -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.COMPACT
}

/**
 * The current window's width class, recomposing on every configuration change (rotation,
 * split-screen resize, fold). Reads `LocalConfiguration`, which excludes the system bars, so a
 * window is classified by the width the content actually gets.
 */
@Composable
@ReadOnlyComposable
fun currentWindowWidthClass(): WindowWidthClass =
    windowWidthClassFor(LocalConfiguration.current.screenWidthDp)

/**
 * Whether a screen lays out as TWO PANES side by side (preview | controls, canvas | list).
 * EXPANDED only: a MEDIUM window split in two leaves each pane narrower than a phone, and the
 * carousel cards and the sheet controls are phone-width designs.
 */
val WindowWidthClass.twoPane: Boolean get() = this == WindowWidthClass.EXPANDED
