// file: app/src/main/java/com/android/shaftschematic/ui/screen/RunoutBubbleDialog.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.shaftschematic.geom.RUNOUT_CLOCK_TICKS
import com.android.shaftschematic.geom.bubbleAngleDeg
import com.android.shaftschematic.geom.clockTickAngleDeg
import com.android.shaftschematic.geom.clockTickLabel
import com.android.shaftschematic.geom.clockTickRimOffset
import com.android.shaftschematic.geom.isOnRingBand
import com.android.shaftschematic.geom.snapToClockTick
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.filterNumericInput
import com.android.shaftschematic.util.formatRunoutValue
import kotlin.math.min

/**
 * RunoutBubbleDialog — the "zoom-in" editor for one runout bubble.
 *
 * A large version of the bubble with:
 * - an interactive **ring**: tapping or dragging on the ring places/moves the high-spot marker,
 *   which snaps to 30-minute clock ticks ([snapToClockTick]). Touches off the ring (the hollow
 *   centre where the value field lives, or far outside the rim) do nothing.
 * - a **value field** in the centre for the TIR reading, entered/shown in the active [unit]
 *   (converted to canonical mm only on Save — the model never sees display units).
 * - three buttons: **Clear** (wipe the working marker + value in place), **Cancel** (discard,
 *   close), **Save** (persist + close). Saving with neither value nor marker removes the reading.
 *
 * Both value and marker are optional and independent. The working state seeds from the passed
 * initial values, so reopening a saved bubble shows its saved data until the user edits it.
 *
 * See `docs/RunoutBubbleEditor_PLAN.md` and `model/RunoutReading.kt`.
 *
 * @param onSave (valueMm, highSpotHalfHours) — either may be null. Called then the dialog closes.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun RunoutBubbleDialog(
    title: String,
    unit: UnitSystem,
    initialValueMm: Float?,
    initialHighSpotHalfHours: Int?,
    onSave: (valueMm: Float?, highSpotHalfHours: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var markerTick by remember { mutableStateOf(initialHighSpotHalfHours) }
    var valueText by remember {
        mutableStateOf(initialValueMm?.let { formatRunoutValue(it, unit) } ?: "")
    }
    val unitSuffix = if (unit == UnitSystem.INCHES) "in" else "mm"

    val ringColor    = MaterialTheme.colorScheme.onSurface
    val tickColor    = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor  = Color(0xFFC62828) // shop red — same high-spot colour as the printed bubble
    val labelColor   = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle   = MaterialTheme.typography.labelMedium.copy(color = labelColor)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(340.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tap or drag the ring to set the high spot",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // ── The interactive bubble ────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        // Two gesture detectors: tap places the marker, drag sweeps it. Both
                        // only act when the touch is on the ring band (isOnRingBand); the hollow
                        // centre passes through to the value field, off-ring touches are ignored.
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                markerTickFromTouch(pos, size.width.toFloat(), size.height.toFloat())
                                    ?.let { markerTick = it }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    markerTickFromTouch(pos, size.width.toFloat(), size.height.toFloat())
                                        ?.let { markerTick = it }
                                },
                                onDrag = { change, _ ->
                                    markerTickFromTouch(change.position, size.width.toFloat(), size.height.toFloat())
                                        ?.let { markerTick = it; change.consume() }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        drawBubbleEditor(
                            markerTick = markerTick,
                            ringColor = ringColor,
                            tickColor = tickColor,
                            markerColor = markerColor,
                            labelStyle = labelStyle,
                            textMeasurer = textMeasurer,
                        )
                    }

                    // Value field sits in the hollow centre.
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = {
                            valueText = filterNumericInput(
                                raw = it, allowNegative = false, allowFraction = true, allowColon = false,
                            )
                        },
                        singleLine = true,
                        label = { Text("TIR") },
                        suffix = { Text(unitSuffix) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                        modifier = Modifier.width(132.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = markerTick?.let { "High spot: ${clockTickLabel(it)}" } ?: "No high spot set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (markerTick != null) markerColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // ── Clear · Cancel · Save ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { markerTick = null; valueText = "" }) { Text("Clear") }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val mm = valueText.trim().toDoubleOrNull()
                                ?.let { unit.toMillimeters(it).toFloat() }
                            onSave(mm, markerTick)
                        }) { Text("Save") }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Drawing + touch mapping
// ─────────────────────────────────────────────────────────────────────────────

/** Fraction of the min half-dimension used as the ring radius (leaves room for the rim dot + labels). */
private const val EDITOR_RING_FRACTION = 0.74f

/**
 * Map a touch position in editor-canvas px to a snapped clock tick, or null when the touch is not
 * on the ring band (so the centre value field and far-outside touches are ignored). Ring geometry
 * is derived identically here and in [drawBubbleEditor] from the canvas size.
 */
private fun markerTickFromTouch(pos: Offset, widthPx: Float, heightPx: Float): Int? {
    val cx = widthPx / 2f
    val cy = heightPx / 2f
    val r = min(widthPx, heightPx) / 2f * EDITOR_RING_FRACTION
    // Generous annulus: from the central field out past the rim, but not the far corners.
    return if (isOnRingBand(cx, cy, r, bandHalfWidth = r * 0.6f, px = pos.x, py = pos.y)) {
        snapToClockTick(bubbleAngleDeg(cx, cy, pos.x, pos.y))
    } else {
        null
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawBubbleEditor(
    markerTick: Int?,
    ringColor: Color,
    tickColor: Color,
    markerColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(size.width, size.height) / 2f * EDITOR_RING_FRACTION
    val center = Offset(cx, cy)

    // Ring.
    drawCircle(ringColor, radius = r, center = center, style = Stroke(width = 3.dp.toPx()))

    // Clock ticks: 24 (half-hour). Emphasize the four cardinals (12/3/6/9).
    for (tick in 0 until RUNOUT_CLOCK_TICKS) {
        val (ux, uy) = clockTickRimOffset(tick, 1f) // unit direction
        val cardinal = tick % 6 == 0
        val inner = r - (if (cardinal) 14.dp.toPx() else 8.dp.toPx())
        drawLine(
            color = if (cardinal) ringColor else tickColor,
            start = Offset(cx + ux * inner, cy + uy * inner),
            end = Offset(cx + ux * r, cy + uy * r),
            strokeWidth = if (cardinal) 2.5.dp.toPx() else 1.2.dp.toPx(),
        )
    }

    // Keyway reference notch: open slot descending from 12 o'clock into the circle (matches the
    // printed bubble's key-at-top convention; the top rim is broken across the slot mouth).
    val notchHalf = r * 0.10f
    val notchDepth = r * 0.20f
    drawLine(ringColor, Offset(cx - notchHalf, cy - r), Offset(cx - notchHalf, cy - r + notchDepth), strokeWidth = 2.dp.toPx())
    drawLine(ringColor, Offset(cx + notchHalf, cy - r), Offset(cx + notchHalf, cy - r + notchDepth), strokeWidth = 2.dp.toPx())
    drawLine(ringColor, Offset(cx - notchHalf, cy - r + notchDepth), Offset(cx + notchHalf, cy - r + notchDepth), strokeWidth = 2.dp.toPx())

    // Cardinal labels 12/3/6/9.
    val labels = mapOf(0 to "12", 6 to "3", 12 to "6", 18 to "9")
    labels.forEach { (tick, txt) ->
        val (ux, uy) = clockTickRimOffset(tick, 1f)
        val lr = r + 16.dp.toPx()
        val measured = textMeasurer.measure(txt, labelStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = txt,
            style = labelStyle,
            topLeft = Offset(
                cx + ux * lr - measured.size.width / 2f,
                cy + uy * lr - measured.size.height / 2f,
            ),
        )
    }

    // High-spot marker: a short dash straddling the rim at the clock position — no radial line,
    // so the centred value stays legible (matches the printed bubble).
    if (markerTick != null) {
        val (ux, uy) = clockTickRimOffset(markerTick, 1f)
        val inner = r * 0.84f
        val outer = r * 1.16f
        drawLine(
            markerColor,
            Offset(cx + ux * inner, cy + uy * inner),
            Offset(cx + ux * outer, cy + uy * outer),
            strokeWidth = 4.dp.toPx(),
        )
    }
}
