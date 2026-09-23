// file: app/src/main/java/com/android/shaftschematic/ui/adaptive/ReadableWidth.kt
package com.android.shaftschematic.ui.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a single column of reading content (settings rows, help cards, the Start
 * screen's buttons, a document list) may run. Past this a row's text and its trailing control
 * drift too far apart to read as one line, and a full-width button on a landscape tablet reads
 * as a banner.
 */
val READABLE_CONTENT_MAX_WIDTH: Dp = 720.dp

/**
 * Caps a full-width column at [max] and centres it in whatever width the parent offers.
 *
 * On a phone this is a no-op (the window is narrower than the cap, so the column still fills
 * it); on a tablet the column stops growing at [max] and sits in the middle. Apply it to the
 * one scrolling column (or `LazyColumn`) of a list screen, never to each row — the cap is a
 * property of the page, and rows keep their own `fillMaxWidth()`.
 *
 * Modifier order is load-bearing: the outer `fillMaxWidth` claims the parent's width,
 * `wrapContentWidth` lets the inner box be narrower and centres it, and the inner
 * `widthIn(max).fillMaxWidth()` fills up to the cap.
 */
fun Modifier.readableWidth(max: Dp = READABLE_CONTENT_MAX_WIDTH): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = max)
    .fillMaxWidth()
