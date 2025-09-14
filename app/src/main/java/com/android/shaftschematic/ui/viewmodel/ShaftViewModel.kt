package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.android.shaftschematic.data.BodySegmentSpec
import com.android.shaftschematic.data.KeywaySpec
import com.android.shaftschematic.data.LinerSpec
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperRatio
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.data.ThreadSpec
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.HintStyle
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import java.util.Locale

class ShaftViewModel : ViewModel() {

    /* ---------------- State ---------------- */

    private val _unit = MutableStateFlow(UnitSystem.INCHES) // or MILLIMETERS — choose your default
    val unit: StateFlow<UnitSystem> = _unit

    private val _spec = MutableStateFlow(ShaftSpecMm())
    val spec: StateFlow<ShaftSpecMm> = _spec

    fun setUnit(newUnit: UnitSystem) { _unit.value = newUnit }

    private val _hintStyle = MutableStateFlow(HintStyle.CHIP)
    val hintStyle: StateFlow<HintStyle> = _hintStyle.asStateFlow()

    fun setHintStyle(style: HintStyle) { _hintStyle.value = style }


    /* ---------------- Helpers ---------------- */

    private fun parseInCurrentUnit(raw: String): Double? {
        val n = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return _unit.value.toMillimeters(n)
    }


    /** Formats a millimeter value in the current unit, trimming trailing zeros. */
    fun formatInCurrentUnit(mm: Double, decimals: Int = 4): String {
        val v = _unit.value.fromMillimeters(mm)
        return "%.${decimals}f".format(Locale.US, v).trimEnd('0').trimEnd('.')
    }

    private fun nonNeg(x: Double?): Double =
        (x ?: 0.0).let { if (it.isNaN()) 0.0 else max(0.0, it) }

    /* ---------------- Basics ---------------- */

    fun setOverallLength(text: String) {
        val newVal = parseInCurrentUnit(text) ?: return
        _spec.update { cur -> cur.copy(overallLengthMm = nonNeg(newVal)) }
    }

    fun setShaftDiameter(text: String) {
        val newVal = parseInCurrentUnit(text) ?: return
        _spec.update { cur -> cur.copy(shaftDiameterMm = nonNeg(newVal)) }
    }

    fun setShoulderLength(text: String) {
        val newVal = parseInCurrentUnit(text) ?: return
        _spec.update { cur -> cur.copy(shoulderLengthMm = nonNeg(newVal)) }
    }

    fun setChamfer(text: String) {
        val newVal = parseInCurrentUnit(text) ?: return
        _spec.update { cur -> cur.copy(chamferMm = nonNeg(newVal)) }
    }

    private fun clampPosLen(posMm: Double, lenMm: Double, maxLen: Double): Pair<Double, Double> {
        val p = posMm.coerceIn(0.0, maxLen)
        val l = lenMm.coerceAtLeast(0.0).coerceAtMost(maxLen - p)
        return p to l
    }


    /* ---------------- Threads ---------------- */

    private enum class End { FORWARD, AFT }

    private fun setThreads(end: End, diameter: String? = null, pitch: String? = null, length: String? = null) {
        _spec.update { cur ->
            val orig = if (end == End.FORWARD) cur.forwardThreads else cur.aftThreads
            val diaMm = diameter?.let { parseInCurrentUnit(it) } ?: orig.diameterMm
            val pitchMm = pitch?.let { parseInCurrentUnit(it) } ?: orig.pitchMm
            val lenMm = length?.let { parseInCurrentUnit(it) } ?: orig.lengthMm
            val upd = orig.copy(
                diameterMm = nonNeg(diaMm),
                pitchMm = nonNeg(pitchMm),
                lengthMm = nonNeg(lenMm)
            )
            if (end == End.FORWARD) cur.copy(forwardThreads = upd) else cur.copy(aftThreads = upd)
        }
    }

    fun setForwardThreads(diameter: String? = null, pitch: String? = null, length: String? = null) =
        setThreads(End.FORWARD, diameter, pitch, length)

    fun setAftThreads(diameter: String? = null, pitch: String? = null, length: String? = null) =
        setThreads(End.AFT, diameter, pitch, length)

    /* ---------------- Tapers (ratio-aware) ---------------- */

    private fun parseTaperRatio(raw: String?): TaperRatio? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace('－', '-').replace('–', '-').replace('—', '-')
        val tokens = cleaned.split(':', '/', '-').map { it.trim() }.filter { it.isNotEmpty() }
        val num: Double
        val den: Double
        when (tokens.size) {
            1 -> { // "10" -> 1:10
                num = 1.0
                den = tokens[0].toDoubleOrNull() ?: return null
            }
            else -> {
                num = tokens[0].toDoubleOrNull() ?: return null
                den = tokens[1].toDoubleOrNull() ?: return null
            }
        }
        if (den == 0.0) return null
        return TaperRatio(num, den)
    }

    private fun setTaper(
        end: End,
        large: String? = null,
        small: String? = null,
        length: String? = null,
        ratio: String? = null
    ) {
        _spec.update { cur ->
            val orig = if (end == End.FORWARD) cur.forwardTaper else cur.aftTaper
            val largeMm = large?.let { parseInCurrentUnit(it) } ?: orig.largeEndMm
            val smallMm = small?.let { parseInCurrentUnit(it) } ?: orig.smallEndMm
            val lengthMm = length?.let { parseInCurrentUnit(it) } ?: orig.lengthMm
            val ratioObj = ratio?.let { parseTaperRatio(it) } ?: orig.ratio
            val upd = orig.copy(
                largeEndMm = nonNeg(largeMm),
                smallEndMm = nonNeg(smallMm),
                lengthMm = nonNeg(lengthMm),
                ratio = ratioObj
            )
            if (end == End.FORWARD) cur.copy(forwardTaper = upd) else cur.copy(aftTaper = upd)
        }
    }

    fun setForwardTaper(large: String? = null, small: String? = null, length: String? = null, ratio: String? = null) =
        setTaper(End.FORWARD, large, small, length, ratio)

    fun setAftTaper(large: String? = null, small: String? = null, length: String? = null, ratio: String? = null) =
        setTaper(End.AFT, large, small, length, ratio)

    /* ---------------- Body Segments (dynamic) ---------------- */

    fun addBodySegment() {
        _spec.update { cur -> cur.copy(bodySegments = cur.bodySegments + BodySegmentSpec()) }
    }

    fun removeBodySegment(index: Int) {
        _spec.update { cur ->
            if (index !in cur.bodySegments.indices) cur
            else cur.copy(bodySegments = cur.bodySegments.toMutableList().also { it.removeAt(index) })
        }
    }

    fun setBodySegment(index: Int, position: String? = null, length: String? = null, diameter: String? = null) {
        _spec.update { cur ->
            if (index !in cur.bodySegments.indices) return@update cur
            val list = cur.bodySegments.toMutableList()
            val orig = list[index]
            val posMm = position?.let { parseInCurrentUnit(it) } ?: orig.positionFromForwardMm
            val lenMm = length?.let { parseInCurrentUnit(it) } ?: orig.lengthMm
            val diaMm = diameter?.let { parseInCurrentUnit(it) } ?: orig.diameterMm
            val (p, l) = clampPosLen(nonNeg(posMm), nonNeg(lenMm), cur.overallLengthMm)
            list[index] = orig.copy(
                positionFromForwardMm = p,
                lengthMm = l,
                diameterMm = nonNeg(diaMm)
            )
            cur.copy(bodySegments = list)
        }
    }


    /* ---------------- Keyways (dynamic) ---------------- */

    fun addKeyway() {
        _spec.update { cur -> cur.copy(keyways = cur.keyways + KeywaySpec()) }
    }

    fun removeKeyway(index: Int) {
        _spec.update { cur ->
            if (index !in cur.keyways.indices) cur
            else cur.copy(keyways = cur.keyways.toMutableList().also { it.removeAt(index) })
        }
    }

    fun setKeyway(index: Int, position: String? = null, width: String? = null, depth: String? = null, length: String? = null) {
        _spec.update { cur ->
            if (index !in cur.keyways.indices) return@update cur
            val list = cur.keyways.toMutableList()
            val orig = list[index]
            val posMm = position?.let { parseInCurrentUnit(it) } ?: orig.positionFromForwardMm
            val widthMm = width?.let { parseInCurrentUnit(it) } ?: orig.widthMm
            val depthMm = depth?.let { parseInCurrentUnit(it) } ?: orig.depthMm
            val (p, l) = clampPosLen(nonNeg(posMm), nonNeg(cur.overallLengthMm), cur.overallLengthMm)
            val lenMm = length?.let { parseInCurrentUnit(it) } ?: orig.lengthMm
            list[index] = orig.copy(
                positionFromForwardMm = nonNeg(posMm),
                widthMm = nonNeg(widthMm),
                depthMm = nonNeg(depthMm),
                lengthMm = nonNeg(lenMm)
            )
            cur.copy(keyways = list)
        }
    }

    /* ---------------- Liners (dynamic) ---------------- */

    fun addLiner() {
        _spec.update { cur -> cur.copy(liners = cur.liners + LinerSpec()) }
    }

    fun removeLiner(index: Int) {
        _spec.update { cur ->
            if (index !in cur.liners.indices) cur
            else cur.copy(liners = cur.liners.toMutableList().also { it.removeAt(index) })
        }
    }

    fun setLiner(index: Int, position: String? = null, length: String? = null, diameter: String? = null) {
        _spec.update { cur ->
            if (index !in cur.liners.indices) return@update cur
            val list = cur.liners.toMutableList()
            val orig = list[index]
            val posMm = position?.let { parseInCurrentUnit(it) } ?: orig.positionFromForwardMm
            val lenMm = length?.let { parseInCurrentUnit(it) } ?: orig.lengthMm
            val diaMm = diameter?.let { parseInCurrentUnit(it) } ?: orig.diameterMm
            val (p, l) = clampPosLen(nonNeg(posMm), nonNeg(lenMm), cur.overallLengthMm)
            list[index] = orig.copy(
                positionFromForwardMm = nonNeg(posMm),
                lengthMm = nonNeg(lenMm),
                diameterMm = nonNeg(diaMm)
            )
            cur.copy(liners = list)
        }
    }

    /* ---------------- Utilities (optional) ---------------- */
    // --- Coverage reporting ---

    /** Short, chip-friendly hint (e.g. "Missing 12 mm"). Returns null if coverage is complete. */
    fun coverageChipHint(): String? {
        val specNow = _spec.value
        val L = specNow.overallLengthMm
        if (L <= 0.0) return null

        val report = computeCoverageReport(specNow)
        val tolMm = 0.5

        if (report.totalGapMm <= tolMm) return null

        val gapText = formatInCurrentUnit(report.totalGapMm, 2)
        val unitName = unit.value.displayName
        return "Missing $gapText $unitName"
    }
    private data class CoverageReport(
        val coveredMm: Double,
        val totalGapMm: Double,
        val gaps: List<Pair<Double, Double>>
    )

    /** Build intervals from forward/aft threads, tapers, and body segments. */
    private fun collectIntervals(spec: ShaftSpecMm): List<Pair<Double, Double>> {
        val L = spec.overallLengthMm
        fun clampPair(start: Double, end: Double): Pair<Double, Double>? {
            val s = start.coerceIn(0.0, L)
            val e = end.coerceIn(0.0, L)
            return if (e > s) s to e else null
        }

        val ints = mutableListOf<Pair<Double, Double>>()

        // End features
        if (spec.forwardThreads.lengthMm > 0.0) clampPair(0.0, spec.forwardThreads.lengthMm)?.let(ints::add)
        if (spec.aftThreads.lengthMm > 0.0) clampPair(L - spec.aftThreads.lengthMm, L)?.let(ints::add)

        if (spec.forwardTaper.lengthMm > 0.0) clampPair(0.0, spec.forwardTaper.lengthMm)?.let(ints::add)
        if (spec.aftTaper.lengthMm > 0.0) clampPair(L - spec.aftTaper.lengthMm, L)?.let(ints::add)

        // Body segments
        for (s in spec.bodySegments) {
            clampPair(s.positionFromForwardMm, s.positionFromForwardMm + s.lengthMm)?.let(ints::add)
        }

        return ints
    }

    /** Merge overlapping intervals and compute covered length + gaps within [0, overall]. */
    private fun computeCoverageReport(spec: ShaftSpecMm): CoverageReport {
        val L = spec.overallLengthMm
        if (L <= 0.0) return CoverageReport(0.0, 0.0, emptyList())

        val sorted = collectIntervals(spec).sortedBy { it.first }
        if (sorted.isEmpty()) return CoverageReport(0.0, L, listOf(0.0 to L))

        // merge
        val merged = mutableListOf<Pair<Double, Double>>()
        for ((s, e) in sorted) {
            if (merged.isEmpty()) {
                merged.add(s to e)
            } else {
                val (ps, pe) = merged.last()
                if (s <= pe + 1e-6) {
                    if (e > pe) merged[merged.lastIndex] = ps to e
                } else {
                    merged.add(s to e)
                }
            }
        }

        // covered + gaps vs [0, L]
        var covered = 0.0
        var cursor = 0.0
        val gaps = mutableListOf<Pair<Double, Double>>()

        for ((s, e) in merged) {
            if (s > cursor + 1e-6) gaps.add(cursor to s)
            covered += (e - s)
            cursor = e
        }
        if (cursor < L - 1e-6) gaps.add(cursor to L)

        val totalGap = gaps.sumOf { (a, b) -> (b - a).coerceAtLeast(0.0) }
        return CoverageReport(coveredMm = covered.coerceAtMost(L), totalGapMm = totalGap.coerceAtLeast(0.0), gaps = gaps)
    }

    /** Human-friendly nudge. Returns null if coverage is (almost) complete. */
    fun coverageHint(): String? {
        val specNow = _spec.value
        val L = specNow.overallLengthMm
        if (L <= 0.0) return null

        val report = computeCoverageReport(specNow)
        val tolMm = 0.5  // tolerance before we start nudging

        if (report.totalGapMm <= tolMm) return null

        val coveredPct = ((report.coveredMm / L) * 100.0).coerceIn(0.0, 100.0)
        val firstGap = report.gaps.firstOrNull()
        return if (firstGap != null) {
            val gapStart = formatInCurrentUnit(firstGap.first, 3)
            val gapEnd   = formatInCurrentUnit(firstGap.second, 3)
            val totalGap = formatInCurrentUnit(report.totalGapMm, 3)
            "Heads up: your components cover ${"%.1f".format(coveredPct)}% of the overall length. " +
                    "There’s about $totalGap missing (e.g., a gap from $gapStart to $gapEnd). " +
                    "Add or extend segments, tapers, or threads to fill the gaps."
        } else {
            "Heads up: your components don’t yet cover the overall length."
        }
    }
    fun clearBodySegments() { _spec.update { it.copy(bodySegments = emptyList()) } }
    fun clearKeyways() { _spec.update { it.copy(keyways = emptyList()) } }
    fun clearLiners() { _spec.update { it.copy(liners = emptyList()) } }
}
