package com.android.shaftschematic.settings

import com.android.shaftschematic.util.DualUnitLayout
import com.android.shaftschematic.util.FractionStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Drawing line thickness (a multiplier on every stroke, preview and PDF alike). The settable
 * range and the fresh-install value; one source for the DataStore clamp, the ViewModel setter
 * and a [DrawingProfile]'s captured value.
 */
const val DRAWING_LINE_THICKNESS_MIN = 0.5f
const val DRAWING_LINE_THICKNESS_MAX = 2.0f
const val DRAWING_LINE_THICKNESS_DEFAULT = 1.0f

/** Longest a saved profile's name may be. The whole map rides one DataStore string. */
const val DRAWING_PROFILE_NAME_MAX_CHARS = 40

/**
 * How many profiles may be saved. A ceiling rather than a product limit: the map is one
 * preference value, and an unbounded list of them would grow the settings blob without bound.
 */
const val DRAWING_PROFILE_MAX_COUNT = 20

/** The fresh-install drawing look — what an untouched field in a saved profile falls back to. */
private val SHIPPED_PDF_PREFS = PdfPrefs()

/**
 * A named set of DRAWING-LOOK preferences: the [PdfPrefs] the composers style a sheet with,
 * plus the drawing line thickness.
 *
 * **App-wide by product decision.** A profile is a set of device preferences and nothing else —
 * no document field, no envelope key, and no memory in a drawing of which profile made it.
 * Applying one is a **one-shot copy** into the live preferences; there is no "active profile"
 * to drift out of sync with the settings the user then adjusts by hand.
 *
 * Deliberately EXCLUDED, because they are not the drawing's look:
 * - capability gates (per-component units, liner shoulders) — they decide which controls exist,
 *   and a profile that hid a control would read as the app losing a feature;
 * - the dual-units default — a starting value for new documents, i.e. document behaviour, and a
 *   document persists its own from then on;
 * - theme / high contrast / preview colors / undercut styles — app chrome and on-screen sheet
 *   styling, which never reach the printed page (Settings → Appearance owns them);
 * - the per-job `RunoutConfig` pair (Shaft height, liner compression) — a *fit* for one shaft's
 *   proportions, not a look, so it stays per-document;
 * - developer options.
 *
 * Every field is defaulted and enum-valued fields are stored by NAME, so a profile saved by an
 * older build decodes under a newer one (missing fields take the shipped value) and a name this
 * build does not know falls back rather than throwing.
 */
@Serializable
data class DrawingProfile(
    val tieringMode: String = SHIPPED_PDF_PREFS.tieringMode.name,
    val showComponentTitles: Boolean = SHIPPED_PDF_PREFS.showComponentTitles,
    val shadedBodies: Boolean = SHIPPED_PDF_PREFS.shadedBodies,
    val shadedTapers: Boolean = SHIPPED_PDF_PREFS.shadedTapers,
    val shadedLiners: Boolean = SHIPPED_PDF_PREFS.shadedLiners,
    val shadeExplicitBodiesOnly: Boolean = SHIPPED_PDF_PREFS.shadeExplicitBodiesOnly,
    val runoutBubbleScale: Float = SHIPPED_PDF_PREFS.runoutBubbleScale,
    val runoutBubbleDropScale: Float = SHIPPED_PDF_PREFS.runoutBubbleDropScale,
    val curveLoHeightIn: Float = SHIPPED_PDF_PREFS.curveLoHeightIn,
    val curveHiHeightIn: Float = SHIPPED_PDF_PREFS.curveHiHeightIn,
    val sBreakThresholdFrac: Float = SHIPPED_PDF_PREFS.sBreakThresholdFrac,
    val arrowSizePt: Float = SHIPPED_PDF_PREFS.arrowSizePt,
    val fractionStyle: String = SHIPPED_PDF_PREFS.fractionStyle.name,
    val dualUnitLayout: String = SHIPPED_PDF_PREFS.dualUnitLayout.name,
    val wearTraceDepthFrac: Float = SHIPPED_PDF_PREFS.wearTraceDepthFrac,
    val wearBandShadeFrac: Float = SHIPPED_PDF_PREFS.wearBandShadeFrac,
    val wearJoinGapMaxMm: Float = SHIPPED_PDF_PREFS.wearJoinGapMaxMm,
    val lineThicknessScale: Float = DRAWING_LINE_THICKNESS_DEFAULT,
) {
    /**
     * The profile's [PdfPrefs], already `clamped()` — a payload hand-edited or written by a build
     * with different bounds can carry an out-of-range number, and the clamp is the same one every
     * other write path goes through.
     */
    fun toPdfPrefs(): PdfPrefs = PdfPrefs(
        tieringMode = PdfTieringMode.fromName(tieringMode),
        showComponentTitles = showComponentTitles,
        shadedBodies = shadedBodies,
        shadedTapers = shadedTapers,
        shadedLiners = shadedLiners,
        shadeExplicitBodiesOnly = shadeExplicitBodiesOnly,
        runoutBubbleScale = runoutBubbleScale,
        runoutBubbleDropScale = runoutBubbleDropScale,
        curveLoHeightIn = curveLoHeightIn,
        curveHiHeightIn = curveHiHeightIn,
        sBreakThresholdFrac = sBreakThresholdFrac,
        arrowSizePt = arrowSizePt,
        fractionStyle = FractionStyle.fromName(fractionStyle),
        dualUnitLayout = DualUnitLayout.fromName(dualUnitLayout),
        wearTraceDepthFrac = wearTraceDepthFrac,
        wearBandShadeFrac = wearBandShadeFrac,
        wearJoinGapMaxMm = wearJoinGapMaxMm,
    ).clamped()

    /** Line thickness inside the settable range — [toPdfPrefs]'s clamp for the one field outside it. */
    val clampedLineThicknessScale: Float
        get() = lineThicknessScale.coerceIn(DRAWING_LINE_THICKNESS_MIN, DRAWING_LINE_THICKNESS_MAX)

    companion object {
        /** Captures the live drawing look. [prefs] is the app-wide mirror, not a per-sheet copy. */
        fun of(prefs: PdfPrefs, lineThicknessScale: Float): DrawingProfile = DrawingProfile(
            tieringMode = prefs.tieringMode.name,
            showComponentTitles = prefs.showComponentTitles,
            shadedBodies = prefs.shadedBodies,
            shadedTapers = prefs.shadedTapers,
            shadedLiners = prefs.shadedLiners,
            shadeExplicitBodiesOnly = prefs.shadeExplicitBodiesOnly,
            runoutBubbleScale = prefs.runoutBubbleScale,
            runoutBubbleDropScale = prefs.runoutBubbleDropScale,
            curveLoHeightIn = prefs.curveLoHeightIn,
            curveHiHeightIn = prefs.curveHiHeightIn,
            sBreakThresholdFrac = prefs.sBreakThresholdFrac,
            arrowSizePt = prefs.arrowSizePt,
            fractionStyle = prefs.fractionStyle.name,
            dualUnitLayout = prefs.dualUnitLayout.name,
            wearTraceDepthFrac = prefs.wearTraceDepthFrac,
            wearBandShadeFrac = prefs.wearBandShadeFrac,
            wearJoinGapMaxMm = prefs.wearJoinGapMaxMm,
            lineThicknessScale = lineThicknessScale
                .coerceIn(DRAWING_LINE_THICKNESS_MIN, DRAWING_LINE_THICKNESS_MAX),
        )
    }
}

private val drawingProfilesSerializer =
    MapSerializer(String.serializer(), DrawingProfile.serializer())

/**
 * `encodeDefaults` is load-bearing: a saved profile records what the user was looking at, so
 * every field is written even when it equals today's shipped value. Omitting them would let a
 * later change of a `PdfPrefs` default silently restyle profiles saved before it.
 */
private val drawingProfilesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** The whole name → profile map as the single JSON string the preference holds. */
fun encodeDrawingProfiles(profiles: Map<String, DrawingProfile>): String =
    drawingProfilesJson.encodeToString(drawingProfilesSerializer, profiles)

/**
 * Decodes the stored map. An absent, empty or unreadable value degrades to "no saved profiles" —
 * a corrupt string must cost the user their presets, never the Settings screen.
 */
fun decodeDrawingProfiles(raw: String?): Map<String, DrawingProfile> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        drawingProfilesJson.decodeFromString(drawingProfilesSerializer, raw)
    }.getOrDefault(emptyMap())
}

/**
 * The key a typed name is stored under: trimmed and length-capped, or null when nothing is left.
 * Two names that differ only by surrounding space are the same profile.
 */
fun normalizeDrawingProfileName(raw: String): String? =
    raw.trim().take(DRAWING_PROFILE_NAME_MAX_CHARS).trim().ifBlank { null }
