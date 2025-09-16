package com.android.shaftschematic.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.android.shaftschematic.data.BodySegmentSpec
import com.android.shaftschematic.data.LinerSpec
import com.android.shaftschematic.data.ShaftSpecMm
import com.android.shaftschematic.data.TaperSpec
import com.android.shaftschematic.data.ThreadSpec
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.HintStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.round

/**
 * ViewModel for the new ShaftSpecMm schema (AFT-based coordinates).
 * - All model values are stored in millimeters (Float).
 * - Positions are "startFromAftMm".
 * - Add-helpers prefill start position to the current farthest end.
 */
class ShaftViewModel : ViewModel() {

    /* ---------------- State ---------------- */

    private val _unit = MutableStateFlow(UnitSystem.INCHES)  // or MILLIMETERS as you prefer
    val unit: StateFlow<UnitSystem> = _unit

    private val _hintStyle = MutableStateFlow(HintStyle.CHIP) // CHIP / TEXT / OFF
    val hintStyle: StateFlow<HintStyle> = _hintStyle

    private val _spec = MutableStateFlow(
        ShaftSpecMm(
            overallLengthMm = 40f * UnitSystem.INCHES.toMillimeters(1.0).toFloat(), // ~1016 mm default
            // lists start empty; you’ll add bodies/liners/tapers/threads as you go
        )
    )
    val spec: StateFlow<ShaftSpecMm> = _spec

    fun setUnit(newUnit: UnitSystem) { _unit.value = newUnit }
    fun setHintStyle(style: HintStyle) { _hintStyle.value = style }

    /* ---------------- Parse / Format helpers ---------------- */

    private fun parseInCurrentUnit(raw: String): Float? {
        val n = raw.trim().toDoubleOrNull() ?: return null
        return _unit.value.toMillimeters(n).toFloat()
    }

    /** Formats a millimeter value in the current unit, trimming trailing zeros. */
    fun formatInCurrentUnit(mm: Float, decimals: Int = 3): String {
        val v = _unit.value.fromMillimeters(mm.toDouble())
        return "%.${decimals}f".format(v).trimEnd('0').trimEnd('.')
    }

    private fun nonNeg(x: Float?): Float =
        (x ?: 0f).let { if (it.isNaN()) 0f else max(0f, it) }

    /* ---------------- Coverage / Prefill logic ---------------- */

    /** Farthest filled axial position (mm from AFT) across all components, clamped to overall length. */
    private fun ShaftSpecMm.currentFillEndMm(): Float {
        fun List<Float>.maxOrZero() = if (isEmpty()) 0f else this.max()

        val bodyEnds   = bodies.map { it.startFromAftMm + it.lengthMm }
        val linerEnds  = liners.map { it.startFromAftMm + it.lengthMm }
        val taperEnds  = tapers.map { it.startFromAftMm + it.lengthMm }
        val threadEnds = threads.map { it.startFromAftMm + it.lengthMm }

        val ends = buildList {
            addAll(bodyEnds); addAll(linerEnds); addAll(taperEnds); addAll(threadEnds)
            forwardTaper?.let { add(it.startFromAftMm + it.lengthMm) }
            aftTaper?.let     { add(it.startFromAftMm + it.lengthMm) }
            forwardThread?.let{ add(it.startFromAftMm + it.lengthMm) }
            aftThread?.let    { add(it.startFromAftMm + it.lengthMm) }
        }

        return (if (ends.isEmpty()) 0f else ends.max()).coerceIn(0f, overallLengthMm)
    }

    /** Best-effort OD at the current end; prefers last body OD, then liner OD, then taper end OD. */
    private fun ShaftSpecMm.inferCurrentOdMm(): Float {
        bodies.maxByOrNull  { it.startFromAftMm + it.lengthMm }?.let { return it.diaMm }
        liners.maxByOrNull  { it.startFromAftMm + it.lengthMm }?.let { return it.odMm }
        tapers.maxByOrNull  { it.startFromAftMm + it.lengthMm }?.let { return it.endDiaMm }
        // Threads don’t define base OD. If you introduce a base OD later, return it here.
        return 0f
    }

    /** Label for a preview chip showing where the next component will auto-start (in current unit). */
    fun nextAddPositionLabel(): String {
        val mm = _spec.value.currentFillEndMm()
        return "${formatInCurrentUnit(mm, 3)} ${_unit.value.displayName} from AFT"
    }

    /** Short “chip” coverage nudge if coverage != overall length. */
    fun coverageChipHint(): String? {
        val s = _spec.value
        val filled = s.currentFillEndMm()
        return when {
            filled < s.overallLengthMm -> "Covered ${formatInCurrentUnit(filled, 3)} / ${formatInCurrentUnit(s.overallLengthMm, 3)}"
            filled > s.overallLengthMm -> "Over by ${formatInCurrentUnit(filled - s.overallLengthMm, 3)}"
            else -> null
        }
    }

    /** Longer text hint for coverage. */
    fun coverageHint(): String? {
        val s = _spec.value
        val filled = s.currentFillEndMm()
        return when {
            filled < s.overallLengthMm ->
                "Components cover ${formatInCurrentUnit(filled, 3)} of ${formatInCurrentUnit(s.overallLengthMm, 3)}. Add more or extend lengths."
            filled > s.overallLengthMm ->
                "Components exceed overall by ${formatInCurrentUnit(filled - s.overallLengthMm, 3)}. Shorten or reposition to fit."
            else -> null
        }
    }

    /* ---------------- Basics ---------------- */

    fun setOverallLength(text: String) {
        val newVal = parseInCurrentUnit(text) ?: return
        _spec.update { cur ->
            val v = nonNeg(newVal)
            // Optionally clamp components that extend beyond the new overall length
            cur.copy(overallLengthMm = v)
        }
    }

    /* ---------------- Add helpers (auto-prefill startFromAftMm) ---------------- */

    fun addBodySegment(defaultLength: String? = null, defaultDia: String? = null) {
        _spec.update { cur ->
            val start = cur.currentFillEndMm()
            val len   = defaultLength?.let { parseInCurrentUnit(it) } ?: 0f
            val dia   = defaultDia?.let { parseInCurrentUnit(it) } ?: cur.inferCurrentOdMm()
            cur.copy(
                bodies = cur.bodies + BodySegmentSpec(
                    startFromAftMm = start,
                    lengthMm = nonNeg(len),
                    diaMm = nonNeg(dia)
                )
            )
        }
    }

    fun addLiner(defaultLength: String? = null, defaultOd: String? = null) {
        _spec.update { cur ->
            val start = cur.currentFillEndMm()
            val len   = defaultLength?.let { parseInCurrentUnit(it) } ?: 0f
            val od    = defaultOd?.let { parseInCurrentUnit(it) } ?: cur.inferCurrentOdMm()
            cur.copy(
                liners = cur.liners + LinerSpec(
                    startFromAftMm = start,
                    lengthMm = nonNeg(len),
                    odMm = nonNeg(od)
                )
            )
        }
    }

    fun addTaper(defaultLength: String? = null, defaultStartDia: String? = null, defaultEndDia: String? = null) {
        _spec.update { cur ->
            val start = cur.currentFillEndMm()
            val base  = cur.inferCurrentOdMm()
            val len   = defaultLength?.let { parseInCurrentUnit(it) } ?: 0f
            val sDia  = defaultStartDia?.let { parseInCurrentUnit(it) } ?: base
            val eDia  = defaultEndDia?.let { parseInCurrentUnit(it) } ?: base
            cur.copy(
                tapers = cur.tapers + TaperSpec(
                    startFromAftMm = start,
                    lengthMm = nonNeg(len),
                    startDiaMm = nonNeg(sDia),
                    endDiaMm = nonNeg(eDia)
                )
            )
        }
    }

    fun addThread(defaultLength: String? = null, defaultMajorDia: String? = null, defaultPitch: String? = null, endLabel: String = "") {
        _spec.update { cur ->
            val start = cur.currentFillEndMm()
            val base  = cur.inferCurrentOdMm()
            val len   = defaultLength?.let { parseInCurrentUnit(it) } ?: 0f
            val dia   = defaultMajorDia?.let { parseInCurrentUnit(it) } ?: base
            val pitch = defaultPitch?.let { parseInCurrentUnit(it) } ?: 0f
            cur.copy(
                threads = cur.threads + ThreadSpec(
                    startFromAftMm = start,
                    lengthMm = nonNeg(len),
                    majorDiaMm = nonNeg(dia),
                    pitchMm = nonNeg(pitch),
                    endLabel = endLabel
                )
            )
        }
    }

    /* ---------------- Remove helpers ---------------- */

    fun removeBody(index: Int)   { _spec.update { cur -> cur.copy(bodies  = cur.bodies .toMutableList().also { if (index in it.indices) it.removeAt(index) }) } }
    fun removeLiner(index: Int)  { _spec.update { cur -> cur.copy(liners  = cur.liners .toMutableList().also { if (index in it.indices) it.removeAt(index) }) } }
    fun removeTaper(index: Int)  { _spec.update { cur -> cur.copy(tapers  = cur.tapers .toMutableList().also { if (index in it.indices) it.removeAt(index) }) } }
    fun removeThread(index: Int) { _spec.update { cur -> cur.copy(threads = cur.threads.toMutableList().also { if (index in it.indices) it.removeAt(index) }) } }

    /* ---------------- Setters for list items ---------------- */

    fun setBody(index: Int, start: String? = null, length: String? = null, dia: String? = null) {
        _spec.update { cur ->
            if (index !in cur.bodies.indices) return@update cur
            val list = cur.bodies.toMutableList()
            val src = list[index]
            val s = start ?.let { parseInCurrentUnit(it) } ?: src.startFromAftMm
            val l = length?.let { parseInCurrentUnit(it) } ?: src.lengthMm
            val d = dia   ?.let { parseInCurrentUnit(it) } ?: src.diaMm
            list[index] = src.copy(
                startFromAftMm = nonNeg(s).coerceAtMost(cur.overallLengthMm),
                lengthMm = nonNeg(l),
                diaMm = nonNeg(d)
            )
            cur.copy(bodies = list)
        }
    }

    fun setLiner(index: Int, start: String? = null, length: String? = null, od: String? = null) {
        _spec.update { cur ->
            if (index !in cur.liners.indices) return@update cur
            val list = cur.liners.toMutableList()
            val src = list[index]
            val s = start ?.let { parseInCurrentUnit(it) } ?: src.startFromAftMm
            val l = length?.let { parseInCurrentUnit(it) } ?: src.lengthMm
            val o = od    ?.let { parseInCurrentUnit(it) } ?: src.odMm
            list[index] = src.copy(
                startFromAftMm = nonNeg(s).coerceAtMost(cur.overallLengthMm),
                lengthMm = nonNeg(l),
                odMm = nonNeg(o)
            )
            cur.copy(liners = list)
        }
    }

    fun setTaper(index: Int, start: String? = null, length: String? = null, startDia: String? = null, endDia: String? = null) {
        _spec.update { cur ->
            if (index !in cur.tapers.indices) return@update cur
            val list = cur.tapers.toMutableList()
            val src = list[index]
            val s  = start    ?.let { parseInCurrentUnit(it) } ?: src.startFromAftMm
            val l  = length   ?.let { parseInCurrentUnit(it) } ?: src.lengthMm
            val sd = startDia ?.let { parseInCurrentUnit(it) } ?: src.startDiaMm
            val ed = endDia   ?.let { parseInCurrentUnit(it) } ?: src.endDiaMm
            list[index] = src.copy(
                startFromAftMm = nonNeg(s).coerceAtMost(cur.overallLengthMm),
                lengthMm = nonNeg(l),
                startDiaMm = nonNeg(sd),
                endDiaMm = nonNeg(ed)
            )
            cur.copy(tapers = list)
        }
    }

    fun setThread(index: Int, start: String? = null, length: String? = null, majorDia: String? = null, pitch: String? = null, endLabel: String? = null) {
        _spec.update { cur ->
            if (index !in cur.threads.indices) return@update cur
            val list = cur.threads.toMutableList()
            val src = list[index]
            val s  = start    ?.let { parseInCurrentUnit(it) } ?: src.startFromAftMm
            val l  = length   ?.let { parseInCurrentUnit(it) } ?: src.lengthMm
            val d  = majorDia ?.let { parseInCurrentUnit(it) } ?: src.majorDiaMm
            val p  = pitch    ?.let { parseInCurrentUnit(it) } ?: src.pitchMm
            val el = endLabel ?: src.endLabel
            list[index] = src.copy(
                startFromAftMm = nonNeg(s).coerceAtMost(cur.overallLengthMm),
                lengthMm = nonNeg(l),
                majorDiaMm = nonNeg(d),
                pitchMm = nonNeg(p),
                endLabel = el
            )
            cur.copy(threads = list)
        }
    }

    fun clearAll() {
        val hundredInMm = UnitSystem.INCHES.toMillimeters(100.0).toFloat()
        _spec.value = com.android.shaftschematic.data.ShaftSpecMm(
            overallLengthMm = hundredInMm,
            threads = emptyList(),
            tapers  = emptyList(),
            liners  = emptyList(),
            bodies  = emptyList()
        )
    }
}
