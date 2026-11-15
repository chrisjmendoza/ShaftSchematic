package com.android.shaftschematic.ui.drawing.render

/**
 * Hidden-line dash for far-side (180°-apart) keyways, in px. Mirrored exactly in the PDF
 * (`DashPathEffect(floatArrayOf(HIDDEN_DASH_ON, HIDDEN_DASH_OFF), 0f)`) so preview and
 * export match. Kept module-level so both the renderer and the same-math preview agree.
 */
const val HIDDEN_DASH_ON = 6f
const val HIDDEN_DASH_OFF = 4f
