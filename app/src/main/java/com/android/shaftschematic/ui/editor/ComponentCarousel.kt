package com.android.shaftschematic.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.input.NumericInputField
import com.android.shaftschematic.util.parseFractionOrDecimal
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults

import kotlinx.coroutines.launch // <-- needed for scope.launch

// --- Data model (demo; adapt to your real model) ---
sealed class ShaftComponent {
    abstract val id: String
    abstract val name: String

    data class Thread(
        override val id: String,
        val startFromAftMm: Float,
        val majorDiaMm: Float,
        val pitchMm: Float,
        val lengthMm: Float,
        val excludeFromOAL: Boolean,
    ) : ShaftComponent() { override val name: String = "Thread" }

    data class Taper(
        override val id: String,
        val startDiaMm: Float,
        val endDiaMm: Float,
        val lengthMm: Float,
    ) : ShaftComponent() { override val name: String = "Taper" }

    data class Body(
        override val id: String,
        val diaMm: Float,
        val lengthMm: Float,
    ) : ShaftComponent() { override val name: String = "Body" }

    data class Liner(
        override val id: String,
        val widthMm: Float,
        val lengthMm: Float,
    ) : ShaftComponent() { override val name: String = "Liner" }
}

// Pages include two “phantom” add slots at ends
private sealed class Page {
    data object AddBefore : Page()
    data class Existing(val index: Int) : Page()
    data object AddAfter : Page()
}

@Composable
fun ComponentCarousel(
    components: List<ShaftComponent>,
    modifier: Modifier = Modifier,
    // callbacks
    onAddRequested: (atIndex: Int) -> Unit,
    onUpdateComponent: (index: Int, updated: ShaftComponent) -> Unit,
    onDeleteComponent: (index: Int) -> Unit,
    // optional: preview highlight hook
    onFocusedComponentChanged: (indexOrNull: Int?) -> Unit = {}
) {
    // Build pages: [AddBefore] [Existing * N] [AddAfter]
    val pages = remember(components) {
        buildList {
            add(Page.AddBefore)
            components.indices.forEach { add(Page.Existing(it)) }
            add(Page.AddAfter)
        }
    }

    val initial = if (components.isNotEmpty()) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = initial,
        pageCount = { pages.size }   // <-- lambda, not an Int
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage, pages) {
        val p = pages[pagerState.currentPage]
        onFocusedComponentChanged((p as? Page.Existing)?.index)
    }

    // Layout: [10% tap zone] [80% page] [10% tap zone]
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
    ) {
        // Left arrow/tap zone
        Box(
            Modifier
                .weight(0.1f)
                .fillMaxHeight()
                .zIndex(2f)
                .clickableWithoutRipple {
                    val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                    if (prev != pagerState.currentPage) {
                        scope.launch { pagerState.animateScrollToPage(prev) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ArrowHint(left = true)
        }

        // Center pager (old API requires pageCount here)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(0.8f)
                .fillMaxHeight()
        ) { page ->
            when (val p = pages[page]) {
                Page.AddBefore -> AddComponentCard(
                    label = "Add section at start",
                    onAdd = { onAddRequested(0) }
                )
                Page.AddAfter -> AddComponentCard(
                    label = "Add section at end",
                    onAdd = { onAddRequested(components.size) }
                )
                is Page.Existing -> ComponentEditorCard(
                    component = components[p.index],
                    index = p.index,
                    onUpdate = { onUpdateComponent(p.index, it) },
                    onDelete = { onDeleteComponent(p.index) }
                )
            }
        }

        // Right arrow/tap zone
        Box(
            Modifier
                .weight(0.1f)
                .fillMaxHeight()
                .zIndex(2f)
                .clickableWithoutRipple {
                    val next = (pagerState.currentPage + 1).coerceAtMost(pages.lastIndex)
                    if (next != pagerState.currentPage) {
                        scope.launch { pagerState.animateScrollToPage(next) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ArrowHint(left = false)
        }
    }
}

@Composable
private fun AddComponentCard(
    label: String,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) {
                    Text("Add component")
                }
            }
        }
    }
}

@Composable
private fun ComponentEditorCard(
    component: ShaftComponent,
    index: Int,
    onUpdate: (ShaftComponent) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${index + 1}. ${component.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            when (component) {
                is ShaftComponent.Thread -> ThreadFields(component, onUpdate)
                is ShaftComponent.Taper  -> TaperFields(component, onUpdate)
                is ShaftComponent.Body   -> BodyFields(component, onUpdate)
                is ShaftComponent.Liner  -> LinerFields(component, onUpdate)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete component"
                    )
                }
            }
        }
    }
}

/* ——— Field panels (simplified) ——— */

@Composable
private fun ThreadFields(
    value: ShaftComponent.Thread,
    onUpdate: (ShaftComponent.Thread) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNumberField("Major Ø (mm)", value.majorDiaMm) { onUpdate(value.copy(majorDiaMm = it)) }
        LabeledNumberField("Pitch (mm/turn)", value.pitchMm) { onUpdate(value.copy(pitchMm = it)) }
        LabeledNumberField("Length (mm)", value.lengthMm) { onUpdate(value.copy(lengthMm = it)) }
    }
}

@Composable
private fun TaperFields(value: ShaftComponent.Taper, onUpdate: (ShaftComponent.Taper) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNumberField("Start Ø (mm)", value.startDiaMm) { onUpdate(value.copy(startDiaMm = it)) }
        LabeledNumberField("End Ø (mm)", value.endDiaMm) { onUpdate(value.copy(endDiaMm = it)) }
        LabeledNumberField("Length (mm)", value.lengthMm) { onUpdate(value.copy(lengthMm = it)) }
    }
}

@Composable
private fun BodyFields(value: ShaftComponent.Body, onUpdate: (ShaftComponent.Body) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNumberField("Ø (mm)", value.diaMm) { onUpdate(value.copy(diaMm = it)) }
        LabeledNumberField("Length (mm)", value.lengthMm) { onUpdate(value.copy(lengthMm = it)) }
    }
}

@Composable
private fun LinerFields(value: ShaftComponent.Liner, onUpdate: (ShaftComponent.Liner) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledNumberField("Width (mm)", value.widthMm) { onUpdate(value.copy(widthMm = it)) }
        LabeledNumberField("Length (mm)", value.lengthMm) { onUpdate(value.copy(lengthMm = it)) }
    }
}

@Composable
private fun LabeledNumberField(label: String, initial: Float, onValue: (Float) -> Unit) {
    NumericInputField(
        label = label,
        initialText = initial.toString(),
        modifier = Modifier.fillMaxWidth(),
        allowNegative = false,
        allowFraction = true,
        parseValid = { parseFractionOrDecimal(it) != null },
        onCommit = { raw ->
            parseFractionOrDecimal(raw)?.toFloat()?.let(onValue)
        }
    )
}

/* ——— Arrow and click helper ——— */

@Composable
private fun ArrowHint(left: Boolean) {
    Text(
        text = if (left) "◀" else "▶",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** No-ripple click helper (non-composable; no remember) */
private fun Modifier.clickableWithoutRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this.clickable(
        enabled = enabled,
        indication = null,
        interactionSource = interaction,
        onClick = onClick
    )
}
