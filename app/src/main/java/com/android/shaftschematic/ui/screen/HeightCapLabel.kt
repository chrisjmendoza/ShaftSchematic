package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.geom.PROFILE_MAX_SHAFT_HEIGHT_PT
import com.android.shaftschematic.geom.PROFILE_MIN_SHAFT_HEIGHT_PT

/**
 * The drawn-height band, spelled for user-facing copy — `0.5″` … `1.5″`.
 *
 * Every Settings blurb, Help topic and slider caption that quotes an end of the band reads it
 * from here, so moving the constants moves the copy with them. The ceiling has already changed
 * twice; a hard-coded figure left behind in a help topic is a lie the build cannot catch.
 */
private fun inchLabel(pt: Float): String =
    "%.2f".format(pt / 72f).trimEnd('0').trimEnd('.')

internal val HEIGHT_CAP_LABEL: String = inchLabel(PROFILE_MAX_SHAFT_HEIGHT_PT) + "″"

/** Same figure with a spelled-out unit, for prose that reads "up to 1.5 in at most". */
internal val HEIGHT_CAP_LABEL_IN: String = inchLabel(PROFILE_MAX_SHAFT_HEIGHT_PT) + " in"

/** The band's near end, for prose that reads "0.5 in to 1.5 in". */
internal val HEIGHT_FLOOR_LABEL_IN: String = inchLabel(PROFILE_MIN_SHAFT_HEIGHT_PT) + " in"
