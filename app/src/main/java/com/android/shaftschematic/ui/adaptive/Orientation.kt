// file: app/src/main/java/com/android/shaftschematic/ui/adaptive/Orientation.kt
package com.android.shaftschematic.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import com.android.shaftschematic.R

/**
 * The orientation this device's activity runs in when no screen has unlocked rotation —
 * `R.integer.activity_orientation`: portrait on phones, unspecified (free rotation) on tablets
 * (`values-sw600dp`). ONE resource decides both what `MainActivity.onCreate` applies and what a
 * rotation-unlocking screen restores on dispose, so the two can never disagree. The manifest
 * keeps a literal portrait rather than referencing this resource: a manifest resource cannot
 * vary by configuration (lint ManifestResource), so a tablet override there is never read.
 */
fun Context.baseActivityOrientation(): Int = resources.getInteger(R.integer.activity_orientation)

/**
 * Lets this activity rotate freely — the PDF preview screens call it on entry so a landscape
 * sheet can be read in landscape. On a tablet the base orientation already is free rotation,
 * so this is a no-op there.
 */
fun Activity.unlockRotation() {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/**
 * Returns the activity to its base orientation — the paired call to [unlockRotation] on
 * dispose. Restoring a hard-coded portrait here would lock a tablet to portrait the first time
 * a preview closed.
 */
fun Activity.restoreBaseOrientation() {
    requestedOrientation = baseActivityOrientation()
}
