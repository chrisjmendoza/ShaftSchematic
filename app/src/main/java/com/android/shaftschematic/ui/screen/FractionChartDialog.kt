package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.util.FRACTION_CHART_DEFAULT_INTERVAL
import com.android.shaftschematic.util.FRACTION_CHART_INTERVALS
import com.android.shaftschematic.util.FractionChartRow
import com.android.shaftschematic.util.fractionChartRows

/**
 * FractionChartDialog — the shop's decimal-equivalent chart: every step of an inch at the
 * chosen interval (1/8 | 1/16 | 1/32 | 1/64, opening on 1/32) with its exact decimal and its
 * millimetres.
 *
 * Same tool posture as [UnitConverterDialog] and [TaperCalcDialog]: it reads nothing from the
 * shaft, stores nothing, marks nothing dirty and prints on no sheet; the interval resets to the
 * default on every open. A reference, not an input — the user reads a value and types it where
 * it belongs. Rows and their exact decimals are pure (`util/FractionChart.kt`).
 */
@Composable
fun FractionChartDialog(onDismiss: () -> Unit) {
    var interval by rememberSaveable { mutableIntStateOf(FRACTION_CHART_DEFAULT_INTERVAL) }
    val rows = remember(interval) { fractionChartRows(interval) }
    val listState = rememberLazyListState()
    // A new interval starts the column at its top; staying scrolled to row 40 of 64ths would
    // land past the end of a 16-row column of sixteenths.
    LaunchedEffect(interval) { listState.scrollToItem(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("fraction_chart_dialog"),
        title = { Text("Fraction chart") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScaleChipRow(
                    denominator = interval,
                    onSelect = { interval = it },
                    options = FRACTION_CHART_INTERVALS,
                    title = "Interval",
                    chipLabel = { "1/$it" },
                    tagPrefix = "fraction_chart_interval_",
                )
                Column {
                    ChartRow(
                        fraction = "Fraction",
                        decimal = "Decimal in",
                        millimeters = "mm",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    HorizontalDivider()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().testTag("fraction_chart_list"),
                    ) {
                        items(rows, key = { "${it.numerator}/${it.denominator}" }) { row ->
                            ChartEntry(row)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("fraction_chart_close")) {
                Text("Close")
            }
        },
    )
}

/** One chart line; the eighths are set bold on the finer intervals, as a printed chart does. */
@Composable
private fun ChartEntry(row: FractionChartRow) {
    val base = MaterialTheme.typography.bodyLarge
    ChartRow(
        fraction = row.fraction,
        decimal = row.decimal,
        millimeters = row.millimeters,
        style = if (row.isMajor) base.copy(fontWeight = FontWeight.Bold) else base,
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("fraction_chart_row_${row.numerator}_${row.denominator}"),
    )
}

/**
 * The three columns: the fraction left, the two numbers right-aligned in tabular figures so
 * their decimal points line up down the column (every row of an interval has the same places).
 */
@Composable
private fun ChartRow(
    fraction: String,
    decimal: String,
    millimeters: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val figures = style.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(fraction, style = figures, modifier = Modifier.weight(1f))
        Text(decimal, style = figures, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
        Text(millimeters, style = figures, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
