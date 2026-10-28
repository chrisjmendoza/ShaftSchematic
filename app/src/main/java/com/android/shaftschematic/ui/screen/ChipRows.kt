package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A chip's label: one line, never broken inside a word.
 *
 * A chip squeezed by its row wraps its label wherever the width runs out — "F / W / D" one letter
 * per line, "Portr / ait" — at a large system font (on-device report, 130% and 200%). A label
 * that cannot wrap keeps the chip its natural width; [ChipFlowRow] is what then finds it room.
 */
@Composable
internal fun ChipLabel(
    text: String,
    style: TextStyle? = null,
    fontFamily: FontFamily? = null,
) {
    if (style != null) {
        Text(text, style = style, fontFamily = fontFamily, maxLines = 1, softWrap = false)
    } else {
        Text(text, fontFamily = fontFamily, maxLines = 1, softWrap = false)
    }
}

/**
 * A row of chips that moves a chip that does not fit onto a second line rather than squeezing
 * it. While everything fits it lays out as the plain centred [androidx.compose.foundation.layout.Row]
 * it replaces, so the default font scale is unchanged.
 *
 * [spacing] is the gap between items; rows whose chips carry their own end padding pass 0.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlowRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        itemVerticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
