// file: app/src/main/java/com/android/shaftschematic/ui/screen/ShaftEditorRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.shaftschematic.ui.adaptive.LocalSidebarPermanent
import com.android.shaftschematic.ui.adaptive.currentWindowWidthClass
import com.android.shaftschematic.ui.adaptive.twoPane
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel

/**
 * ShaftEditorRoute
 *
 * Top-level container for the editor document views:
 * Schematic (shaft editor), Runout Sheet, Wear Document, Undercut Drawing, Final Schematic,
 * and Consolidated Output.
 *
 * ## Navigation model
 * On a COMPACT or MEDIUM window navigation is handled by [EditorSidebarOverlay], a modal
 * overlay drawer. The sidebar never displaces content — it slides in from the left and
 * overlays the content with a scrim. Content always occupies the full screen width.
 *
 * This avoids the "crushed content" problem that occurs with a persistent side rail
 * on phones, especially smaller devices.
 *
 * An EXPANDED window has the room, so the same [EditorSidebarPanel] is laid out PERMANENTLY
 * beside the tab content — no scrim, no open/close state, a tab tap just switches. The tab
 * content is composed under [LocalSidebarPermanent] `true`, which is how each tab's toolbar
 * knows to drop its hamburger: the panel is already on screen, so there is nothing to open.
 * `sidebarOpen` is still remembered and simply goes unread there, so shrinking back to one
 * pane restores the overlay exactly as it was.
 *
 * ## "Built" definition
 * The Runout, Wear, Undercut, Final Schematic, and Consolidated Output tabs are enabled once
 * the spec has ≥1 component and a non-zero OAL. If the shaft loses "built" status (all
 * components deleted) the active tab reverts to Schematic automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaftEditorRoute(
    vm: ShaftViewModel,
    onNavigateHome: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit = {},
    /**
     * Tap on the document title strip. One action for every tab — the decision between naming
     * an unsaved document and renaming a saved one belongs to the nav layer, which owns both
     * destinations; a per-tab branch here would let the same strip mean different things.
     */
    onTitleClick: (() -> Unit)? = null,
    /** Open "Duplicate for mate" — writes a sibling document; the session is untouched. */
    onDuplicateForMate: () -> Unit = {},
    /** Close the current document (guarded for unsaved work) and return to Start. */
    onCloseDocument: () -> Unit = {},
    onOpenSettings: () -> Unit,
    /** Open the Help & FAQ screen from the sidebar's tools group. */
    onOpenHelp: () -> Unit = {},
    /**
     * Open Help at one topic — the "?" button the Runout, Wear, Undercut and Consolidated
     * Output tabs carry. The same screen as [onOpenHelp], reached with its `topic` argument
     * set; a second Help channel would let the two drift apart.
     */
    onOpenHelpTopic: (String) -> Unit = {},
    onOpenDeveloperOptions: () -> Unit,
    /** Export the main shaft schematic PDF (goes to existing preview/SAF flow). */
    onExportPdf: () -> Unit,
    /**
     * Export the FINAL drawing's schematic PDF — the same preview/SAF flow, with the final
     * target carried in its route so the sheet previewed and the file written are the same
     * geometry. Defaulted to a no-op so a host that never reaches the Final tab is unaffected.
     */
    onExportFinalPdf: () -> Unit = {},
) {
    var activeTab by rememberSaveable { mutableStateOf(EditorTab.SCHEMATIC) }
    var sidebarOpen by rememberSaveable { mutableStateOf(false) }
    // Standalone shop-floor tool, reachable from every tab; the dialog owns its own blank
    // state, so all the route holds is whether it is open.
    var keywayCalcOpen by rememberSaveable { mutableStateOf(false) }
    // Same posture as the keyway calculator — a read-only tool, not a document surface.
    var taperCalcOpen by rememberSaveable { mutableStateOf(false) }
    var converterOpen by rememberSaveable { mutableStateOf(false) }

    val spec by vm.spec.collectAsState()
    val unit by vm.unit.collectAsState()
    val isBuilt = remember(spec.bodies, spec.tapers, spec.threads, spec.liners, spec.overallLengthMm) {
        (spec.bodies.isNotEmpty() || spec.tapers.isNotEmpty() ||
            spec.threads.isNotEmpty() || spec.liners.isNotEmpty()) &&
            spec.overallLengthMm > 0f
    }

    // Snap back to Schematic when shaft is no longer built
    if (!isBuilt && activeTab != EditorTab.SCHEMATIC) {
        activeTab = EditorTab.SCHEMATIC
    }

    // Retirement fallback: if WEAR_TAB_ENABLED is turned off, a restored session whose
    // active tab was Wear lands on the Runout tab.
    if (!WEAR_TAB_ENABLED && activeTab == EditorTab.WEAR) {
        activeTab = EditorTab.RUNOUT
    }

    // One adaptive decision for the whole container; see `docs/contracts/Adaptive.md`.
    val sidebarPermanent = currentWindowWidthClass().twoPane

    val onTabSelected: (EditorTab) -> Unit = { tab ->
        if (isBuilt || tab == EditorTab.SCHEMATIC) activeTab = tab
    }

    // The active document, declared once so both placements host the identical tab switch.
    val tabContent: @Composable () -> Unit = {
        when (activeTab) {
            EditorTab.SCHEMATIC -> ShaftRoute(
                vm = vm,
                onNew = onNew,
                onOpen = onOpen,
                onSave = onSave,
                onSaveAs = onSaveAs,
                onTitleClick = onTitleClick,
                onDuplicateForMate = onDuplicateForMate,
                onCloseDocument = onCloseDocument,
                onExportPdf = onExportPdf,
                onOpenSettings = onOpenSettings,
                onOpenDeveloperOptions = onOpenDeveloperOptions,
                onOpenSidebar = { sidebarOpen = true },
            )

            EditorTab.RUNOUT -> RunoutRoute(
                vm = vm,
                onOpenSidebar = { sidebarOpen = true },
                onSave = onSave,
                onTitleClick = onTitleClick,
                onOpenHelpTopic = onOpenHelpTopic,
            )

            EditorTab.WEAR -> WearRoute(
                vm = vm,
                onOpenSidebar = { sidebarOpen = true },
                onSave = onSave,
                onTitleClick = onTitleClick,
                onOpenHelpTopic = onOpenHelpTopic,
            )

            EditorTab.UNDERCUT -> UndercutRoute(
                vm = vm,
                onOpenSidebar = { sidebarOpen = true },
                onSave = onSave,
                onTitleClick = onTitleClick,
                onOpenHelpTopic = onOpenHelpTopic,
            )

            EditorTab.FINAL -> FinalRoute(
                vm = vm,
                onOpenSidebar = { sidebarOpen = true },
                onSave = onSave,
                onNew = onNew,
                onOpen = onOpen,
                onSaveAs = onSaveAs,
                onDuplicateForMate = onDuplicateForMate,
                onCloseDocument = onCloseDocument,
                onExportFinalPdf = onExportFinalPdf,
                onOpenSettings = onOpenSettings,
                onOpenDeveloperOptions = onOpenDeveloperOptions,
            )

            EditorTab.OUTPUT -> OutputRoute(
                vm = vm,
                onOpenSidebar = { sidebarOpen = true },
                onOpenRunoutTab = { activeTab = EditorTab.RUNOUT },
                onSave = onSave,
                onTitleClick = onTitleClick,
                onOpenHelpTopic = onOpenHelpTopic,
            )
        }
    }

    // Full-size Box so the sidebar can overlay the content
    Box(Modifier.fillMaxSize()) {

        if (sidebarPermanent) {
            // ── Permanent sidebar: the panel displaces the content rather than covering it ──
            Row(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(EDITOR_SIDEBAR_PERMANENT_WIDTH),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    EditorSidebarPanel(
                        selectedTab = activeTab,
                        runoutEnabled = isBuilt,
                        onTabSelected = onTabSelected,
                        onHome = onNavigateHome,
                        onSettings = onOpenSettings,
                        onKeywayCalculator = { keywayCalcOpen = true },
                        onTaperCalculator = { taperCalcOpen = true },
                        onUnitConverter = { converterOpen = true },
                        onHelp = onOpenHelp,
                        // Nothing to close — the panel stays put and the selection moves.
                    )
                }

                VerticalDivider(modifier = Modifier.fillMaxHeight())

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalSidebarPermanent provides true) {
                        tabContent()
                    }
                }
            }
        } else {
            // ── Active document (always full-width) ─────────────────────────────
            tabContent()

            // ── Overlay sidebar (modal panel when open) ─────────────────────────
            EditorSidebarOverlay(
                open = sidebarOpen,
                selectedTab = activeTab,
                runoutEnabled = isBuilt,
                onOpen = { sidebarOpen = true },
                onClose = { sidebarOpen = false },
                onTabSelected = onTabSelected,
                onHome = onNavigateHome,
                onSettings = onOpenSettings,
                onKeywayCalculator = { keywayCalcOpen = true },
                onTaperCalculator = { taperCalcOpen = true },
                onUnitConverter = { converterOpen = true },
                onHelp = onOpenHelp,
            )
        }

        if (keywayCalcOpen) {
            BoreKeywayCalcDialog(
                defaultUnit = unit,
                onDismiss = { keywayCalcOpen = false },
            )
        }

        if (taperCalcOpen) {
            TaperCalcDialog(
                defaultUnit = unit,
                onDismiss = { taperCalcOpen = false },
            )
        }

        if (converterOpen) {
            UnitConverterDialog(
                defaultUnit = unit,
                onDismiss = { converterOpen = false },
            )
        }
    }
}
