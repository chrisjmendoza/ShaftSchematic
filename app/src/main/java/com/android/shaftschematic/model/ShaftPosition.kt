package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShaftPosition {
    PORT,
    STBD,
    CENTER,
    OTHER;

    fun uiLabel(): String = when (this) {
        PORT -> "PORT"
        STBD -> "STBD"
        CENTER -> "Center"
        OTHER -> "Other"
    }

    /** What we print in drawings (no title). Null means do not print. */
    fun printableLabelOrNull(): String? = when (this) {
        OTHER -> null
        CENTER -> "CENTER"
        PORT, STBD -> name
    }
}
