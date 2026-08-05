// file: app/src/main/java/com/android/shaftschematic/ui/screen/HelpRoute.kt
package com.android.shaftschematic.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * HelpRoute — in-app how-to guides and FAQ (Settings → Help & FAQ).
 *
 * Purpose
 * Static reference content only: no ViewModel, no state beyond which cards are expanded.
 * Topics describe CURRENT app behavior — when a behavior changes, the topic must change in
 * the same PR (same posture as the contract docs; this screen is the user-facing summary
 * of them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRoute(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & FAQ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            helpSections.forEach { section ->
                item(key = "header_${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(section.topics, key = { it.title }) { topic ->
                    HelpTopicCard(topic)
                }
            }
        }
    }
}

private data class HelpTopic(val title: String, val body: String)
private data class HelpSection(val title: String, val topics: List<HelpTopic>)

@Composable
private fun HelpTopicCard(topic: HelpTopic) {
    var expanded by rememberSaveable(topic.title) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                Text(
                    topic.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

private val helpSections: List<HelpSection> = listOf(
    HelpSection(
        "Getting Started",
        listOf(
            HelpTopic(
                "Create your first shaft",
                "From the home screen tap New. Set the overall length (OAL) or leave it in " +
                    "auto mode and let it follow the components you add. Add tapers, threads, " +
                    "liners, and bodies with the add buttons; each component becomes a card in " +
                    "the carousel where you edit its dimensions. Any span you don't cover is " +
                    "filled automatically with bare shaft (an \"auto-body\"), so the drawing " +
                    "is always continuous."
            ),
            HelpTopic(
                "Millimeters and inches",
                "Settings → Units picks how values are displayed and typed. Internally every " +
                    "value is stored in millimeters, so switching units never changes your " +
                    "numbers — it only changes how they are shown. Inch fields accept " +
                    "fractions like 3 1/2."
            ),
            HelpTopic(
                "Saving, drafts, and the asterisk",
                "Save keeps your shaft inside the app; use Back up in Settings to get files " +
                    "out. An asterisk in the title bar means unsaved changes. The app also " +
                    "autosaves a rolling draft ring in the background — if you leave without " +
                    "saving, the home screen offers to continue where you left off."
            ),
        )
    ),
    HelpSection(
        "How-To Guides",
        listOf(
            HelpTopic(
                "Edit components in the carousel",
                "Values commit when you leave a field (tap elsewhere or Done) — not on every " +
                    "keystroke. Tapping into a field and leaving without editing changes " +
                    "nothing. The Add dialog for each component shows the same controls as its " +
                    "carousel card, so anything you can set at add time you can also edit later."
            ),
            HelpTopic(
                "Bodies and auto-bodies",
                "Bodies are the shaft's filler stock. Spans you don't define are drawn as " +
                    "auto-bodies — derived, greyed Start/Length, with one shared bare-shaft Ø " +
                    "you can set on any auto-body card. Tick \"Explicit body\" on an auto-body " +
                    "card to promote it to a real, editable body; untick it on an explicit body " +
                    "to make it automatic again. Adding a taper, thread, or liner over a body " +
                    "splits it; deleting one merges the fragments back."
            ),
            HelpTopic(
                "Tapers, keyways, and spooned ends",
                "Tapers measure from the AFT or FWD end (direction chips on the card and in " +
                    "the Add dialog). Tapers and explicit bodies can carry a keyway (width × " +
                    "depth, length, offset). A spooned keyway adds the enlarged bowl at the " +
                    "closed end on the drawing — it is drawing-only and never changes the " +
                    "keyway's dimensions. With two or more keyways, clocking toggles " +
                    "(180°/90°, CW/CCW) appear on the keyway cards."
            ),
            HelpTopic(
                "Liners and threads",
                "Liners sleeve over the shaft and measure from the AFT or FWD end. Threads " +
                    "can be excluded from the overall length (\"Thread excluded from OAL\") — " +
                    "useful when the OAL is quoted to the shoulder; an excluded thread just " +
                    "picks which end it hangs off."
            ),
            HelpTopic(
                "Record wear readings",
                "The Wear document records liner wear areas, pit/dye-failure X marks, and " +
                    "measured diameters. Tap a liner strip to zoom in, then place marks and " +
                    "type measured Ø values. These are reference marks only — they never " +
                    "change the shaft's geometry. Blank mode prints a write-in template with " +
                    "the drawing but no values, for taking measurements on the job."
            ),
            HelpTopic(
                "Record runout",
                "The Runout sheet places dial-indicator stations per component and records a " +
                    "TIR value plus a high-spot clock position per bubble. Tap a bubble to " +
                    "edit it. Readings are reference-only and survive component edits; a " +
                    "reading whose station no longer exists is simply not drawn."
            ),
            HelpTopic(
                "Record undercut sections",
                "The Undercut Drawing records machined-below-surface spans: distance from a " +
                    "shaft end (S.E.T.) or a liner edge, length, and measured Ø. Tap a liner " +
                    "or highlighted section to zoom in and add cuts. Cut depth on the drawing " +
                    "is exaggerated for visibility (real cuts are hairline-thin at scale) — " +
                    "the printed Ø values are always your typed numbers. The exaggeration " +
                    "slider on the sheet only changes the drawing, never the data."
            ),
            HelpTopic(
                "Export, print, and blank templates",
                "Each document (Shaft, Wear, Runout, Undercut) exports to PDF or prints " +
                    "directly from its own tab. The preview shows the actual page. Template " +
                    "mode prints the shaft only (no dimensions); Blank draft prints the " +
                    "drawing with empty write-in value slots. PDF styling options (shading, " +
                    "tiering, titles) live in Settings → PDF Export Options."
            ),
            HelpTopic(
                "Consolidated output and Export all",
                "The Consolidated Output tab prints the schematic's dimensions, runout " +
                    "bubbles, and wear info together on one sheet — choose All three, " +
                    "Schematic + Runout, or Schematic + Wear, and preview before printing. " +
                    "Worn sections (measured areas whose Ø values print inside the profile) " +
                    "are added on this tab. Export all writes every checked document to one " +
                    "folder in a single step. The Shaft height slider sets the drawn shaft's " +
                    "height on paper directly, in inches, on the schematic and " +
                    "consolidated/runout sheets — up to 1.5 in at most — and the Standard " +
                    "button restores the default size."
            ),
            HelpTopic(
                "Back up and restore",
                "Settings → Data → \"Back up all shafts\" saves every shaft as one zip " +
                    "wherever you choose (Drive, Downloads, SD card). \"Restore from backup\" " +
                    "imports a zip without ever overwriting — name collisions are renamed. " +
                    "\"Restore sample shafts\" re-adds the bundled examples."
            ),
            HelpTopic(
                "Customize colors and theme",
                "Settings → Appearance picks the app theme: System, Light, or Dark, plus a " +
                    "High contrast option for better visibility. Settings → Preview Colors " +
                    "styles the editor preview (outline, fills, black/white only) and the " +
                    "Undercut Drawing (shade color, intensity, or full line art). Drawing " +
                    "sheets and printed PDFs always stay white with dark ink, whatever the " +
                    "app theme."
            ),
        )
    ),
    HelpSection(
        "FAQ",
        listOf(
            HelpTopic(
                "Why did my typed value stay exactly as I entered it?",
                "By design. A value you type is never rounded, snapped, or \"helpfully\" " +
                    "adjusted — down to .001. Components sit exactly where you put them, and " +
                    "the automatic bare-shaft spans absorb the consequences. Only derived " +
                    "values (auto OAL, auto-body spans, auto taper rate text) move on their own."
            ),
            HelpTopic(
                "Why is the Free-to-End badge missing?",
                "The badge hides when the shaft has no precision components (tapers, liners, " +
                    "counted threads) and isn't oversized. With only bodies, the auto-fill " +
                    "always runs to the end, so a free-to-end number would be meaningless."
            ),
            HelpTopic(
                "Why don't wear marks, runout readings, or undercuts change my shaft?",
                "They are reference features: notes on top of the drawing, like pencil marks " +
                    "on a print. They never affect overall length, component positions, or " +
                    "collision checks, and deleting a component never deletes your record of " +
                    "what was measured — orphaned marks simply stop drawing."
            ),
            HelpTopic(
                "Why does the undercut look deeper than its numbers?",
                "A real undercut is a few thousandths deep — at drawing scale that is " +
                    "invisible. The drawing exaggerates cut depth (up to the sheet's " +
                    "exaggeration setting, never shallower than true) so the cut can be seen " +
                    "and tapped. Printed values are always the measured numbers you typed."
            ),
            HelpTopic(
                "Why can't this auto-body host a keyway?",
                "Auto-bodies are derived filler — they can move or vanish as neighbors " +
                    "change, which would silently move or delete a keyway. Promote the span " +
                    "to an explicit body first (tick \"Explicit body\"), then add the keyway. " +
                    "A body with a keyway is protected from being split into fragments."
            ),
            HelpTopic(
                "Where are my files?",
                "Shafts live in the app's private storage (they survive app updates but not " +
                    "an uninstall). Use Settings → Data → Back up to write a zip anywhere, " +
                    "including cloud drives. PDFs export wherever you choose at export time."
            ),
            HelpTopic(
                "Does switching units convert my numbers?",
                "No — it only changes the display. The model is always millimeters, so " +
                    "3 in shows as 76.2 mm and back with no drift, no matter how many times " +
                    "you switch."
            ),
        )
    ),
)
