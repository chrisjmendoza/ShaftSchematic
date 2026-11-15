package com.android.shaftschematic.gfx

/** A font face a [Paint] sets text in. `android.graphics.Typeface` on Android. */
expect open class Typeface

/**
 * The platform's medium-weight sans — the stand-in for a heavier bundled face that did not load.
 * `Typeface.create("sans-serif-medium", Typeface.NORMAL)` on Android.
 */
expect fun fallbackSansMediumTypeface(): Typeface

/**
 * The platform's regular sans — the stand-in for a bundled face that did not load.
 * `Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)` on Android.
 */
expect fun fallbackSansTypeface(): Typeface
