package com.android.shaftschematic.ui.config

import com.android.shaftschematic.util.UnitSystem

/**
 * Central, app-wide defaults for new components.
 *
 * Contract:
 *  • Callers always receive millimeters (mm).
 *  • User-facing intent for threads is expressed in in/TPI, then converted to mm.
 *  • Changing constants here automatically updates the Add… flows in the UI.
 */
object AddDefaultsConfig {
    // ---- Inch presets (authoritative for user intent) ------------------------
    const val BODY_LEN_IN   = 16f
    const val LINER_LEN_IN  = 16f
    const val TAPER_LEN_IN  = 12f

    const val BODY_DIA_IN   = 7f
    const val LINER_OD_IN   = 8f
    const val TAPER_SET_IN  = 6f
    const val TAPER_LET_IN  = 7f

    // Thread defaults per spec: 6" length, 5" major, 4 TPI
    const val THREAD_LEN_IN      = 6f
    const val THREAD_MAJ_DIA_IN  = 5f
    const val THREAD_TPI_IN      = 4f

    // ---- Metric presets (used when unit = mm) --------------------------------
    // Keep explicit values to avoid drift and to match drawings/docs precisely.
    const val BODY_LEN_MM        = 406.4f
    const val LINER_LEN_MM       = 406.4f
    const val TAPER_LEN_MM       = 304.8f

    const val BODY_DIA_MM        = 177.8f
    const val LINER_OD_MM        = 203.2f
    const val TAPER_SET_MM       = 152.4f
    const val TAPER_LET_MM       = 177.8f

    // 6 in → 152.4 mm ; 5 in → 127.0 mm
    const val THREAD_LEN_MM      = 152.4f
    const val THREAD_MAJ_DIA_MM  = 127.0f

    // ---- Geometry helpers ----------------------------------------------------
    /** Taper slope as rise/run; 1:12 by default. */
    const val TAPER_RATIO = 1f / 12f
}

/* ---------- Unit-aware helpers (always return mm) ---------------------------- */

private const val MM_PER_INCH = 25.4f

fun defaultBodyLenMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.BODY_LEN_IN * MM_PER_INCH
    else AddDefaultsConfig.BODY_LEN_MM

fun defaultLinerLenMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.LINER_LEN_IN * MM_PER_INCH
    else AddDefaultsConfig.LINER_LEN_MM

fun defaultTaperLenMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.TAPER_LEN_IN * MM_PER_INCH
    else AddDefaultsConfig.TAPER_LEN_MM

fun defaultThreadLenMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.THREAD_LEN_IN * MM_PER_INCH
    else AddDefaultsConfig.THREAD_LEN_MM

fun defaultThreadMajorDiaMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.THREAD_MAJ_DIA_IN * MM_PER_INCH
    else AddDefaultsConfig.THREAD_MAJ_DIA_MM

fun defaultBodyDiaMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.BODY_DIA_IN * MM_PER_INCH
    else AddDefaultsConfig.BODY_DIA_MM

fun defaultLinerOdMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.LINER_OD_IN * MM_PER_INCH
    else AddDefaultsConfig.LINER_OD_MM

fun defaultTaperSetDiaMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.TAPER_SET_IN * MM_PER_INCH
    else AddDefaultsConfig.TAPER_SET_MM

fun defaultTaperLetDiaMm(unit: UnitSystem): Float =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.TAPER_LET_IN * MM_PER_INCH
    else AddDefaultsConfig.TAPER_LET_MM

/** 4 TPI → 25.4 / 4 = 6.35 mm pitch (unit-independent; canonical output is mm). */
fun defaultThreadPitchMm(): Float = MM_PER_INCH / AddDefaultsConfig.THREAD_TPI_IN
