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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

class ShaftViewModel : ViewModel() {

    /* ---------------- State ---------------- */

    private val _unit = MutableStateFlow(UnitSystem.INCHES) // or MILLIMETERS — choose your default
    val unit: StateFlow<UnitSystem> = _unit

    private val _spec = MutableStateFlow(ShaftSpecMm())
    val spec: StateFlow<ShaftSpecMm> = _spec

    fun setUnit(newUnit: UnitSystem) { _unit.value = newUnit }

    /* ---------------- Helpers ---------------- */

    private fun parseInCurrentUnit(raw: String): Double? {
        val n = raw.trim().toDoubleOrNull() ?: return null
        return _unit.value.toMillimeters(n)
    }

    /** Formats a millimeter value in the current unit, trimming trailing zeros. */
    fun formatInCurrentUnit(mm: Double, decimals: Int = 4): String {
        val v = _unit.value.fromMillimeters(mm)
        return "%.${decimals}f".format(v).trimEnd('0').trimEnd('.')
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
            list[index] = orig.copy(
                positionFromForwardMm = nonNeg(posMm),
                lengthMm = nonNeg(lenMm),
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
            list[index] = orig.copy(
                positionFromForwardMm = nonNeg(posMm),
                lengthMm = nonNeg(lenMm),
                diameterMm = nonNeg(diaMm)
            )
            cur.copy(liners = list)
        }
    }

    /* ---------------- Utilities (optional) ---------------- */

    fun clearBodySegments() { _spec.update { it.copy(bodySegments = emptyList()) } }
    fun clearKeyways() { _spec.update { it.copy(keyways = emptyList()) } }
    fun clearLiners() { _spec.update { it.copy(liners = emptyList()) } }
}
