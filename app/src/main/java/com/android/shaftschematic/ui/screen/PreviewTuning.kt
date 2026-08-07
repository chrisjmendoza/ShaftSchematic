package com.android.shaftschematic.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.settings.RunoutConfig
import com.android.shaftschematic.util.PDF_PREVIEW_RENDER_SCALE

/**
 * PreviewTuning — the live, visual-only overrides behind an open PDF preview while a
 * tuning slider is being dragged.
 *
 * The four tuning sliders (Line thickness, Body S-break, Shaft height, Liner compression)
 * report their in-progress value through their `onDrag` channel; the hosting preview
 * screen parks it here and renders the page from it, so the drawing reshapes under the
 * finger instead of only on release ("see the differences without choosing, closing menu,
 * opening menu, choosing" — on-device request).
 *
 * **These overrides are drawing state, never storage.** No DataStore write and no
 * `RunoutConfig` update may happen on a drag frame: persistence and the per-job dirty mark
 * stay on the sliders' commit-on-release path, exactly as before. A field returns to null
 * when its drag ends, and the committed value takes over again.
 *
 * [active] is a `derivedStateOf` so the screens that read it (the raster resolution and the
 * bottom-sheet scrim) recompose when the drag STARTS and ENDS, not on every frame.
 */
class PreviewTuning {
    /** In-progress "Line thickness" multiplier, or null when no thickness drag is live. */
    var lineThickness by mutableStateOf<Float?>(null)

    /** In-progress "Body S-break" threshold fraction, or null. */
    var sBreakFrac by mutableStateOf<Float?>(null)

    /** In-progress "Shaft height" multiplier — undetented, see `ShaftHeightSlider`. */
    var heightScale by mutableStateOf<Float?>(null)

    /** In-progress raw "Liner compression" value (not the derived width floor). */
    var linerCompression by mutableStateOf<Float?>(null)

    private val activeState = derivedStateOf {
        lineThickness != null || sBreakFrac != null ||
            heightScale != null || linerCompression != null
    }

    /** True while any tuning slider is being dragged. */
    val active: Boolean get() = activeState.value
}

/** Screen-scoped [PreviewTuning] — one holder per preview surface. */
@Composable
internal fun rememberPreviewTuning(): PreviewTuning = remember { PreviewTuning() }

/**
 * [prefs] carrying the [sBreakThresholdFrac] this render pass should draw with — the
 * committed pref normally, the in-progress value during a "Body S-break" drag. The
 * composers read the threshold off `PdfPrefs`, so the drag value rides in on a copy and
 * the stored pref is untouched.
 */
internal fun tunedPdfPrefs(prefs: PdfPrefs, sBreakThresholdFrac: Float): PdfPrefs =
    if (sBreakThresholdFrac == prefs.sBreakThresholdFrac) prefs
    else prefs.copy(sBreakThresholdFrac = sBreakThresholdFrac)

/**
 * [config] with live "Shaft height" / "Liner compression" drags applied. The liner floor
 * the composers consume is derived by `RunoutConfig.linerMinFracOfTrue` off this copy —
 * never a re-stated formula — so the drag draws exactly what the release will commit.
 */
internal fun tunedRunoutConfig(
    config: RunoutConfig,
    heightScale: Float?,
    linerCompression: Float?,
): RunoutConfig =
    if (heightScale == null && linerCompression == null) {
        config
    } else {
        config.copy(
            heightScale = heightScale ?: config.heightScale,
            linerCompression = linerCompression ?: config.linerCompression,
        )
    }

/**
 * Raster resolution for one preview pass: half res (≈¼ the pixels) while a slider drag is
 * live so the page keeps up with the finger, full [PDF_PREVIEW_RENDER_SCALE] otherwise —
 * the release pass always restores the sharp page.
 */
internal fun previewRenderScale(tuningActive: Boolean): Int =
    if (tuningActive) 1 else PDF_PREVIEW_RENDER_SCALE
