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
 *
 * The "Settings Reference" section carries that obligation control by control: every
 * user-visible control on every Settings page (main, Preview Colors, PDF Export) has an
 * entry naming it by its on-screen label, what it changes, and its default. Adding,
 * renaming, or re-defaulting a Settings control means editing its entry here in the same
 * change. Per-job controls that live on a document rather than in Settings (Shaft height,
 * liner compression, blank draft, cut-depth exaggeration) are covered by the last topic in
 * that section, so a reader who goes looking in Settings for them is told where they are.
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

/**
 * One expandable card. [illustration] is an optional figure drawn under the body text —
 * see `HelpIllustrations.kt`. Figures elaborate; the body text must explain the topic on
 * its own, since a figure is skipped by a screen reader beyond its caption.
 */
private data class HelpTopic(
    val title: String,
    val body: String,
    val illustration: (@Composable () -> Unit)? = null,
)

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
                topic.illustration?.invoke()
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
                    "is always continuous.",
                illustration = { AftFwdFigure() },
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
            HelpTopic(
                "Undo and redo",
                "The History button in the editor's toolbar (the clock icon) holds Undo and " +
                    "Redo. Each greys out when there is nothing to step to. A burst of quick " +
                    "edits collapses into a single undo step, so undoing after typing a value " +
                    "returns you to before you started typing rather than stepping back one " +
                    "character at a time. History covers the shaft itself — components, " +
                    "lengths, keyways — and lasts for the editing session; it is not saved " +
                    "with the file and starts fresh when you open a document."
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
                    "(180°/90°, CW/CCW) appear on the keyway cards.",
                illustration = { SpoonedKeywayFigure() },
            ),
            HelpTopic(
                "Liners and threads",
                "Liners sleeve over the shaft and measure from the AFT or FWD end. Threads " +
                    "can be excluded from the overall length (\"Thread excluded from OAL\") — " +
                    "useful when the OAL is quoted to the shoulder; an excluded thread just " +
                    "picks which end it hangs off."
            ),
            HelpTopic(
                "Coupler bolt slots",
                "A coupler bolt slot is a row of radial cutouts drawn on the shaft. Add one " +
                    "from the add chooser, then set Measure From (AFT or FWD), the distance to " +
                    "the first slot, hole Ø, and how many; Spacing appears once the count is " +
                    "more than one. Through hole is on by default — switch it off and a Depth " +
                    "field appears for a blind hole. The Add dialog warns if the row would run " +
                    "off either end of the shaft.\n\n" +
                    "Slots are reference features: they are drawn, but they never change the " +
                    "overall length, never split a body, and never collide with anything else. " +
                    "Their card carries a \"show dimension rail\" toggle if you want the row " +
                    "dimensioned on the printed schematic."
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
                    "reading whose station no longer exists is simply not drawn.\n\n" +
                    "Stations default to two per taper and liner (set in an inch from each " +
                    "edge, where the geometry changes and an indicator reads unreliably) and " +
                    "three per body; you can raise or lower the count per component, or set it " +
                    "to zero to hide that component's stations. Set the indicator direction on " +
                    "the sheet and it prints as \"TIR's taken looking AFT / FORWARD\" at the " +
                    "bottom, so the shop knows how to read the high-spot arrows.\n\n" +
                    "The preview stays put at the top of the tab while you scroll, so you can " +
                    "watch the bubbles move as you change a component's station count. The " +
                    "orientation setting and the Preview / Print / Export buttons sit directly " +
                    "under it; the station editor is at the bottom, since it is only needed " +
                    "when the sheet needs adjusting."
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
                    "consolidated/runout sheets — $HEIGHT_FLOOR_LABEL_IN to $HEIGHT_CAP_LABEL_IN — and the Standard " +
                    "button restores the default size. That default follows a sizing curve " +
                    "you can adjust in Settings → Drawing → Default drawing size: set " +
                    "what a 4 in and an 8 in shaft draw, and sizes in between follow. " +
                    "Liner compression (next to the height slider) keeps the measured " +
                    "components readable: check \"Keep liners proportional lengthwise\" to " +
                    "ask for liners at true scale, or set how far they may shorten with " +
                    "the slider. The page balances the request — liners keep as much true " +
                    "length as fits, and the runs between them always keep their relative " +
                    "lengths readable. Settings → Drawing → Body S-break sets how far a " +
                    "body run may be shortened before it prints the S-break symbol — set " +
                    "it to Never to hide compression entirely, or higher to mark it sooner."
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
                    "app theme. Every Settings field is listed in the Settings Reference " +
                    "section below."
            ),
        )
    ),
    HelpSection(
        "Settings Reference",
        listOf(
            HelpTopic(
                "Units",
                "\"Millimeters\" or \"Inches\" — default Millimeters. Picks how every value is " +
                    "shown and typed throughout the app: fields, badges, drawings, and PDFs. " +
                    "Nothing in the file is converted — the model is always millimeters — so " +
                    "you can switch back and forth as often as you like with no drift. Inch " +
                    "fields also accept fractions like 3 1/2."
            ),
            HelpTopic(
                "Appearance",
                "The app's color scheme. Affects app screens only — drawing sheets and " +
                    "printed PDFs are always white paper with dark ink.\n\n" +
                    "• \"System\" / \"Light\" / \"Dark\" — default Light, which is the app's " +
                    "original look. System follows the device's own dark-mode switch.\n" +
                    "• \"High contrast\" — default off. Boosts figure/ground separation for " +
                    "bright sunlight or low-vision use."
            ),
            HelpTopic(
                "Drawing — Default drawing size",
                "Settings → Drawing. How tall the shaft prints on paper before any per-job " +
                    "adjustment. Two sliders set the sizing line, each adjustable from 0.25 " +
                    "in to $HEIGHT_CAP_LABEL_IN in 1/16 in steps:\n\n" +
                    "• \"4″ shaft draws\" — default 0.5 in.\n" +
                    "• \"8″ shaft draws\" — default 1 in.\n\n" +
                    "Every other diameter follows the straight line through those two points, " +
                    "so a 6 in shaft lands halfway; the line under the sliders shows that " +
                    "result live. No drawing ever exceeds $HEIGHT_CAP_LABEL_IN of shaft height on paper. " +
                    "\"Standard (0.5″ / 1″)\" restores the shipped pair, which is the " +
                    "proportional hand-sheet rule. If you set the 8 in value below the 4 in " +
                    "value a warning appears and drawings simply flatten at the 4 in height — " +
                    "a larger shaft never draws smaller than a smaller one. The per-job " +
                    "\"Shaft height\" slider works on top of this default."
            ),
            HelpTopic(
                "Drawing — Body S-break",
                "Settings → Drawing. How much a body run must be squeezed before it prints " +
                    "the S-break symbol — the pair of curved breaks that says \"length " +
                    "removed here\".\n\n" +
                    "• Slider from \"Never\" to \"Always\" in 5% steps, default 50%, with a " +
                    "\"Default (50%)\" button. The readout reads \"Never\" at the low end and " +
                    "\"below 50%\" (or whatever you pick) elsewhere.\n" +
                    "• At the default, a body that draws shorter than half its true length " +
                    "gets the symbol. Drag left to mark only heavier compression, right to " +
                    "mark even mild compression, or all the way to Never to keep compression " +
                    "hidden entirely.\n" +
                    "• Bodies only — liners and tapers always foreshorten silently.\n" +
                    "• A genuinely long run (about 3 in of paper at true scale) still prints " +
                    "its break at every setting, Never included.\n" +
                    "• The same slider sits on the PDF Options sheet of the schematic and " +
                    "runout/consolidated previews, so the threshold can be set against the " +
                    "drawing it changes — one setting, wherever you move it.\n" +
                    "• Dimension values always print true lengths, whatever the drawing does."
            ),
            HelpTopic(
                "Editor Screen",
                "Presentation of the editor and its preview.\n\n" +
                    "• \"Line Thickness\" — default 100%. Slider from 50% to 200%, a typed % " +
                    "box beside it, and a \"Default (100%)\" button. This one applies to both " +
                    "the on-screen preview and every printed PDF: 100% is the standard thin " +
                    "weight, 200% the heavier original weight. Releasing the slider near 100% " +
                    "snaps exactly to it; a number you type is used exactly as typed. The same " +
                    "slider also sits in each document's PDF Options sheet — one setting, two " +
                    "places to reach it.\n" +
                    "• \"Preview Colors\" — opens the preview color page (see below).\n" +
                    "• \"Show Grid in Preview\" — default off. Draws a background grid behind " +
                    "the shaft in the editor preview. Preview only; never printed.\n" +
                    "• \"Highlight Selected Component in Preview\" — default on. Emphasizes the " +
                    "component whose card you are editing.\n" +
                    "• \"Show Left/Right Arrows on Component Cards\" — default off. Adds arrow " +
                    "buttons to the carousel cards for stepping between components.\n" +
                    "• \"Small\" / \"Medium\" / \"Large\" — arrow size, default Medium. " +
                    "Selectable only while the arrows switch is on."
            ),
            HelpTopic(
                "Preview Colors",
                "Styles the editor preview only — the PDF has its own fixed drawing colors.\n\n" +
                    "• \"Black/White Only\" — default off. Turns every fill off and forces " +
                    "outlines to black; the color rows below are disabled while it is on.\n" +
                    "• Six color rows: \"Outline\" (default Steel), \"Body Fill\" " +
                    "(Transparent), \"Taper Fill\" (Steel), \"Liner Fill\" (Bronze), \"Thread " +
                    "Fill\" (Transparent), \"Thread Hatch\" (Steel). Each row's button offers " +
                    "Stainless, Steel, Bronze, Transparent, or Custom.\n" +
                    "• Choosing Custom adds a \"Palette\" button for picking the exact shade " +
                    "(Monochrome, Primary, Secondary, Tertiary, Surface Variant, Outline, On " +
                    "Surface, Error, or Transparent). Presets follow the app theme, so they " +
                    "stay sensible in light and dark."
            ),
            HelpTopic(
                "Undercut Drawing style",
                "At the bottom of the Preview Colors page. Styles the on-screen Undercut " +
                    "Drawing; the printed undercut PDF keeps standard drawing colors.\n\n" +
                    "• \"Line art (no shading)\" — default off. Everything draws white with " +
                    "black outlines only; the two controls below are disabled while it is on.\n" +
                    "• \"Shade color\" — Grey (default), Bronze, or Blue.\n" +
                    "• \"Shade intensity\" — Light, Standard (default), or Dark. The undercut " +
                    "section's core always shades one step lighter than the liner, at every " +
                    "intensity."
            ),
            HelpTopic(
                "PDF Export — printing and shading",
                "Settings → PDF Export Options. These apply to every document you export or " +
                    "print.\n\n" +
                    "• \"Open PDF after export\" — default off. Hands the finished file to your " +
                    "PDF viewer as soon as it is written.\n" +
                    "• \"Show component titles in PDF\" — default on. The DEFAULT for " +
                    "component names on the schematic: components you haven't decided about " +
                    "follow it. Each card's \"Show name on drawing\" toggle overrides it for " +
                    "that one component, in either direction — a name switched on there " +
                    "prints even with this off, and one switched off stays hidden even with " +
                    "this on.\n" +
                    "• \"Shade in Components\": \"Bodies\", \"Tapers\", \"Liners\" — all off by " +
                    "default. Fills those sections with light grey instead of leaving them " +
                    "outlined. \"Explicit bodies only\", under \"Bodies\", narrows the fill to " +
                    "sections you added: auto (bare-shaft) runs stay unshaded. The same group " +
                    "appears in each document's PDF Options sheet, folded behind its heading. " +
                    "On a consolidated sheet that prints Ø values inside the profile, " +
                    "that sheet shows its Liners box unchecked and greyed — the values sit on " +
                    "white halos a fill would fight — and your setting comes straight back on " +
                    "every other document."
            ),
            HelpTopic(
                "PDF Export — Template mode and dimension reference",
                "• \"Template (shaft only)\" — default off. Prints the shaft outline with no " +
                    "dimensions, for marking up by hand. (Blank draft, the other write-in " +
                    "mode, keeps the dimension lines and empties the values; it lives on each " +
                    "document's preview, not here.)\n" +
                    "• \"Measurement reference\" — \"Auto (closest end)\" (default), " +
                    "\"AFT (force AFT SET)\", or \"FWD (force FWD SET)\". Picks which end " +
                    "printed dimensions measure from. Auto anchors each dimension to whichever " +
                    "end is closer. The same choice appears under that name in the schematic " +
                    "and consolidated PDF Options sheets."
            ),
            HelpTopic(
                "Achievements",
                "• \"Enable Achievements\" — default off. Turns on the app's unlockable " +
                    "milestones.\n" +
                    "• \"View Achievements\" — opens the list. It stays greyed out, with a " +
                    "note, until achievements are enabled."
            ),
            HelpTopic(
                "Data — back up, restore, samples",
                "• \"Back up all shafts…\" — writes every saved shaft into one zip at a " +
                    "location you pick (Drive, Downloads, SD card).\n" +
                    "• \"Restore from backup…\" — imports shafts from a backup zip. It never " +
                    "overwrites: a name that already exists is brought in under a new name.\n" +
                    "• \"Restore sample shafts\" — re-adds the bundled examples to Saved, " +
                    "again without overwriting anything of yours."
            ),
            HelpTopic(
                "Help, About, and Developer Options",
                "• \"Help & FAQ\" — this screen.\n" +
                    "• \"About ShaftSchematic\" — app version and build, plus the note that all " +
                    "geometry is stored in millimeters. Tapping App Version seven times there " +
                    "unlocks Developer Options.\n" +
                    "• \"Developer Options\" — appears in the Settings list only once " +
                    "unlocked. Debug overlays (OAL labels and helper lines, component labels, " +
                    "render layout and OAL markers) and verbose logging switches for render, " +
                    "OAL, PDF, and storage. Nothing there changes a drawing's data, and " +
                    "turning the master switch off hides the row and clears the debug flags."
            ),
            HelpTopic(
                "Drawing controls that live on the document",
                "These are saved with the job rather than in Settings, so a reopened document " +
                    "prints exactly as it did.\n\n" +
                    "• \"Shaft height\" — on the Consolidated Output tab and in the schematic, " +
                    "runout, and wear PDF Options sheets. Sets the drawn shaft height on paper " +
                    "by value in inches, anywhere from $HEIGHT_FLOOR_LABEL_IN to $HEIGHT_CAP_LABEL_IN — shrink " +
                    "a long shaft to uncramp the sheet, or grow it for room to write in; " +
                    "\"Standard (…)\" returns to the " +
                    "size the Default drawing size setting picks. One value behind the " +
                    "schematic, runout, consolidated, and wear sheets.\n" +
                    "• \"Keep liners proportional lengthwise\" and \"Liner compression\" — same " +
                    "two places. Ask for liners at true length, or set how far they may " +
                    "shorten when the page needs the room; the line underneath reports how " +
                    "much true length the page can actually afford. Neither ever changes the " +
                    "drawn shaft height.\n" +
                    "• \"Blank draft\" — a Content chip on each document's PDF Options sheet, " +
                    "and a chip over the schematic preview. Prints the drawing with values " +
                    "blanked for handwriting. Not saved; it resets each session. On the " +
                    "schematic, \"Ø callouts\" beside it decides whether that blank sheet " +
                    "still carries Ø leaders to fill in.\n" +
                    "• \"Cut depth exaggeration\" — on the Undercut Drawing. Changes only how " +
                    "deep cuts look, never the printed numbers.\n" +
                    "• \"Sheet content\", worn sections, and \"Export all\" — on the " +
                    "Consolidated Output tab.\n" +
                    "• Each document's PDF Options sheet repeats Line thickness and Shade in " +
                    "Components, on the schematic and consolidated sheets Measurement " +
                    "reference, and — on the schematic and the runout/consolidated sheets, the " +
                    "drawings that can break — Body S-break: the same app-wide settings, " +
                    "reachable without leaving the drawing.\n" +
                    "• \"Bubble size\" and \"Bubble height\" — on the runout and consolidated " +
                    "sheets. How large the runout bubbles draw and how far they hang below the " +
                    "shaft; both move the canvas markers and the printed sheet together.\n" +
                    "• \"Dimension arrows\" — Small / Medium / Large arrowheads on the " +
                    "dimension rails, on the schematic and consolidated sheets. Heads point " +
                    "inward unless the span is too narrow to hold both.\n" +
                    "• Drag any of those sliders and the preview updates as you drag — the " +
                    "sheet stops dimming the page and the drawing reshapes under your finger, " +
                    "so there is no need to pick a value, close the sheet, look, and reopen it. " +
                    "The picture sharpens when you let go, and only then is the value saved.\n" +
                    "• The page stays visible above the menu while you adjust. Opening the " +
                    "options sheet moves the drawing into a strip across the top of the " +
                    "screen — trimmed to the drawing itself, so blank paper doesn't take up " +
                    "the room — and the menu stops just below it. Any zoom you had is reset " +
                    "so the strip is in view; close the menu and the whole page fills the " +
                    "screen again, pinch and double-tap as before."
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
                "Why don't wear marks, runout readings, or undercuts change my shaft?",
                "They are reference features: notes on top of the drawing, like pencil marks " +
                    "on a print. They never affect overall length, component positions, or " +
                    "collision checks, and deleting a component never deletes your record of " +
                    "what was measured — orphaned marks simply stop drawing. Coupler bolt " +
                    "slots work the same way: drawn, but never part of the length or the " +
                    "collision check."
            ),
            HelpTopic(
                "Why is Export PDF greyed out?",
                "Two reasons, and the button's message says which. Either the shaft has no " +
                    "components yet — add at least one taper, thread, liner, or body, since " +
                    "coupler bolt slots alone give nothing to dimension — or two components " +
                    "overlap. Overlapping cards are flagged in the carousel; fix the " +
                    "positions and export re-enables itself. Bodies never trigger this: a body " +
                    "running under a liner or up against a taper is normal, and the drawing " +
                    "trims it around them."
            ),
            HelpTopic(
                "Why does the undercut look deeper than its numbers?",
                "A real undercut is a few thousandths deep — at drawing scale that is " +
                    "invisible. The drawing exaggerates cut depth (up to the sheet's " +
                    "exaggeration setting, never shallower than true) so the cut can be seen " +
                    "and tapped. Printed values are always the measured numbers you typed.",
                illustration = { UndercutDepthFigure() },
            ),
            HelpTopic(
                "Why does a body print with a break symbol through it?",
                "The drawing shortens long plain runs so the whole shaft fits the page at a " +
                    "readable height. When a body run is squeezed past the point where it " +
                    "still reads honestly, it prints the S-break pair — the drafting symbol " +
                    "for \"length removed here\". The dimension values are always the true " +
                    "lengths. Settings → Drawing → \"Body S-break\" sets how much squeeze " +
                    "earns the symbol, from Never (compression stays hidden) to Always.",
                illustration = { SBreakFigure() },
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
