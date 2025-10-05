package com.android.shaftschematic.ui.config

import com.android.shaftschematic.util.UnitSystem

/**
 * Central, app-wide defaults for new components.
 * Canonical units are millimeters (mm). Helpers below return mm for the active unit.
 */
object AddDefaultsConfig {
    // ---- Inch presets (authoritative for user-facing intent) ----
    const val BODY_LEN_IN   = 16f
    const val LINER_LEN_IN  = 16f
    const val TAPER_LEN_IN  = 16f
    const val THREAD_LEN_IN = 5f      // requested: 5 inches
    const val THREAD_MAJ_DIA_IN = 6f  // requested: 6 inches
    const val THREAD_TPI_IN = 4f      // requested: 4 TPI

    // ---- Metric presets (used when unit = mm) ----
    // If a metric default differs from the inch equivalent, set it explicitly here.
    const val BODY_LEN_MM   = 100f
    const val LINER_LEN_MM  = 100f
    const val TAPER_LEN_MM  = 100f
    const val THREAD_LEN_MM = 127f    // 5 in → 127 mm
    const val THREAD_MAJ_DIA_MM = 152.4f // 6 in → 152.4 mm

    // ---- Geometry helpers ----
    // Taper slope as rise/run. 1:12 by default.
    const val TAPER_RATIO = 1f / 12f
}

/* ---------- Unit-aware helpers (always return mm) ---------- */

fun defaultBodyLenMm(unit: UnitSystem) =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.BODY_LEN_IN * 25.4f else AddDefaultsConfig.BODY_LEN_MM

fun defaultLinerLenMm(unit: UnitSystem) =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.LINER_LEN_IN * 25.4f else AddDefaultsConfig.LINER_LEN_MM

fun defaultTaperLenMm(unit: UnitSystem) =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.TAPER_LEN_IN * 25.4f else AddDefaultsConfig.TAPER_LEN_MM

fun defaultThreadLenMm(unit: UnitSystem) =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.THREAD_LEN_IN * 25.4f else AddDefaultsConfig.THREAD_LEN_MM

fun defaultThreadMajorDiaMm(unit: UnitSystem) =
    if (unit == UnitSystem.INCHES) AddDefaultsConfig.THREAD_MAJ_DIA_IN * 25.4f else AddDefaultsConfig.THREAD_MAJ_DIA_MM

/** 4 TPI → 25.4 / 4 = 6.35 mm pitch (unit-independent; canonical is mm). */
fun defaultThreadPitchMm(): Float = 25.4f / AddDefaultsConfig.THREAD_TPI_IN
