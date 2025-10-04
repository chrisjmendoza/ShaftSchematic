package com.android.shaftschematic.util

enum class UnitSystem(val displayName: String) {
    INCHES("Inches") {
        override fun toMillimeters(value: Double): Double = value * 25.4
        override fun fromMillimeters(value: Double): Double = value / 25.4
    },
    MILLIMETERS("Millimeters") {
        override fun toMillimeters(value: Double): Double = value
        override fun fromMillimeters(value: Double): Double = value
    };

    abstract fun toMillimeters(value: Double): Double
    abstract fun fromMillimeters(value: Double): Double

    @kotlinx.serialization.Serializable
    enum class UnitSystem { MILLIMETERS, INCHES }
}
