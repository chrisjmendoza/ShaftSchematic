// file: com/android/shaftschematic/model/ShaftSpec.kt
package com.android.shaftschematic.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Body(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val diaMm: Float = 0f,
)

@Serializable
data class Taper(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val startDiaMm: Float = 0f,
    val endDiaMm: Float = 0f,
)

@Serializable
data class ThreadSpec(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val majorDiaMm: Float = 0f,
    val pitchMm: Float = 0f,
    val lengthMm: Float = 0f,
)

@Serializable
data class Liner(
    val id: String = UUID.randomUUID().toString(),
    val startFromAftMm: Float = 0f,
    val lengthMm: Float = 0f,
    val odMm: Float = 0f,
)

@Serializable
data class ShaftSpec(
    val overallLengthMm: Float = 0f,
    val bodies: List<Body> = emptyList(),
    val tapers: List<Taper> = emptyList(),
    val threads: List<ThreadSpec> = emptyList(),
    val liners: List<Liner> = emptyList(),
)
