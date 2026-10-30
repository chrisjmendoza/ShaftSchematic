package com.android.shaftschematic.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.android.shaftschematic.geom.END_EPS_MM
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.BoltHoleClocking
import com.android.shaftschematic.model.BoltHoleStyle
import com.android.shaftschematic.model.CouplerBoltSlot
import com.android.shaftschematic.model.KeywayClocking
import com.android.shaftschematic.model.SlotAuthoredReference
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.model.hasKeyway
import com.android.shaftschematic.model.keywayAbsSpanMm
import com.android.shaftschematic.model.keywayClocking
import com.android.shaftschematic.model.keywayCount
import com.android.shaftschematic.model.shoulderOn
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.autoTaperRateText
import com.android.shaftschematic.util.buildBodyTitleById
import com.android.shaftschematic.util.buildLinerTitleById
import com.android.shaftschematic.util.drawRichText
import com.android.shaftschematic.util.wrapRichLines
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────────
// The footer subsystem — drawn by both the schematic sheet and the consolidated
// runout/wear sheet: `RunoutPdfComposer` calls `drawFooter`/`FooterConfig`/
// `selectFooterTapers`/`hasAftThread`/`hasFwdThread` directly, so the two documents
// can never print a different spec block for the same shaft.
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Hard floor on that air. Only reachable by a shaft too tall for the budget to have honoured
 * [INFO_GAP_PT]; it keeps the block off the drawing and still leaves clearance for
 * [FOOTER_GROWTH_MAX_PT] of upward growth.
 */
private const val INFO_GAP_MIN_PT = 56f

/**
 * Top of the footer band. The block **sits on the bottom margin** and grows upward from it
 * ([drawFooter] lays its rows out from `rect.bottom`), so a page's slack is the air between the
 * drawing and the footer rather than a dead strip below it (on-device direction: "bring the
 * footer down a little bit and make better use of the white space"). A block that needs more
 * room — dual-unit lines, wrapped columns — takes it from that air and moves closer to the
 * shaft; the page's bottom edge never moves. The runout/consolidated sheet has always placed
 * its footer this way, so this is also what makes the two documents agree.
 *
 * [shaftBottomY] only binds in the degenerate case: the shaft placement already reserves
 * [INFO_GAP_PT] measured up from the margin, so the floor here is reachable only when the shaft
 * was too tall for that reservation to hold. [INFO_GAP_MIN_PT] stays clear of
 * [FOOTER_GROWTH_MAX_PT] so even a fully-grown band cannot climb into the drawing.
 */
internal fun footerBandTop(
    pageH: Float,
    marginPt: Float,
    footerBlockPt: Float,
    shaftBottomY: Float,
): Float = max(pageH - marginPt - footerBlockPt, shaftBottomY + INFO_GAP_MIN_PT)
internal const val FOOTER_BLOCK_PT = 96f
// Blank drafts reserve a taller footer band: line spacing opens up for handwriting.
// Sized for the fullest column (taper header + Rate/L.E.T./S.E.T./Length/KW + captured inset
// + Thread = 8 lines) at the blank pitch; drawFooter additionally fit-clamps its pitch to
// the band, so an overloaded column tightens up instead of running off the page.
internal const val FOOTER_BLOCK_BLANK_PT = 200f
/** Air the band keeps around its rows — the plan's height and the drawn pitch both count it. */
private const val FOOTER_BAND_PAD_PT = 10f
private const val FOOTER_LINE_FACTOR = 1.35f
/**
 * Tightest footer line pitch. The fit-clamp may squeeze toward it when wrapped lines make a column
 * tall, but never past it — below this the lines touch and the block stops being readable.
 */
private const val FOOTER_LINE_FACTOR_MIN = 1.12f
/**
 * How far the footer band may grow UPWARD to fit wrapped content, in points.
 *
 * A safety net for a caller that hands [drawFooter] a fixed rect. Both composers now reserve
 * [footerBlockHeightPt], the measured height, so on their sheets this never fires — growth into
 * the info gap is where the component-name rows and the Ø callouts live, and a fully loaded
 * footer climbed into them (`SheetTextOverlapTest`). Comfortably inside `INFO_GAP_PT` still, so a
 * footer that does grow can never reach the drawing itself.
 */
private const val FOOTER_GROWTH_MAX_PT = 48f
// Handwriting pitch: ~2.2 lines of the footer text size (≈ 26 pt on the schematic, ≈ 22 pt
// on the consolidated sheet) — a printed-density 1.35 factor leaves no room to write a value
// between the rules (on-device report).
private const val FOOTER_LINE_FACTOR_BLANK = 2.2f
/**
 * Tightest handwriting pitch a blank draft's footer gives up room down to when the drawing above
 * needs it ([footerBlockMinHeightPt]) — ≈ 17 pt on the consolidated sheet, still writable.
 */
private const val FOOTER_LINE_FACTOR_BLANK_MIN = 1.7f

// The "body-only" and "single-taper-only" shaft classifiers lived here to decide whether the
// footer's compression note was worth printing. The note is gone (the S-break says it), and
// nothing else on the sheet branches on shaft shape — every component draws from its own pass.

internal data class FooterTapers(
    val aft: Taper?,
    val fwd: Taper?
)

internal fun selectFooterTapers(spec: ShaftSpec): FooterTapers {
    val oal = spec.overallLengthMm
    val tapers = spec.tapers
        .asSequence()
        .filter { it.lengthMm > 0f && it.startDiaMm > 0f && it.endDiaMm > 0f }
        .toList()
    if (tapers.isEmpty()) return FooterTapers(aft = null, fwd = null)

    fun midMm(t: Taper): Float = t.startFromAftMm + t.lengthMm * 0.5f

    if (tapers.size == 1) {
        val t = tapers.first()
        // Default to AFT if we can't meaningfully classify.
        if (oal <= 0f) return FooterTapers(aft = t, fwd = null)
        return if (midMm(t) <= oal * 0.5f) FooterTapers(aft = t, fwd = null) else FooterTapers(aft = null, fwd = t)
    }

    val sorted = tapers.sortedBy(::midMm)
    return FooterTapers(aft = sorted.first(), fwd = sorted.last())
}

/**
 * True when [t] is the shaft's END feature on that side — it starts at the AFT end (x = 0, or the
 * AFT end thread's shoulder) for [aftSide], or ends at the FWD end (x = OAL, or the FWD end
 * thread's shoulder) otherwise, so nothing but a thread (and reference features — bolt slots and
 * the like) lies beyond it. Only such a taper may be headed "AFT Taper"/"FWD Taper": the column a
 * taper lands in ([selectFooterTapers]) says nothing about where the shaft ends, and a step-down
 * taper mid-shaft headed "FWD Taper" told the machinist the coupling end was a taper (on-device report).
 */
internal fun isFooterEndTaper(spec: ShaftSpec, t: Taper, aftSide: Boolean): Boolean =
    (if (aftSide) getAftEndTaper(spec) else getFwdEndTaper(spec)) === t

/**
 * The heading of a footer taper block. A taper the user NAMED prints that name (on-device request:
 * a rename has to reach the printed page); otherwise the shaft's end taper on that side reads
 * "AFT Taper"/"FWD Taper" ([isFooterEndTaper]) and any other taper plain "Taper".
 */
internal fun footerTaperHeader(spec: ShaftSpec, t: Taper, aftSide: Boolean): String =
    t.label?.trim()?.takeIf { it.isNotEmpty() }
        ?: when {
            isFooterEndTaper(spec, t, aftSide) -> if (aftSide) FOOTER_AFT_TAPER_HEADER else FOOTER_FWD_TAPER_HEADER
            else -> FOOTER_MID_TAPER_HEADER
        }

// ──────────────────────────────────────────────────────────────────────────────
// Footer (3 columns; center column is work-order info)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The middle (work-order info) column's lines, in print order.
 *
 * Built as a list so the line count is known BEFORE [drawFooter] picks its pitch — the end
 * columns already arrive as lists. A blank draft's lines end in `:` (or a bare `Ø`), which is
 * what tells [drawFooter] to draw a writing rule after the label instead of a value.
 *
 * **Item is optional.** A blank draft always rules a line for it, but a printed sheet omits the
 * line entirely when it is blank — a label with nothing after it is an orphan on a finished
 * drawing. Every other field here is unconditional.
 *
 * **Drawing is optional the same way**, keyed off [ProjectInfo.drawingLabel] instead of a
 * document field: blank prints nothing on either branch (an original-drawing sheet stays
 * byte-identical), a blank draft rules a line for it only when the label is set (a blank draft
 * of the original drawing must not grow a line it will never carry a value for).
 */
internal fun buildFooterMidColumn(
    spec: ShaftSpec,
    project: ProjectInfo,
    cfg: FooterConfig,
    date: String,
    blankValues: Boolean = false,
): List<String> = if (blankValues) {
    buildList {
        add("Customer:"); add("Vessel:"); add("Job #:"); add("Item:")
        if (project.drawingLabel.isNotBlank()) add("Drawing:")
        add("Date:")
        keywayClockingFooterNote(spec)?.let { add(it) }
        add("Side:")
    }
} else {
    buildList {
        add("Customer: ${project.customer}")
        add("Vessel: ${project.vessel}")
        add("Job #: ${project.jobNumber}")
        if (project.item.isNotBlank()) add("Item: ${project.item}")
        if (project.drawingLabel.isNotBlank()) add("Drawing: ${project.drawingLabel}")
        add("Date: $date")
        keywayClockingFooterNote(spec)?.let { add(it) }
    }
}

/**
 * Everything [drawFooter] decides BEFORE it inks: the three columns' lines (already built for
 * the mode and units), where each column starts and how wide it may print, and how tall the
 * band has to be to print every wrapped row at the printed pitch. One plan, two consumers:
 * `drawFooter` lays out from it, and a composer that reserves its own footer band asks it for
 * [neededHeightPt] first — so the band a sheet reserves and the band the footer draws are the
 * same number. The consolidated sheet reserving a fixed block while the footer grew upward past
 * it put the "TIR's taken looking:" line through the "AFT Taper" heading (on-device report).
 */
internal class FooterPlan(
    val cols: FooterColumns,
    val midLines: List<String>,
    val midLeadLines: Int,
    val leftX: Float,
    val midX: Float,
    val rightX: Float,
    val leftMaxW: Float,
    val midMaxW: Float,
    val rightMaxW: Float,
    val wrappedMaxLines: Int,
    /** Band height that prints every wrapped row at the printed pitch, pad included. */
    val neededHeightPt: Float,
)

/** Today's footer date, the one string both the plan and the draw must agree on. */
private fun footerDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

internal fun planFooter(
    rectLeft: Float,
    rectRight: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    text: Paint,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
    date: String = footerDate(),
): FooterPlan {
    val cols = buildFooterEndColumns(spec, unit, cfg, blankValues, displayUnits)

    // The end columns lead with a taper heading; the middle job-info block has no heading of its
    // own, so its writing rules would sit one line proud of the end columns' rules. On a blank
    // draft (every line a rule) that misalignment is what the eye reads first — drop the middle
    // column one line so all three columns share the same set of baselines. Printed footers carry
    // values, not rules, and their reserved band has no room to spare, so they stay flush.
    val midLeadLines = if (blankValues && (cols.aftLeadsWithHeading || cols.fwdLeadsWithHeading)) 1 else 0

    // Column starts. A printed footer weights the band toward the left and middle columns,
    // where the long free text lives (taper specs, customer and vessel names) — the FWD column
    // holds the same short spec lines as AFT and needs no more. A blank draft prints no values
    // at all: every line is a writing rule that runs to its column edge, so an uneven split is
    // read as uneven writing room and the FWD column comes out visibly short (on-device report).
    // Blank drafts therefore split the band into equal thirds.
    val width = rectRight - rectLeft
    val midFrac   = if (blankValues) 1f / 3f else 0.40f
    val rightFrac = if (blankValues) 2f / 3f else 0.76f
    val leftX  = rectLeft
    val midX   = rectLeft + width * midFrac
    val rightX = rectLeft + width * rightFrac

    // Column budgets: long free text (customer/vessel names) must never overrun the
    // neighbouring column.
    val colPad = 6f
    val leftMaxW  = midX - leftX - colPad
    val midMaxW   = rightX - midX - colPad
    // The last column has no neighbour to clear, so printed text may run to the band edge; a
    // blank draft pads it like the others so all three rules come out the same length.
    val rightMaxW = rectRight - rightX - (if (blankValues) colPad else 0f)

    val midLines = buildFooterMidColumn(spec, project, cfg, date, blankValues)

    // WRAPPING, not ellipsizing. A dual-unit spec line is roughly twice as wide as a single-unit
    // one, and the old `…` truncation dropped the very figure the sheet exists to carry
    // (on-device sheet, `docs/DualUnitStacking_PLAN.md` §1d). Wrapped rows cost line count, which
    // the pitch absorbs — and the band grows a little if it must.
    fun wrapCount(lines: List<String>, maxW: Float): Int =
        lines.sumOf { wrapRichLines(it, text, maxW, rich = true).size }
    val wrappedMaxLines = maxOf(
        wrapCount(cols.aftLines, leftMaxW),
        wrapCount(cols.fwdLines, rightMaxW),
        midLeadLines + wrapCount(midLines, midMaxW) + (if (blankValues) 0 else 1),  // +1: Side badge
        1,
    )
    val printedPitch = text.textSize * FOOTER_LINE_FACTOR
    return FooterPlan(
        cols = cols, midLines = midLines, midLeadLines = midLeadLines,
        leftX = leftX, midX = midX, rightX = rightX,
        leftMaxW = leftMaxW, midMaxW = midMaxW, rightMaxW = rightMaxW,
        wrappedMaxLines = wrappedMaxLines,
        neededHeightPt = wrappedMaxLines * printedPitch + FOOTER_BAND_PAD_PT,
    )
}

/**
 * The footer band a composer must RESERVE for this sheet: never less than its mode's default
 * block, and as much more as the wrapped columns need at the printed pitch. A composer that
 * stacks other content directly above its footer (the consolidated sheet's TIR line and coupling
 * face) reserves this, so the block never has to grow upward into that content.
 */
internal fun footerBlockHeightPt(
    rectLeft: Float,
    rectRight: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    text: Paint,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): Float {
    val base = if (blankValues) FOOTER_BLOCK_BLANK_PT else FOOTER_BLOCK_PT
    val plan = planFooter(rectLeft, rectRight, spec, unit, project, text, cfg, blankValues, displayUnits)
    return max(base, plan.neededHeightPt)
}

/**
 * The tightest a BLANK draft's footer band may be squeezed: every wrapped row at
 * [FOOTER_LINE_FACTOR_BLANK_MIN] — still a hand's width between the rules. A composer whose page
 * cannot afford its drawing at the height floor reserves less than [footerBlockHeightPt], down to
 * this, and [drawFooter] fit-clamps its pitch to the band it is handed. A printed footer has no
 * give: this returns its full reservation.
 */
internal fun footerBlockMinHeightPt(
    rectLeft: Float,
    rectRight: Float,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    text: Paint,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): Float {
    val full = footerBlockHeightPt(rectLeft, rectRight, spec, unit, project, text, cfg, blankValues, displayUnits)
    if (!blankValues) return full
    val plan = planFooter(rectLeft, rectRight, spec, unit, project, text, cfg, blankValues, displayUnits)
    val tight = plan.wrappedMaxLines * text.textSize * FOOTER_LINE_FACTOR_BLANK_MIN + FOOTER_BAND_PAD_PT
    return min(full, max(tight, plan.neededHeightPt))
}

// Internal (not private): the consolidated runout sheet prints the SAME footer block —
// one footer implementation for both documents, so the spec lines can never drift apart.
internal fun drawFooter(
    c: Canvas,
    rect: RectF,
    spec: ShaftSpec,
    unit: UnitSystem,
    project: ProjectInfo,
    filename: String,
    appVersion: String,
    text: Paint,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
) {
    val plan = planFooter(rect.left, rect.right, spec, unit, project, text, cfg, blankValues, displayUnits)
    val cols = plan.cols
    val midLines = plan.midLines
    val midLeadLines = plan.midLeadLines
    val leftX = plan.leftX
    val midX = plan.midX
    val rightX = plan.rightX
    val leftMaxW = plan.leftMaxW
    val midMaxW = plan.midMaxW
    val rightMaxW = plan.rightMaxW
    val wrappedMaxLines = plan.wrappedMaxLines

    // The band grows UPWARD into the info gap when the wrapped content cannot fit it at the
    // printed pitch — never past [FOOTER_GROWTH_MAX_PT], which is well inside `INFO_GAP_PT`, so
    // the footer can never climb into the drawing. A composer that reserved
    // [footerBlockHeightPt] hands a rect the plan already fits, and no growth happens.
    val neededH = plan.neededHeightPt
    val bandH = maxOf(rect.height(), minOf(neededH, rect.height() + FOOTER_GROWTH_MAX_PT))
    val top = rect.bottom - bandH + 6f

    // Blank drafts open the line pitch up for handwriting; both modes then fit-clamp to the band
    // so the fullest column tightens instead of running off the page.
    val lh = min(
        text.textSize * (if (blankValues) FOOTER_LINE_FACTOR_BLANK else FOOTER_LINE_FACTOR),
        (bandH - FOOTER_BAND_PAD_PT) / wrappedMaxLines,
    ).coerceAtLeast(text.textSize * FOOTER_LINE_FACTOR_MIN)

    // Blank drafts: any line that ends with ":" (or a bare "Ø") is a label whose value gets
    // hand-written — draw a writing rule after it instead of a printed value. The rule runs
    // to the COLUMN edge, not a fixed width: a ~1" rule is too short to hand-write a customer
    // name or a diameter on a clipboard (on-device report).
    //
    // Returns the y AFTER this line, one pitch per WRAPPED row.
    fun drawFooterLine(line: String, x: Float, y: Float, maxW: Float): Float {
        if (blankValues && (line.endsWith(":") || line.endsWith("Ø"))) {
            drawLabelWithRule(c, line, x, y, text, ruleWidth = maxW, maxRight = x + maxW)
            return y + lh
        }
        // Rich: footer spec lines carry the shop fractions ("Length: 12 5/8\"",
        // "KW: 1/4 × 1/8 × 2\"") and must set them the way the rails do.
        var yy = y
        wrapRichLines(line, text, maxW, rich = true).forEach { row ->
            c.drawRichText(row, x, yy, text)
            yy += lh
        }
        return yy
    }

    // AFT (left) — left-aligned at left margin
    run {
        var y = top
        cols.aftLines.forEach { line -> y = drawFooterLine(line, leftX, y, leftMaxW) }
    }

    // Middle (Work order) — left-aligned at 1/3 mark, one line down on a blank draft so its
    // rules line up with the end columns' (see midLeadLines).
    run {
        var y = top + midLeadLines * lh
        // One list, one draw loop, for both modes — the same lines the pitch was measured from
        // above, so what was counted is exactly what prints. Free-text job fields (customer,
        // vessel, job #) WRAP like every other footer line rather than losing their tail.
        midLines.forEach { line -> y = drawFooterLine(line, midX, y, midMaxW) }

        // The Side badge sits below the job block, set larger — a blank draft writes it in on a
        // rule instead, which `midLines` already carries as its own label line. A non-blank
        // drawingLabel is a sibling on the SAME baseline (never its own row — wrappedMaxLines'
        // Side-badge reservation must still count for exactly one line): to the right of the
        // Side badge separated by two spaces, or alone at midX when there is no Side badge.
        if (!blankValues) {
            val sidePos = project.side.printableLabelOrNull()
            val label = project.drawingLabel.takeIf { it.isNotBlank() }?.uppercase()
            if (sidePos != null || label != null) {
                y += lh * 0.35f
                val posPaint = Paint(text).apply {
                    textSize = text.textSize * 1.20f
                    isFakeBoldText = true
                }
                val badgeText = when {
                    sidePos != null && label != null -> "$sidePos  $label"
                    sidePos != null -> sidePos
                    else -> label!!
                }
                c.drawText(badgeText, midX, y, posPaint)
            }
        }
    }

    // FWD (right) — left-aligned at 2/3 mark
    run {
        var y = top
        cols.fwdLines.forEach { line -> y = drawFooterLine(line, rightX, y, rightMaxW) }
    }
}

/**
 * The single keyway-clocking note printed in the footer's middle column, or null when none
 * applies. Only meaningful with ≥ 2 keyways on the shaft — "apart from each other" says nothing
 * about a lone keyway.
 *
 * At most one note ever prints: [keywayClocking] resolves the mutually-exclusive flags into one
 * mode. Shared by the printed and blank-draft footer branches so they can't drift apart.
 */
internal fun keywayClockingFooterNote(spec: ShaftSpec): String? {
    if (spec.keywayCount() < 2) return null
    return when (spec.keywayClocking()) {
        KeywayClocking.DEG_180    -> "Keyways 180° apart"
        KeywayClocking.DEG_90_CW  -> "Keyways 90° apart (CW from aft)"
        KeywayClocking.DEG_90_CCW -> "Keyways 90° apart (CCW from aft)"
        KeywayClocking.NONE       -> null
    }
}

internal data class FooterColumns(
    val aftLines: List<String>,
    val fwdLines: List<String>,
    /** The AFT column opens with a taper block's heading line (a label, not a writing rule). */
    val aftLeadsWithHeading: Boolean = false,
    /** The FWD column opens with a taper block's heading line. */
    val fwdLeadsWithHeading: Boolean = false,
)

/** Clocking note under a cross-drilled hole's lines; null when the shaft has no keyway to clock against. */
internal const val BOLT_HOLE_90_NOTE = "90° from keyway"
internal const val BOLT_HOLE_IN_LINE_NOTE = "In line with keyway"

/**
 * Footer lines for ONE cross-drilled coupler bolt row: "Bolt hole:" (Ø, × count above one — only
 * while [showDia], `PdfPrefs.footerBoltHoleDia`), "Hole center from FWD/AFT:" (the authored
 * distance, plus pitch above one), and the clocking note — printed only when the shaft carries a
 * keyway, since "from keyway" says nothing without one (the keyway-clocking-note rule). [line] is
 * the caller's label/value joiner so blank drafts keep the labels and rule the values, like every
 * other spec line.
 */
internal fun crossDrilledHoleLines(
    slot: CouplerBoltSlot,
    spec: ShaftSpec,
    displayUnits: DisplayUnits,
    line: (String, () -> String) -> String,
    showDia: Boolean = true,
): List<String> = buildList {
    val unit = displayUnits.unitFor(slot.id)
    val dual = displayUnits.dual
    val face = if (slot.authoredReference == SlotAuthoredReference.FWD) "FWD" else "AFT"
    if (showDia) {
        add(line("Bolt hole:") {
            val count = if (slot.count > 1) " × ${slot.count}" else ""
            "Ø ${formatDiaWithUnitDual(slot.holeDiaMm.toDouble(), unit, dual)}$count"
        })
    }
    add(line("Hole center from $face:") {
        val pitch = if (slot.count > 1) " @ ${formatLenWithUnitDual(slot.spacingMm.toDouble(), unit, dual)}" else ""
        "${formatLenWithUnitDual(slot.authoredCenterMm(spec.overallLengthMm).toDouble(), unit, dual)}$pitch"
    })
    if (spec.keywayCount() >= 1) {
        add(if (slot.clocking == BoltHoleClocking.DEG_90) BOLT_HOLE_90_NOTE else BOLT_HOLE_IN_LINE_NOTE)
    }
}

// A spooned keyway prints NOTHING on the footer — no "(spooned)" and no note: the bowl on the
// drawing shows it (on-device ruling). The KW line reads the same spooned or not.

/**
 * Label of the line printed directly under a captured keyway's footer spec line (inset > 0):
 * "Captured KW: inset <len> from S.E.T." on a taper, "... from AFT|FWD" on a body, while
 * `PdfPrefs.footerCapturedKwInset` is on. The inset is a machining value the drawing only shows to
 * scale; an open keyway (inset 0) prints no such line.
 */
internal const val CAPTURED_KW_PREFIX = "Captured KW:"

// The footer carries NO compression note. The S-break pair IS the drawing's statement that a
// body run is foreshortened — a line of prose repeating it is redundant (on-device direction),
// and it cost a footer row on exactly the long shafts with the least room to spare.

// Column headings of the footer's end columns. Named because [drawFooter] tests whether a column
// leads with one to decide the middle column's first baseline — a literal there would drift.
internal const val FOOTER_AFT_TAPER_HEADER = "AFT Taper"
internal const val FOOTER_FWD_TAPER_HEADER = "FWD Taper"
/** Heading of an unnamed mid-shaft taper's block — it is neither end's taper. */
internal const val FOOTER_MID_TAPER_HEADER = "Taper"
/**
 * An empty footer line: one blank row in BOTH the measurement and the draw (an empty string wraps
 * to one empty row), and never a write-in rule on a blank draft (it carries no label).
 */
internal const val FOOTER_GAP_LINE = ""

/**
 * Builds the exact left/right footer text lines that [drawFooter] will render.
 * Exposed for JVM unit tests so we can validate end-feature detection without
 * depending on Android Canvas/PdfDocument runtime.
 */
internal fun buildFooterEndColumns(
    spec: ShaftSpec,
    unit: UnitSystem,
    cfg: FooterConfig,
    blankValues: Boolean = false,
    displayUnits: DisplayUnits = DisplayUnits.single(unit),
): FooterColumns {
    val ends = detectEndFeatures(spec)
    val taperSides = selectFooterTapers(spec)
    val dual = displayUnits.dual

    // Blank drafts keep every LABEL (so the writer knows what goes where) and drop the value;
    // drawFooter turns the trailing ":" into a writing rule.
    fun line(label: String, value: () -> String): String =
        if (blankValues) label else "$label ${value()}"

    fun taperLines(tp: Taper): List<String> = buildList {
        val ls = letSet(tp)
        val tpUnit = displayUnits.unitFor(tp.id)
        // Rate notation follows the taper's OWN unit like the L.E.T./S.E.T./Length lines
        // beside it — a metric-overridden taper keeps the ratio form ("/ft would clash with
        // mm dimensions"), instead of borrowing the document unit's convention.
        add(line("Rate:") { printedTaperRate(tp.taperRateText.trim().ifEmpty { rate1toN(tp) }, tpUnit) })
        // No face suffix on L.E.T./S.E.T. — the footer column already says which end of the
        // shaft this taper is, and "(FWD)" beside an AFT taper's L.E.T. read as if it were
        // asking about the FWD taper (on-device report).
        add(line("L.E.T.:") { formatDiaWithUnitDual(ls.let.toDouble(), tpUnit, dual) })
        add(line("S.E.T.:") { formatDiaWithUnitDual(ls.set.toDouble(), tpUnit, dual) })
        add(line("Length:") { formatLenWithUnitDual(tp.lengthMm.toDouble(), tpUnit, dual) })
        if (tp.keywayWidthMm > 0f && tp.keywayDepthMm > 0f) {
            // The keyway resolves its OWN unit, falling back to the taper's: a metric keyway on an
            // imperial taper is the common European case, and the rest of this column stays inches.
            val kwUnit = displayUnits.keywayUnitFor(tp.id)
            add(line("KW:") {
                if (tp.keywayLengthMm > 0f) {
                    "${formatLenWithUnitDual(tp.keywayWidthMm.toDouble(), kwUnit, dual)} × ${formatLenWithUnitDual(tp.keywayDepthMm.toDouble(), kwUnit, dual)} × ${formatLenWithUnitDual(tp.keywayLengthMm.toDouble(), kwUnit, dual)}"
                } else {
                    "${formatLenWithUnitDual(tp.keywayWidthMm.toDouble(), kwUnit, dual)} × ${formatLenWithUnitDual(tp.keywayDepthMm.toDouble(), kwUnit, dual)}"
                }
            })
            if (cfg.capturedKwInset && tp.keywayOffsetFromSetMm > 0f) {
                add(line(CAPTURED_KW_PREFIX) {
                    "inset ${formatLenWithUnitDual(tp.keywayOffsetFromSetMm.toDouble(), kwUnit, dual)} from S.E.T."
                })
            }
        }
    }

    // Thread spec line: a metric-designated thread (`Threads.metricDesignation`) prints its
    // designation verbatim in place of the dia × pitch term — a metric thread names both in
    // one token, and re-deriving them from majorDiaMm/pitchMm would be redundant and could
    // drift from the authored designation (golden rule: authored text is never re-derived).
    // The length term still resolves the thread's own display unit (its id already carries
    // an mm override when metric — see DisplayUnits/ShaftViewModel). Non-metric threads keep
    // today's dia × TPI-or-mm-pitch × length line, unchanged apart from dual-awareness. The
    // major Ø drops trailing zeros (`4.5"`, not `4.500"`) — the one exception to the
    // three-decimal diameter rule, see [formatThreadDiaWithUnit]. A thread relief prints NO footer
    // line — the groove is drawing detail only (on-device ruling).
    fun threadLines(th: Threads): List<String> = buildList {
        val thUnit = displayUnits.unitFor(th.id)
        add(line("Thread:") {
            val designation = th.metricDesignation
            if (designation != null) {
                "$designation × ${formatLenWithUnitDual(th.lengthMm.toDouble(), thUnit, dual)}"
            } else {
                "${formatThreadDiaWithUnitDual(th.majorDiaMm.toDouble(), thUnit, dual)} × ${fmtPitch(th.pitchMm, thUnit)} × ${formatLenWithUnitDual(th.lengthMm.toDouble(), thUnit, dual)}"
            }
        })
    }

    val aft = mutableListOf<String>()
    var aftTaperBlock = false
    if (cfg.showAftTaper) {
        taperSides.aft?.let { tp ->
            aft += footerTaperHeader(spec, tp, aftSide = true)
            aft += taperLines(tp)
            aftTaperBlock = true
        }
    }
    if (cfg.showAftThread && ends.aftThread) {
        getAftEndThread(spec)?.let { th -> aft += threadLines(th) }
    }

    val fwd = mutableListOf<String>()
    var fwdTaperBlock = false
    if (cfg.showFwdTaper) {
        taperSides.fwd?.let { tp ->
            fwd += footerTaperHeader(spec, tp, aftSide = false)
            fwd += taperLines(tp)
            fwdTaperBlock = true
        }
    }
    if (cfg.showFwdThread && ends.fwdThread) {
        getFwdEndThread(spec)?.let { th -> fwd += threadLines(th) }
    }

    // Cross-drilled coupler bolt holes — the plain coupling end's spec: hole Ø, the distance
    // from the authored face to the hole center, and its clocking against the keyway. Listed
    // in the column of the face it was quoted from. Seam rows print nothing, as before.
    spec.couplerBoltSlots.filter { it.holeStyle == BoltHoleStyle.CROSS }.forEach { slot ->
        val col = if (slot.authoredReference == SlotAuthoredReference.FWD) fwd else aft
        col += crossDrilledHoleLines(slot, spec, displayUnits, ::line, showDia = cfg.boltHoleDia)
    }

    // Body-hosted keyways (fitted couplings on intermediate shafts): list in the column
    // matching the keyway's physical half of the shaft. Below a taper block the line names its
    // body ("Body #1 KW:" — the drawing's title for it), or it reads as that taper's keyway
    // (on-device report: a FWD body's keyway printed under a mid-shaft taper's block).
    val bodyTitles = buildBodyTitleById(spec)
    fun bodyKwLine(b: Body, underTaper: Boolean): String {
        val kwUnit = displayUnits.keywayUnitFor(b.id)
        val label = if (underTaper) "${bodyTitles[b.id] ?: "Body"} KW:" else "Body KW:"
        return line(label) {
            "${formatLenWithUnitDual(b.keywayWidthMm.toDouble(), kwUnit, dual)} × " +
                "${formatLenWithUnitDual(b.keywayDepthMm.toDouble(), kwUnit, dual)} × " +
                formatLenWithUnitDual(b.keywayLengthMm.toDouble(), kwUnit, dual)
        }
    }
    // A blank row sets the body keyways apart from a taper block in the same column: the
    // body's keyway is not part of the taper's spec, and printed straight under "Length:" it
    // read as one (on-device request). One gap per column, before its first body keyway.
    var aftKwGap = false
    var fwdKwGap = false
    spec.bodies.filter { it.hasKeyway }.forEach { b ->
        val span = b.keywayAbsSpanMm() ?: return@forEach
        val centerMm = span.centerMm
        val aftHalf = centerMm <= spec.overallLengthMm * 0.5f
        val col = if (aftHalf) aft else fwd
        val underTaper = if (aftHalf) aftTaperBlock else fwdTaperBlock
        if (underTaper && !(if (aftHalf) aftKwGap else fwdKwGap)) {
            col += FOOTER_GAP_LINE
            if (aftHalf) aftKwGap = true else fwdKwGap = true
        }
        col += bodyKwLine(b, underTaper = underTaper)
        if (cfg.capturedKwInset && b.keywayOffsetFromEndMm > 0f) {
            val kwUnit = displayUnits.keywayUnitFor(b.id)
            val face = if (b.keywayEnd == LinerAuthoredReference.FWD) "FWD" else "AFT"
            col += line(CAPTURED_KW_PREFIX) {
                "inset ${formatLenWithUnitDual(b.keywayOffsetFromEndMm.toDouble(), kwUnit, dual)} from $face"
            }
        }
    }

    // Liner shoulder edge radii — the radius's ONLY printed value (the drawing shows the
    // step and fillet curve; no leader, by decision). ONE line per liner: a shoulder at one
    // end prints its radius alone (column of that end's physical half, as before); shoulders
    // at both ends share the line — one value when the radii agree, `AFT r, FWD r` when they
    // differ (on-device report: two identical "Liner shoulder R:" lines with nothing telling
    // them apart). A sharp corner (radius 0) prints nothing. With more than one shouldered
    // liner the line carries the liner's name, the carousel's title for it.
    val shoulderedLiners = spec.liners.filter { ln ->
        (ln.shoulderOn(LinerAuthoredReference.AFT)?.radiusMm ?: 0f) > 0f ||
            (ln.shoulderOn(LinerAuthoredReference.FWD)?.radiusMm ?: 0f) > 0f
    }
    val linerTitles = if (shoulderedLiners.size > 1) buildLinerTitleById(spec) else emptyMap()
    shoulderedLiners.forEach { ln ->
        val lnUnit = displayUnits.unitFor(ln.id)
        fun fmt(radiusMm: Float) = formatLenWithUnitDual(radiusMm.toDouble(), lnUnit, dual)
        val aftR = ln.shoulderOn(LinerAuthoredReference.AFT)?.radiusMm?.takeIf { it > 0f }
        val fwdR = ln.shoulderOn(LinerAuthoredReference.FWD)?.radiusMm?.takeIf { it > 0f }
        val label = linerTitles[ln.id]?.let { "$it shoulder R:" } ?: "Liner shoulder R:"
        val half = spec.overallLengthMm * 0.5f
        val startMm = ln.startFromAftMm
        val endMm = ln.startFromAftMm + ln.lengthMm
        val (col, value) = when {
            aftR != null && fwdR != null -> {
                val col = if ((startMm + endMm) * 0.5f <= half) aft else fwd
                col to (if (aftR == fwdR) fmt(aftR) else "AFT ${fmt(aftR)}, FWD ${fmt(fwdR)}")
            }
            aftR != null -> (if (startMm <= half) aft else fwd) to fmt(aftR)
            else -> (if (endMm <= half) aft else fwd) to fmt(fwdR!!)
        }
        col += line(label) { value }
    }

    return FooterColumns(
        aftLines = aft, fwdLines = fwd,
        aftLeadsWithHeading = aftTaperBlock, fwdLeadsWithHeading = fwdTaperBlock,
    )
}

private data class LetSetResult(val let: Float, val set: Float)

// startDiaMm is always the AFT-facing end of the taper (position = startFromAftMm); the
// large end is the L.E.T. whichever physical face carries it.
private fun letSet(t: Taper): LetSetResult =
    if (t.startDiaMm >= t.endDiaMm)
        LetSetResult(t.startDiaMm, t.endDiaMm)
    else
        LetSetResult(t.endDiaMm, t.startDiaMm)

/**
 * Shop notation for the two most common tapers on inch drawings: 1:12 prints as 1"/ft and
 * 1:16 as 3/4"/ft — the way the shop hand-writes them. Every other rate keeps its ratio form
 * (1:10, 1:20, exact 1:N.NNN, or the user's own manual text). Metric drawings keep the
 * ratio for all rates; inch-per-foot notation would clash with mm dimensions.
 * The fraction is spelled plain n/d — the fraction renderer sets it built-up at the draw
 * site, and FractionText.kt's parse map is the only place a vulgar glyph may appear.
 */
internal fun printedTaperRate(rateText: String, unit: UnitSystem): String = when {
    unit != UnitSystem.INCHES -> rateText
    rateText == "1:12" -> "1\"/ft"
    rateText == "1:16" -> "3/4\"/ft"
    else -> rateText
}

// Delegate to the shared auto-rate formatter so a blank-rate taper prints the
// same snapped/exact text the taper card's Auto mode shows on screen.
private fun rate1toN(t: Taper): String =
    autoTaperRateText(
        lengthMm = t.lengthMm,
        setDiaMm = t.startDiaMm,
        letDiaMm = t.endDiaMm,
        exactDecimals = 3,
    ) ?: "—"

private const val MM_PER_IN = 25.4f

private fun tpiFromPitch(pitchMm: Float): Float = if (pitchMm > 0f) MM_PER_IN / pitchMm else 0f
private fun fmtTpi(tpi: Float): String {
    val i = tpi.toInt()
    return if (abs(tpi - i) < 0.01f) i.toString() else String.format(Locale.US, "%.2f", tpi)
}

/** Pitch callout matching the active unit system: TPI for inches, mm pitch for metric. */
private fun fmtPitch(pitchMm: Float, unit: UnitSystem): String =
    if (unit == UnitSystem.INCHES) "${fmtTpi(tpiFromPitch(pitchMm))} TPI"
    else "${formatLenWithUnit(pitchMm.toDouble(), unit)} pitch"

/** Presence checks for end features. */
internal fun hasAftThread(spec: ShaftSpec): Boolean =
    spec.threads.any { it.startFromAftMm <= 0.5f }    // thread touches AFT end

internal fun hasFwdThread(spec: ShaftSpec): Boolean {
    val oal = spec.overallLengthMm
    return spec.threads.any { (it.startFromAftMm + it.lengthMm) >= (oal - 0.5f) } // thread touches FWD end
}

// --- End-feature presence detection -----------------------------------------

private data class EndFlags(
    val aftThread: Boolean,
    val fwdThread: Boolean,
    val aftTaper:  Boolean,
    val fwdTaper:  Boolean
)

/**
 * Determines which geometric features (taper, thread, etc.) physically reach
 * each end face of the shaft. A feature "exists at the end" only if its
 * interval in millimeters actually touches the end face within a small epsilon.
 *
 * We deliberately do NOT infer from component types or labels; only from
 * real geometric extents in shaft coordinates.
 *
 * Conventions:
 * - Shaft X-axis increases AFT ➜ FWD.
 * - AFT end face is fixed at X = 0.0 mm.
 * - FWD end face is at X = spec.overallLengthMm.
 * - Threads/tapers expose [startFromAftMm, startFromAftMm + lengthMm].
 *   • AFT-end features begin exactly at 0.0 mm and extend forward.
 *   • FWD-end features terminate exactly at overallLengthMm and approach from aft.
 * - A feature is considered “present” at an end when its start or end point
 *   lies within ±epsMm of that end face and its length exceeds epsMm.
 *
 * The returned EndFlags report presence independently for tapers and threads
 * at both ends, allowing combinations (e.g. a taper and a thread at the same end)
 * to be rendered in proper stacked order in the footer.
 */

private fun detectEndFeatures(spec: ShaftSpec, epsMm: Double = END_EPS_MM): EndFlags {
    val aftX = 0.0
    val fwdX = spec.overallLengthMm.toDouble()

    fun near(a: Double, b: Double) = abs(a - b) <= epsMm

    // Threads — also match excluded threads (their startFromAftMm is negative or ≥ OAL,
    // but they physically live at the shaft ends).
    val aftThread = spec.threads.any { th ->
        th.lengthMm > epsMm &&
            (near(th.startFromAftMm.toDouble(), aftX) || (th.excludeFromOAL && th.isAftEnd))
    }
    val fwdThread = spec.threads.any { th ->
        th.lengthMm > epsMm &&
            (near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) || (th.excludeFromOAL && !th.isAftEnd))
    }

    // If an end-thread exists, its shoulder can be the effective boundary for a taper.
    // Example: AFT thread starts at X=0 and a taper starts at X=threadEnd.
    val aftThreadEndX = spec.threads
        .asSequence()
        .filter { th -> near(th.startFromAftMm.toDouble(), aftX) && th.lengthMm > epsMm }
        .minByOrNull { it.startFromAftMm }
        ?.let { (it.startFromAftMm + it.lengthMm).toDouble() }

    val fwdThreadStartX = spec.threads
        .asSequence()
        .filter { th -> near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) && th.lengthMm > epsMm }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
        ?.startFromAftMm
        ?.toDouble()

    // Tapers
    val aftTaper = spec.tapers.any { tp ->
        tp.lengthMm > epsMm && (
            near(tp.startFromAftMm.toDouble(), aftX) ||
                (aftThreadEndX != null && near(tp.startFromAftMm.toDouble(), aftThreadEndX))
            )
    }
    val fwdTaper = spec.tapers.any { tp ->
        val endX = (tp.startFromAftMm + tp.lengthMm).toDouble()
        tp.lengthMm > epsMm && (
            near(endX, fwdX) ||
                (fwdThreadStartX != null && near(endX, fwdThreadStartX))
            )
    }

    return EndFlags(aftThread, fwdThread, aftTaper, fwdTaper)
}

private fun near(a: Double, b: Double, eps: Double = END_EPS_MM) =
    abs(a - b) <= eps

private fun getAftEndThread(spec: ShaftSpec): Threads? =
    spec.threads
        .asSequence()
        .filter { th ->
            th.lengthMm > END_EPS_MM &&
                (near(th.startFromAftMm.toDouble(), 0.0) || (th.excludeFromOAL && th.isAftEnd))
        }
        .minByOrNull { it.startFromAftMm }

private fun getFwdEndThread(spec: ShaftSpec): Threads? {
    val fwdX = spec.overallLengthMm.toDouble()
    return spec.threads
        .asSequence()
        .filter { th ->
            th.lengthMm > END_EPS_MM &&
                (near((th.startFromAftMm + th.lengthMm).toDouble(), fwdX) || (th.excludeFromOAL && !th.isAftEnd))
        }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
}

// internal: ShaftPdfComposer.buildTaperLengthSpans (same package, different file) calls this.
internal fun getAftEndTaper(spec: ShaftSpec): Taper? {
    val aftThread = getAftEndThread(spec)
    val anchors = mutableListOf(0.0)
    if (aftThread != null) {
        anchors += (aftThread.startFromAftMm + aftThread.lengthMm).toDouble()
    }

    return spec.tapers
        .asSequence()
        .filter { tp ->
            tp.lengthMm > END_EPS_MM && anchors.any { a -> near(tp.startFromAftMm.toDouble(), a) }
        }
        .minByOrNull { it.startFromAftMm }
}

// internal: ShaftPdfComposer.buildTaperLengthSpans (same package, different file) calls this.
internal fun getFwdEndTaper(spec: ShaftSpec): Taper? {
    val fwdX = spec.overallLengthMm.toDouble()
    val fwdThread = getFwdEndThread(spec)
    val anchors = mutableListOf(fwdX)
    if (fwdThread != null) {
        anchors += fwdThread.startFromAftMm.toDouble()
    }

    return spec.tapers
        .asSequence()
        .filter { tp ->
            val endX = (tp.startFromAftMm + tp.lengthMm).toDouble()
            tp.lengthMm > END_EPS_MM && anchors.any { a -> near(endX, a) }
        }
        .maxByOrNull { it.startFromAftMm + it.lengthMm }
}


data class FooterConfig(
    val showAftThread: Boolean,
    val showFwdThread: Boolean,
    val showAftTaper: Boolean,
    val showFwdTaper: Boolean,
    /** `PdfPrefs.footerCapturedKwInset`: the "Captured KW: inset …" line under a captured keyway. */
    val capturedKwInset: Boolean = true,
    /**
     * `PdfPrefs.footerBoltHoleDia`: the "Bolt hole: Ø …" line of a cross-drilled bolt row. The
     * shipped pref is OFF; [sheetFooterConfig] always passes it, so this default only serves
     * direct builders.
     */
    val boltHoleDia: Boolean = true,
)

/**
 * The footer configuration both sheets print from — which end columns print. ONE builder so
 * the schematic and the consolidated sheet can never disagree about the block, and built
 * BEFORE either composer's vertical budget, because the band each reserves
 * ([footerBlockHeightPt]) is measured from exactly the lines it will print.
 *
 * The job block carries no body Ø line: a body's diameter prints only where its card asks for
 * it, as a below-shaft callout (on-device ruling — the "Body: Ø" list repeated values the
 * drawing already carries and cost a footer row). Taper rendering is gated by
 * `detectEndFeatures()`; the taper flags here only decide whether the footer carries that
 * end's spec column at all.
 */
internal fun sheetFooterConfig(spec: ShaftSpec, prefs: PdfPrefs = PdfPrefs()): FooterConfig {
    val tapers = selectFooterTapers(spec)
    return FooterConfig(
        showAftThread = hasAftThread(spec),
        showFwdThread = hasFwdThread(spec),
        showAftTaper = tapers.aft != null,
        showFwdTaper = tapers.fwd != null,
        capturedKwInset = prefs.footerCapturedKwInset,
        boltHoleDia = prefs.footerBoltHoleDia,
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// "Units in …" note — Settings → Drawing → "Units note on sheets" (`PdfPrefs.showUnitsNote`)
// ──────────────────────────────────────────────────────────────────────────────

/** Text size of the units note: one step under the footer's smallest text. */
internal const val UNITS_NOTE_TEXT_PT = 8f

/**
 * Distance the note's lowest ink keeps from the paper's bottom edge — the unprintable band most
 * printers leave (¼"). The note sits in the bottom MARGIN strip, below everything the composers
 * lay out, so it can never collide with a footer column, the coupling end view, the FINAL badge or
 * the undercut sheet's notes line on any sheet or orientation.
 */
internal const val UNITS_NOTE_EDGE_CLEAR_PT = 18f

/** The note's wording for the document [unit]. */
internal fun unitsNoteText(unit: UnitSystem): String = when (unit) {
    UnitSystem.INCHES -> "Units in inches"
    UnitSystem.MILLIMETERS -> "Units in mm"
}

/**
 * Prints "Units in inches" / "Units in mm" (the DOCUMENT display unit — a per-component override
 * or a dual secondary does not change what the sheet as a whole is drawn in) small, right-aligned
 * at the page's right margin, inside the bottom margin strip (on-device request). Every composer
 * calls it LAST, and only while `PdfPrefs.showUnitsNote` is on — off draws nothing, so the sheet
 * stays byte-identical.
 *
 * The baseline keeps the ink [UNITS_NOTE_EDGE_CLEAR_PT] off the bottom edge, but never lets the
 * text rise above the margin line `pageH - marginPt`, which is where every composer's content
 * stops.
 */
internal fun drawUnitsNote(c: Canvas, pageW: Float, pageH: Float, marginPt: Float, unit: UnitSystem) {
    val paint = sheetTextPaint(UNITS_NOTE_TEXT_PT, Paint.Align.RIGHT)
    val fm = paint.fontMetrics
    val clearOfEdge = pageH - UNITS_NOTE_EDGE_CLEAR_PT - fm.descent
    val clearOfContent = pageH - marginPt - fm.ascent
    c.drawText(unitsNoteText(unit), pageW - marginPt, max(clearOfEdge, clearOfContent), paint)
}
