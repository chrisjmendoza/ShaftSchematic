package com.android.shaftschematic.util

import java.util.Locale
import java.util.UUID

actual fun randomUuidString(): String = UUID.randomUUID().toString()

actual fun formatFixedUs(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

actual fun formatFixedUs(value: Float, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

actual fun mathRound(x: Float): Int = Math.round(x)

actual fun mathRound(x: Double): Long = Math.round(x)

actual fun mathToRadians(angdeg: Double): Double = Math.toRadians(angdeg)

actual fun mathToDegrees(angrad: Double): Double = Math.toDegrees(angrad)

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
