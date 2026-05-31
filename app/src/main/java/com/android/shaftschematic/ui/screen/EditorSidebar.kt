package com.android.shaftschematic.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * EditorSidebarOverlay
 *
 * A modal overlay navigation drawer for the shaft editor's three document views
 * (Schematic, Runout Sheet, Wear Document) plus Home and Settings shortcuts.
 *
 * ## Layout strategy
 * The sidebar does NOT push content. It overlays as a modal:
 *  - When [open] is false: content gets full screen width. A thin 20 dp "handle" tab
 *    on the left edge shows a `›` chevron — tapping it calls [onOpen].
 *  - When [open] is true: a semi-transparent scrim covers the full screen. The sidebar
 *    slides in from the left (200 dp wide). Tapping the scrim calls [onClose].
 *
 * This pattern avoids the content-squishing problem of a persistent side rail on phones,
 * especially on smaller devices.
 *
 * ## Status bar
 * [statusBarsPadding] is applied inside the sidebar so the top icon never crashes into
 * the phone's notification bar area.
 *
 * ## Items
 * Top group:  Home · Schematic · Runout Sheet · Wear Document
 * Bottom group: Settings
 * Runout and Wear are dimmed when [runoutEnabled] is false (shaft not yet built).
 *
 * @param open          Whether the sidebar is currently visible.
 * @param selectedTab   Which document tab is active (highlighted).
 * @param runoutEnabled Whether Runout and Wear tabs respond to taps.
 * @param onOpen        Called when the user taps the collapsed handle tab.
 * @param onClose       Called when the user taps the scrim or any active nav item.
 * @param onTabSelected Called with the newly selected [EditorTab].
 * @param onHome        Called when the user taps the Home item.
 * @param onSettings    Called when the user taps the Settings item.
 */
@Composable
fun EditorSidebarOverlay(
    open: Boolean,
    selectedTab: EditorTab,
    runoutEnabled: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTabSelected: (EditorTab) -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {

        // No persistent handle tab — the toolbar hamburger button opens the sidebar.

        // ── Modal overlay (scrim + sidebar panel) ──────────────────────────────
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(animationSpec = tween(200)) { -it },
            exit  = slideOutHorizontally(animationSpec = tween(180)) { -it },
        ) {
            Box(Modifier.fillMaxSize()) {
                // Scrim — tapping closes the sidebar
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Close navigation" },
                )

                // Sidebar panel
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(200.dp)
                        .align(Alignment.CenterStart),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()        // keeps top icon below status bar
                            .navigationBarsPadding()    // keeps Settings above system nav bar
                            .padding(vertical = 8.dp),
                    ) {
                        // ── App title ────────────────────────────────────────
                        Text(
                            text = "ShaftSchematic",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                        Spacer(Modifier.size(4.dp))

                        // ── Home ─────────────────────────────────────────────
                        NavItem(
                            icon = Icons.Filled.Home,
                            label = "Home",
                            selected = false,
                            enabled = true,
                            onClick = { onHome(); onClose() },
                        )

                        Spacer(Modifier.size(4.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.size(4.dp))

                        // ── Document tabs ─────────────────────────────────────
                        NavItem(
                            icon = Icons.Filled.Schema,
                            label = EditorTab.SCHEMATIC.label,
                            selected = selectedTab == EditorTab.SCHEMATIC,
                            enabled = true,
                            onClick = { onTabSelected(EditorTab.SCHEMATIC); onClose() },
                        )
                        NavItem(
                            icon = Icons.Filled.TrackChanges,
                            label = EditorTab.RUNOUT.label,
                            selected = selectedTab == EditorTab.RUNOUT,
                            enabled = runoutEnabled,
                            onClick = { if (runoutEnabled) { onTabSelected(EditorTab.RUNOUT); onClose() } },
                            disabledHint = "Add components first",
                        )
                        NavItem(
                            icon = Icons.Filled.Article,
                            label = EditorTab.WEAR.label,
                            selected = selectedTab == EditorTab.WEAR,
                            enabled = runoutEnabled,
                            onClick = { if (runoutEnabled) { onTabSelected(EditorTab.WEAR); onClose() } },
                            disabledHint = "Add components first",
                        )

                        Spacer(Modifier.weight(1f))

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.size(4.dp))

                        // ── Settings ──────────────────────────────────────────
                        NavItem(
                            icon = Icons.Filled.Settings,
                            label = "Settings",
                            selected = false,
                            enabled = true,
                            onClick = { onSettings(); onClose() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single navigation item row inside the sidebar.
 *
 * Selected items use the primary container background. Disabled items are dimmed
 * and show an optional [disabledHint] in a secondary text line so users know why
 * the item is not available.
 */
@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    disabledHint: String? = null,
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                  else Color.Transparent
    val iconTint = when {
        selected && enabled -> MaterialTheme.colorScheme.onPrimaryContainer
        !enabled            -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else                -> MaterialTheme.colorScheme.onSurface
    }
    val textColor = when {
        selected && enabled -> MaterialTheme.colorScheme.onPrimaryContainer
        !enabled            -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else                -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!enabled && disabledHint != null) {
                Text(
                    text = disabledHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}
