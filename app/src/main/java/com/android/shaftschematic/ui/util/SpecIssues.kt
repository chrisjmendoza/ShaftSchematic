package com.android.shaftschematic.ui.util

import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.collidingIds
import com.android.shaftschematic.model.collisionPartnerIds
import com.android.shaftschematic.pdf.formatLenWithUnit
import com.android.shaftschematic.util.UnitSystem

/** What is wrong with the parts a [SpecIssue] names. */
enum class SpecIssueKind { OVERLAP, PAST_FWD_END, PAST_AFT_END, TINY_SEGMENT }

/**
 * One problem on the drawing, naming the parts it is about.
 *
 * @property componentIds the stored ids of the parts, in the order the message names them — the
 *   first is the one a tap on the banner line selects.
 * @property message the banner line, in plain shop language, parts named by their card titles.
 * @property markStartMm / [markEndMm] the stretch of shaft the preview marks red (mm from the
 *   AFT face): the overlapping stretch of an [SpecIssueKind.OVERLAP] pair, the part beyond the
 *   end of a past-end one. Equal for a [SpecIssueKind.TINY_SEGMENT], which marks nothing.
 * @property boundMm the shaft end a past-end part runs beyond (0 or the OAL); null otherwise.
 */
data class SpecIssue(
    val kind: SpecIssueKind,
    val componentIds: List<String>,
    val message: String,
    val markStartMm: Float = 0f,
    val markEndMm: Float = 0f,
    val boundMm: Float? = null,
)

/**
 * Every problem on [spec], overlaps first (they block export), then parts past the FWD end, past
 * the AFT end, and tiny segments. The ONE list behind the Schematic tab's warning banner, the
 * preview's red marks (`RenderOptions.issueMarks`) and — for overlaps — the export gate's message
 * ([overlapExportMessage]), so the three can never name different parts.
 *
 * **Only PROBLEMS belong here** — anything the user can act on, never a note about normal
 * behaviour. The banner is styled as an advisory, so a line describing ordinary authoring reads as
 * an error and makes the real ones easier to ignore (on-device report: a "no explicit bodies" note
 * that fired on every taper-and-liner shaft was removed on that ground).
 *
 * The rules are the cards' own: overlaps are the export gate's pairs (`collisionPartnerIds`, which
 * the cards' "Overlaps …" chip also reads — bodies never collide), and the ends are the card
 * chip's bounds halves ([startsBeforeAftEnd] / [endsPastOal]). Bodies, tapers, liners and
 * OAL-counted threads are judged on their STORED spans; excluded threads and reference features
 * (bolt slots, wear and runout marks, undercuts) never appear. A 0 OAL ("not typed yet") has no
 * ends to run past. Distances print in [unit] through the footer's length formatter, so an inch
 * drawing reads fractions.
 */
fun specIssues(spec: ShaftSpec, unit: UnitSystem): List<SpecIssue> {
    val titles = componentCardTitles(spec)
    fun title(id: String) = titles[id] ?: "A component"
    fun len(mm: Float) = formatLenWithUnit(mm.toDouble(), unit)

    val parts = issueParts(spec)
    val out = mutableListOf<SpecIssue>()
    out += overlapIssues(spec, parts, ::title)
    // Within each kind the lines read AFT → FWD, the order the parts sit on the shaft.
    val aftToFwd = parts.sortedWith(compareBy({ it.startMm }, { it.id }))

    if (spec.overallLengthMm > 0f) {
        aftToFwd.filter { endsPastOal(spec, it.startMm, it.lengthMm) }.forEach { p ->
            val end = p.startMm + p.lengthMm
            out += SpecIssue(
                kind = SpecIssueKind.PAST_FWD_END,
                componentIds = listOf(p.id),
                message = "${title(p.id)} runs ${len(end - spec.overallLengthMm)} past the FWD end",
                markStartMm = maxOf(p.startMm, spec.overallLengthMm),
                markEndMm = end,
                boundMm = spec.overallLengthMm,
            )
        }
        aftToFwd.filter { startsBeforeAftEnd(it.startMm) }.forEach { p ->
            out += SpecIssue(
                kind = SpecIssueKind.PAST_AFT_END,
                componentIds = listOf(p.id),
                message = "${title(p.id)} starts ${len(-p.startMm)} before the AFT end",
                markStartMm = p.startMm,
                markEndMm = minOf(p.startMm + p.lengthMm, 0f),
                boundMm = 0f,
            )
        }
    }

    aftToFwd.filter { isShortSegment(it.lengthMm) }.forEach { p ->
        out += SpecIssue(
            kind = SpecIssueKind.TINY_SEGMENT,
            componentIds = listOf(p.id),
            message = "${title(p.id)} is shorter than 1 mm",
            markStartMm = p.startMm,
            markEndMm = p.startMm,
        )
    }
    return out
}

/**
 * The export gate's refusal for a spec with overlaps — the banner's first overlap line, so the
 * gate and the banner say the same thing ("Can't export: FWD Liner overlaps …"). Null when
 * nothing overlaps.
 */
fun overlapExportMessage(spec: ShaftSpec): String? {
    val titles = componentCardTitles(spec)
    val overlaps = overlapIssues(spec, issueParts(spec)) { titles[it] ?: "A component" }
    val first = overlaps.firstOrNull() ?: return null
    val more = if (overlaps.size > 1) " (+${overlaps.size - 1} more)" else ""
    return "Can't export: ${first.message}$more"
}

/**
 * A judged part: its stored span and, for overlaps, which part a line names first — the sleeve
 * over the shaft before what it sits on (a liner "overlaps" the taper it runs onto), then a
 * thread, then a taper ([rank] ascending).
 */
private data class IssuePart(val id: String, val startMm: Float, val lengthMm: Float, val rank: Int)

/** Bodies, tapers, liners and OAL-counted threads, by stored span. */
private fun issueParts(spec: ShaftSpec): List<IssuePart> =
    spec.liners.map { IssuePart(it.id, it.startFromAftMm, it.lengthMm, 0) } +
        spec.threads.filter { !it.excludeFromOAL }.map { IssuePart(it.id, it.startFromAftMm, it.lengthMm, 1) } +
        spec.tapers.map { IssuePart(it.id, it.startFromAftMm, it.lengthMm, 2) } +
        spec.bodies.map { IssuePart(it.id, it.startFromAftMm, it.lengthMm, 3) }

/**
 * One issue per colliding pair, ordered by where the overlap starts (AFT → FWD). The pairs are
 * [collisionPartnerIds]' — the export gate's rule — so a pair here is exactly a pair the cards'
 * chips name.
 */
private fun overlapIssues(
    spec: ShaftSpec,
    parts: List<IssuePart>,
    title: (String) -> String,
): List<SpecIssue> {
    val colliding = spec.collidingIds()
    if (colliding.isEmpty()) return emptyList()
    val byId = parts.associateBy { it.id }
    val seen = mutableSetOf<Pair<String, String>>()
    val out = mutableListOf<SpecIssue>()
    for (id in colliding) {
        val a = byId[id] ?: continue
        for (partnerId in spec.collisionPartnerIds(id)) {
            val b = byId[partnerId] ?: continue
            val key = if (a.id < b.id) a.id to b.id else b.id to a.id
            if (!seen.add(key)) continue
            val (first, second) = listOf(a, b).sortedWith(compareBy({ it.rank }, { it.startMm }, { it.id }))
                .let { it[0] to it[1] }
            out += SpecIssue(
                kind = SpecIssueKind.OVERLAP,
                componentIds = listOf(first.id, second.id),
                message = "${title(first.id)} overlaps ${title(second.id)}",
                markStartMm = maxOf(a.startMm, b.startMm),
                markEndMm = minOf(a.startMm + a.lengthMm, b.startMm + b.lengthMm),
            )
        }
    }
    return out.sortedWith(compareBy({ it.markStartMm }, { it.message }))
}
