package com.android.shaftschematic.data

/** Middle panel: job/customer info printed under the shaft (labels always shown). */
data class MetaBlock(
    val customer: String = "",
    val vessel: String = "",
    val jobNumber: String = "",
    val side: String = "",      // e.g., "Port" / "Starboard"
    val date: String = ""       // e.g., "2025-09-14"
)

/** Per-end panel (left = AFT, right = FWD typically). All fields are pre-formatted strings. */
data class EndInfo(
    /** Large End Taper, e.g. "Ø45 mm" or "—" */
    val let: String = "",
    /** Small End Taper, e.g. "Ø30 mm" or "—" */
    val set: String = "",
    /** Taper Rate, e.g. "1:10" or "0.100" */
    val taperRate: String = "",
    /** Keyway, e.g. "10 × 3 mm" */
    val keyway: String = "",
    /** Threads, e.g. "Ø30 × 2.0 × 60 mm" or UNC form; free text is OK */
    val threads: String = ""
)
