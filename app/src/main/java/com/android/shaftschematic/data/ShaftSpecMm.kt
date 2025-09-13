package com.android.shaftschematic.data

/** All fields in millimeters (mm). Use 0.0 to mean "not used" when applicable. */
/** Body segment: a cylindrical span with its own diameter. */
data class BodySegmentSpec(
    /** Distance from forward end */
    val positionFromForwardMm: Double = 0.0,
    val lengthMm: Double = 0.0,
    val diameterMm: Double = 0.0
)

/** Thread spec for either end */
data class ThreadSpec(
    val diameterMm: Double = 0.0,
    val pitchMm: Double = 0.0,
    val lengthMm: Double = 0.0
)

/** Taper spec with ratio */
data class TaperSpec(
    val largeEndMm: Double = 0.0,
    val smallEndMm: Double = 0.0,
    val lengthMm: Double = 0.0,
    val ratio: TaperRatio = TaperRatio()
)

/** Taper ratio like 1:10 */
data class TaperRatio(
    val num: Double = 1.0,
    val den: Double = 0.0
) {
    val value: Double get() = if (den == 0.0) 0.0 else num / den
    override fun toString(): String = if (den == 0.0) "" else "$num:$den"
}

/** Keyway spec */
data class KeywaySpec(
    val positionFromForwardMm: Double = 0.0,
    val widthMm: Double = 0.0,
    val depthMm: Double = 0.0,
    val lengthMm: Double = 0.0
)

/** Liner spec */
data class LinerSpec(
    val positionFromForwardMm: Double = 0.0,
    val lengthMm: Double = 0.0,
    val diameterMm: Double = 0.0
)


/** Full shaft spec, everything stored in millimeters */
data class ShaftSpecMm(
    val overallLengthMm: Double = 254.0,
    val shaftDiameterMm: Double = 25.0,
    val shoulderLengthMm: Double = 40.0,
    val chamferMm: Double = 1.0,

    val forwardTaper: TaperSpec = TaperSpec(),
    val aftTaper: TaperSpec = TaperSpec(),

    val forwardThreads: ThreadSpec = ThreadSpec(),
    val aftThreads: ThreadSpec = ThreadSpec(),

    val keyways: List<KeywaySpec> = emptyList(),
    val bodySegments: List<BodySegmentSpec> = emptyList(),
    val liners: List<LinerSpec> = emptyList()
)
