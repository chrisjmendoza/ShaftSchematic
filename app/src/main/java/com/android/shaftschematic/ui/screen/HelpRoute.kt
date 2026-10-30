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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.adaptive.readableWidth
import kotlinx.coroutines.flow.drop

/**
 * HelpRoute — in-app glossary, how-to guides, and FAQ. Reached from the editor sidebar's
 * tools group, the Start screen, the Settings list, and the "?" button on each of the
 * Runout, Wear, Undercut and Consolidated Output tabs: reference content is looked for
 * mid-job, so it keeps a top-level entry rather than living only behind Settings.
 *
 * Purpose
 * Static reference content only: no ViewModel, no state beyond which cards are expanded.
 * Topics describe CURRENT app behavior — when a behavior changes, the topic must change in
 * the same PR (same posture as the contract docs; this screen is the user-facing summary
 * of them).
 *
 * The "Glossary" section sits third, after the How-To Guides: the guides are what a reader
 * opens Help for, and the glossary is the reference behind them (on-device request). Its
 * entries define shop and app vocabulary in the wording the controls themselves use.
 *
 * The "Settings Reference" section carries that obligation control by control: every
 * user-visible control on every Settings page (main, Preview Colors, PDF Export) has an
 * entry naming it by its on-screen label, what it changes, and its default. Adding,
 * renaming, or re-defaulting a Settings control means editing its entry here in the same
 * change. Per-job controls that live on a document rather than in Settings (Shaft height,
 * liner compression, blank draft, cut-depth exaggeration) are covered by the last topic in
 * that section, so a reader who goes looking in Settings for them is told where they are.
 *
 * Search and deep links
 * The search field is pinned above the list, so a query is always reachable however far the
 * reader has scrolled. While a query is active its hits render EXPANDED — a hit that still
 * needs a tap to read is the failing state — but the query never WRITES a card's saved
 * expansion, so clearing it returns every card to the state the reader left it in.
 * [initialTopicKey] is the `topic` query argument of the `help` route: the document tabs'
 * "?" buttons open this screen on their own how-to topic, expanded and scrolled to. An
 * unknown key is ignored rather than guessed at.
 *
 * The User Manual
 * Help is the short answer; the bundled User Manual (`ManualRoute`) is the full one, and Help
 * links into it rather than repeating it. With [onOpenManual] wired, the list opens on a "User
 * manual" card, each topic with a [HelpTopic.manualAnchor] ends in "Read more in the manual"
 * (opening the manual at that section), and a search closes with "Search the user manual",
 * which opens the manual's find bar on the same words. `ManualAssetsTest` fails when an
 * anchor names a section the bundled manual no longer has. Null [onOpenManual] shows none of
 * the three.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRoute(
    onBack: () -> Unit,
    initialTopicKey: String? = null,
    onOpenManual: ((anchor: String?, find: String?) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val shown = remember(query) { filterHelpSections(helpSections, query) }
    val terms = remember(query) { helpSearchTerms(query) }
    val listState = rememberLazyListState()

    // The deep link is honoured once per key, against the UNFILTERED list — the screen opens
    // with an empty query, so the two indices agree.
    // The manual card is the list's first item, ahead of the first section header.
    val leadItems = if (onOpenManual != null) 1 else 0
    LaunchedEffect(initialTopicKey) {
        helpTopicItemIndex(helpSections, initialTopicKey)?.let { listState.scrollToItem(it + leadItems) }
    }
    // A new query opens on its first result. The list keeps its scroll anchored to the key of
    // the item at the top, so a result that survives a re-rank holds the view wherever it lands
    // and the leading results sit above the fold (on-device report). `drop(1)` skips the value
    // present at composition, so neither the deep link nor a restored query is overridden.
    LaunchedEffect(listState) {
        snapshotFlow { query }.drop(1).collect { listState.scrollToItem(0) }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                // Search field and list share the one readable column, so both stop at the
                // same width on a tablet and the field never runs wider than the cards.
                .readableWidth(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search help") },
                placeholder = { Text("Search help") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(HELP_SEARCH_TAG),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onOpenManual != null && !searching) {
                    item(key = "user_manual") {
                        ManualEntryCard(onClick = { onOpenManual(null, null) })
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "no_matches") {
                        Text(
                            "No topics match \"${query.trim()}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                shown.forEach { section ->
                    item(key = "header_${section.title}") {
                        Text(
                            rememberHighlighted(section.title, terms),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(section.topics, key = { it.key }) { topic ->
                        HelpTopicCard(
                            topic = topic,
                            searching = searching,
                            highlightTerms = terms,
                            openOnFirstShow = topic.key == initialTopicKey,
                            onReadMore = onOpenManual?.let { open ->
                                topic.manualAnchor?.let { anchor -> { open(anchor, null) } }
                            },
                        )
                    }
                }
                if (onOpenManual != null && searching) {
                    item(key = "search_manual") {
                        TextButton(
                            onClick = { onOpenManual(null, query.trim()) },
                            modifier = Modifier.testTag(HELP_SEARCH_MANUAL_TAG),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Search the user manual for \"${query.trim()}\"")
                        }
                    }
                }
            }
        }
    }
}

/** Test tag for the search field pinned above the topic list. */
const val HELP_SEARCH_TAG = "help_search"

/** Test tag for the "User manual" card at the top of the Help list. */
const val HELP_MANUAL_CARD_TAG = "help_manual_card"

/** Test tag for the "Search the user manual" button under a Help search. */
const val HELP_SEARCH_MANUAL_TAG = "help_search_manual"

/** Test tag for a topic's "Read more in the manual" link. */
const val HELP_READ_MORE_TAG = "help_read_more"

/** The card that opens the User Manual at its contents. */
@Composable
private fun ManualEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HELP_MANUAL_CARD_TAG),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("User manual", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The full illustrated manual: every control, how to read each printed " +
                        "sheet, and the glossary. Works offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Test tag for a document tab's "?" button, stable across every hosting tab. */
const val TAB_HELP_TAG = "tab_help"

/**
 * The "?" a document tab carries at the trailing edge of its toolbar, opening Help on
 * [topicKey] — that tab's own how-to guide.
 *
 * It sits in the toolbar rather than with the output actions so it never displaces the
 * Print-primary button, and one construction serves all four tabs so the icon and its
 * description cannot drift between them.
 */
@Composable
internal fun TabHelpButton(topicKey: String, onOpenHelpTopic: (String) -> Unit) {
    IconButton(
        onClick = { onOpenHelpTopic(topicKey) },
        modifier = Modifier.testTag(TAB_HELP_TAG),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "Help for this tab",
        )
    }
}

/**
 * One expandable card. [illustration] is an optional figure drawn under the body text —
 * see `HelpIllustrations.kt`. Figures elaborate; the body text must explain the topic on
 * its own, since a figure is skipped by a screen reader beyond its caption.
 *
 * [key] is the slug every reference to this topic uses — list item key, saved-expansion key,
 * and the `help` route's `topic` argument. It is derived from [title] rather than authored,
 * so the two can never disagree.
 *
 * [manualAnchor] is the id of the User Manual section that covers the topic in full; the card
 * then ends in "Read more in the manual". `ManualAssetsTest` pins every anchor to a section
 * the bundled manual still has.
 */
internal data class HelpTopic(
    val title: String,
    val body: String,
    val illustration: (@Composable () -> Unit)? = null,
    val manualAnchor: String? = null,
) {
    val key: String = helpTopicKey(title)
}

internal data class HelpSection(val title: String, val topics: List<HelpTopic>)

/**
 * @param searching       a query is active: the body shows without touching the saved
 *                        expansion, so clearing the query restores the card's own state.
 * @param openOnFirstShow this card is the deep link's target — it SEEDS the saved expansion
 *                        rather than forcing it, so the reader can still collapse it.
 * @param highlightTerms  search words to mark in the title and body ([helpSearchTerms]);
 *                        empty while not searching.
 * @param onReadMore      opens the manual at the topic's section; null shows no link.
 */
@Composable
private fun HelpTopicCard(
    topic: HelpTopic,
    searching: Boolean = false,
    highlightTerms: List<String> = emptyList(),
    openOnFirstShow: Boolean = false,
    onReadMore: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(topic.key) { mutableStateOf(openOnFirstShow) }
    val showBody = expanded || searching
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
                    rememberHighlighted(topic.title, highlightTerms),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showBody) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showBody) "Collapse" else "Expand",
                )
            }
            if (showBody) {
                HelpBodyText(topic.body, highlightTerms, modifier = Modifier.padding(top = 8.dp))
                topic.illustration?.invoke()
                if (onReadMore != null) {
                    TextButton(
                        onClick = onReadMore,
                        modifier = Modifier.testTag(HELP_READ_MORE_TAG),
                    ) {
                        Text("Read more in the manual")
                    }
                }
            }
        }
    }
}

/**
 * A topic body set as paragraphs: a blank line (`\n\n`) in the body starts a new paragraph,
 * spaced a clear gap from the last, and a single `\n` inside a paragraph (a bullet list)
 * starts a line set closer. Each line is its own Text, so a test's substring lookup must stay
 * within one line; search reads the body string and is unaffected.
 */
@Composable
private fun HelpBodyText(
    body: String,
    highlightTerms: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (paragraph in body.split("\n\n")) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (line in paragraph.split("\n")) {
                    Text(
                        rememberHighlighted(line, highlightTerms),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * [text] with every search hit ([helpHighlightRanges]) set like a highlighter stroke — bold on
 * the primary container — so a reader sees at a glance WHY a card matched. Theme roles, not
 * fixed inks: this is app chrome, never a sheet. No terms → the plain text.
 */
@Composable
private fun rememberHighlighted(text: String, terms: List<String>): AnnotatedString {
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.Bold,
    )
    return remember(text, terms, style) {
        val ranges = helpHighlightRanges(text, terms)
        if (ranges.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var at = 0
                for (range in ranges) {
                    append(text.substring(at, range.first))
                    withStyle(style) { append(text.substring(range.first, range.last + 1)) }
                    at = range.last + 1
                }
                append(text.substring(at))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

internal val helpSections: List<HelpSection> = listOf(
    HelpSection(
        "Getting Started",
        listOf(
            HelpTopic(
                "Create your first shaft",
                "From the home screen tap New Drawing. Type the overall length (OAL) and accept it " +
                    "with the green ✓ or the keyboard's Done key — the drawing takes the new " +
                    "length once it is accepted, and the red ✗ puts the old value back. On a new " +
                    "drawing the first length you accept then asks for the bare-shaft diameter, " +
                    "so the components you add sit on a shaft of the right size; Skip leaves it " +
                    "to the Body (auto) card.\n\n" +
                    "Add " +
                    "tapers, threads, liners, and bodies with the + Add Component button; each " +
                    "component becomes a card in " +
                    "the carousel where you edit its dimensions. Any span you don't cover is " +
                    "filled automatically with bare shaft (an \"auto-body\"), so the drawing " +
                    "is always continuous.",
                illustration = { AftFwdFigure() },
                manualAnchor = "editor-start",
            ),
            HelpTopic(
                "Millimeters and inches",
                "Settings → Units picks the unit every new drawing starts in, and how the open " +
                    "drawing's values are displayed and typed. Internally every " +
                    "value is stored in millimeters, so switching units never changes your " +
                    "numbers — it only changes how they are shown. Inch fields accept " +
                    "fractions like 3 1/2. Some keyboards' number pads have no / key, so while " +
                    "you type in an inch field a small grey fraction button (n over d) sits at " +
                    "the right end of the field. It types the fraction bar — tap it where the / " +
                    "goes. 10-1/4 works too.\n\n" +
                    "A value typed as a fraction reads back as that fraction, down to 1/32. " +
                    "Settings → Units → Inch fields show: Decimals shows the exact decimal " +
                    "instead (12.34375, never rounded). Printed diameters keep three decimals, " +
                    "or four for an exact sixteenth like .9375.",
                manualAnchor = "intro-units",
            ),
            HelpTopic(
                "Saving, drafts, and the asterisk",
                "Save keeps your shaft inside the app; use Back up in Settings to get files " +
                    "out. An asterisk in the title bar means unsaved changes.\n\n" +
                    "The app also " +
                    "autosaves a rolling draft ring in the background — if you leave without " +
                    "saving, the home screen offers to continue where you left off.",
                manualAnchor = "intro-saving",
            ),
            HelpTopic(
                "Undo and redo",
                "The History button in the editor's toolbar (the clock icon) holds Undo and " +
                    "Redo. Each greys out when there is nothing to step to. The Runout, Wear, " +
                    "Undercut and Consolidated Output tabs — and the wear and undercut detail " +
                    "pages — carry the same Undo and Redo as arrows in their top bar.\n\n" +
                    "A burst of quick " +
                    "edits collapses into a single undo step, so undoing after typing a value " +
                    "returns you to before you started typing rather than stepping back one " +
                    "character at a time.\n\n" +
                    "History covers the whole job — the shaft and its " +
                    "Final drawing, runout readings and bubble moves, station counts, wear " +
                    "marks, and undercuts — and lasts for the editing session; it is not saved " +
                    "with the file and starts fresh when you open a document.",
                manualAnchor = "editor-history",
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
                    "nothing.\n\n" +
                    "The Add dialog for each component shows the same controls as its " +
                    "carousel card, so anything you can set at add time you can also edit later.",
                manualAnchor = "editor-carousel",
            ),
            HelpTopic(
                "Bodies and auto-bodies",
                "Bodies are the shaft's filler stock. Spans you don't define are drawn as " +
                    "auto-bodies — derived, greyed Start/Length, and a Ø you can set on any " +
                    "auto-body card. With more than one auto section the card asks whether the " +
                    "new Ø is for \"This section\" alone or \"All auto sections\"; one section's " +
                    "Ø never changes another's. A section's own Ø goes with it when the " +
                    "component that separated it is deleted (Undo brings it back).\n\n" +
                    "Tick \"Explicit body\" on an auto-body " +
                    "card to promote it to a real, editable body; untick it on an explicit body " +
                    "to make it automatic again.\n\n" +
                    "Explicit bodies carry \"Measure From: AFT | FWD\" like tapers and liners " +
                    "(on the card and in Add Body). A body measured from FWD keeps its distance " +
                    "from the FWD end when the OAL changes; switching the chips never moves it.\n\n" +
                    "An explicit body at either end of the shaft — the coupling body — prints " +
                    "its own length as a dimension; one in the middle does not. \"Show " +
                    "dimension rail\" on its card overrides that either way.\n\n" +
                    "Adding a taper, thread, or liner over a body " +
                    "splits it; deleting one merges the fragments back.",
                manualAnchor = "editor-bodies",
            ),
            HelpTopic(
                "Component blends and seal areas",
                "A blend is a smooth machined ramp at a body end: the drawing curves out of the " +
                    "body's own face, from the neighbour's Ø into the body's Ø, instead of a " +
                    "square shoulder. Each face has its own Square | Blend chips under " +
                    "\"Component blend\" on the body card (explicit or auto) and in Add Body; " +
                    "Blend shows the length the curve runs in from the face, and one Shape — " +
                    "S-curve, Fillet or Eased cone — serves both faces. Where a liner butts a " +
                    "blended face, \"At a liner: Seat | Liner OD\" picks where the curve starts: " +
                    "halfway between the liner OD and the body Ø (Seat, the default), or the " +
                    "full liner OD for a shaft that ramps straight up to the liner.\n\n" +
                    "A seal area is the stretch at a body end the fiberglass seats into. Tick " +
                    "AFT or FWD under \"Seal areas\" and give it a length; it is measured from " +
                    "the face, whether that face is square or blended, and draws as three " +
                    "dashed cuts across the body.\n\n" +
                    "Neither prints a dimension or a footer line — the rails still dimension the " +
                    "body ends as if they were square — so a short blend is drawn wider than " +
                    "scale just so it can be seen.",
                manualAnchor = "components-blends",
            ),
            HelpTopic(
                "Tapers, keyways, and spooned ends",
                "Tapers measure from the AFT or FWD end (\"Measure From\" chips on the card and " +
                    "in the Add dialog). The rate field takes a ratio (1:12), inches per foot " +
                    "(3/4\"/ft — the shop's 1:16), or a decimal; a bare fraction like 3/4, or " +
                    "a bare decimal steeper than 0.25 like .75, is refused because it could " +
                    "mean either, and the message names the two forms to write instead. " +
                    "Rate mode Auto works the rate out from Length, S.E.T. and L.E.T.; Manual " +
                    "keeps the rate you typed — through a Length edit and when the drawing is " +
                    "reopened.\n\n" +
                    "Tapers and explicit bodies can carry a keyway (width × " +
                    "depth, length): tick \"Keyway\" on the card or in the Add dialog to show " +
                    "its fields; unticking a card that has keyway values asks before clearing " +
                    "them.\n\n" +
                    "A keyway runs out the end of its host unless you switch " +
                    "on \"Captured keyway\" — the shop term for a slot inset from the S.E.T. " +
                    "(taper) or the chosen end face (body), rounded at both ends — which " +
                    "reveals its Inset field; the footer then prints the inset.\n\n" +
                    "A spooned " +
                    "keyway adds the enlarged bowl at the " +
                    "closed end on the drawing — it is drawing-only and never changes the " +
                    "keyway's dimensions.\n\n" +
                    "With two or more keyways, clocking toggles " +
                    "(180°/90°, CW/CCW) appear on the keyway cards.",
                illustration = { SpoonedKeywayFigure() },
                manualAnchor = "components-tapers",
            ),
            HelpTopic(
                "Liners and threads",
                "Liners sleeve over the shaft and measure from the AFT or FWD end.\n\n" +
                    "Threads " +
                    "can be left out of the overall length (switch off \"Include thread in OAL\" " +
                    "on the card or in the Add dialog) — " +
                    "useful when the OAL is quoted to the shoulder; an excluded thread just " +
                    "picks which end it hangs off.\n\n" +
                    "On the card that switch asks first: Shorten OAL (or Lengthen OAL when " +
                    "switching a thread in) changes the overall length by the thread's length, " +
                    "exactly as typing it would; Keep OAL leaves the length alone. A thread " +
                    "switched in sits inside the shaft at its end. One Undo takes it all back.",
                manualAnchor = "components-liners",
            ),
            HelpTopic(
                "Coupler bolt slots and holes",
                "A coupler bolt slot is a row of radial holes drawn on the shaft. Add one " +
                    "from the add chooser (\"Coupler Bolt Slot / Hole\"), pick the hole kind, " +
                    "then set Measure From (AFT or FWD), the distance to the nearest hole " +
                    "center, hole Ø, and how many; Spacing appears once the count is more " +
                    "than one.\n\n" +
                    "Through hole is on by default — switch it off and a Depth " +
                    "field appears for a blind hole. The Add dialog warns if the row would run " +
                    "off either end of the shaft.\n\n" +
                    "• \"Seam\" — the muff-coupler cutout, half in the shaft and half in the " +
                    "coupling sleeve; drawn as a circle straddling the outline, top and " +
                    "bottom.\n" +
                    "• \"Cross-drilled\" — a bolt hole straight through the shaft, the plain " +
                    "coupling end with no taper. Enter the hole Ø and the distance from the " +
                    "end of the shaft to the hole center, and say where it sits against the " +
                    "keyway: \"90°\" (the usual case — drawn as a hidden, dashed bore across " +
                    "the shaft, since the keyway faces you) or \"In line\" (drawn as one " +
                    "circle on the centerline). The footer prints the Ø, the center distance " +
                    "and the clocking.\n\n" +
                    "The card's \"Show dimension rail\" switch prints one dimension from the end " +
                    "of the shaft the row was measured from to the nearest hole center, on the " +
                    "schematic and consolidated sheets. On paper a row is never shortened: its " +
                    "holes keep their true spacing inside a compressed body, and the body's " +
                    "S-break is placed clear of them.\n\n" +
                    "Slots are reference features: they are drawn, but they never change the " +
                    "overall length, never split a body, and never collide with anything else.",
                manualAnchor = "components-bolt-slots",
            ),
            HelpTopic(
                "Record wear readings",
                "The Wear document records liner wear areas, pit/dye-failure X marks, and " +
                    "measured diameters. Tap a liner strip to zoom in, then place marks and " +
                    "type measured Ø values. These are reference marks only — they never " +
                    "change the shaft's geometry.\n\n" +
                    "Blank mode prints a write-in template with " +
                    "the drawing but no values, for taking measurements on the job.",
                manualAnchor = "recording-wear",
            ),
            HelpTopic(
                "Record runout",
                "The Runout sheet places dial-indicator stations per component and records a " +
                    "TIR value plus a high-spot clock position per bubble. Tap a bubble to " +
                    "edit it; press and hold one to drag it along its component.\n\n" +
                    "Readings " +
                    "are reference-only and survive component edits; a reading whose station " +
                    "no longer exists is simply not drawn.\n\n" +
                    "Move mode — the pencil toggle in the preview's corner — is the quick way " +
                    "to arrange bubbles: a bubble moves the moment you touch it, a tap selects " +
                    "a bubble or a component instead of opening its reading, and the − / + " +
                    "under the preview add or remove a station on whatever is selected. " +
                    "\"Undo move\" puts the last moved bubble back; it goes away once that " +
                    "component's station count changes, since its stations are then numbered " +
                    "differently. Switch the mode off to enter readings again.\n\n" +
                    "Station counts follow length: a body gets one station per 20 in (at least " +
                    "one), a liner the same but never fewer than two (set in an inch from each " +
                    "edge, where the geometry changes and an indicator reads unreliably), and a " +
                    "taper always two; you can raise or lower the count per component, or set it " +
                    "to zero to hide that component's stations.\n\n" +
                    "Set the indicator direction on " +
                    "the sheet and it prints as \"TIR's taken looking AFT / FORWARD\" at the " +
                    "bottom, so the shop knows how to read the high-spot arrows.\n\n" +
                    "The preview stays put at the top of the tab while you scroll, so you can " +
                    "watch the bubbles move as you change a component's station count. The " +
                    "orientation setting and the Print / Preview / Export buttons sit directly " +
                    "under it; the station editor is at the bottom, since it is only needed " +
                    "when the sheet needs adjusting.",
                manualAnchor = "recording-runout",
            ),
            HelpTopic(
                "Record undercut sections",
                "The Undercut Drawing records machined-below-surface spans: distance from a " +
                    "shaft end (S.E.T.), a liner edge, or — for a cut inside another — that " +
                    "parent cut's shoulder (Parent AFT / Parent FWD), plus length and measured " +
                    "Ø. A liner edge that a cut has been machined across into the body is no " +
                    "longer offered; measure from the nearer S.E.T. instead.\n\n" +
                    "\"Add " +
                    "undercut\" asks where the cut is (a liner, or bare shaft from either " +
                    "S.E.T.) and opens that part zoomed in on a new card; or tap a liner " +
                    "or highlighted section to zoom in and add cuts.\n\n" +
                    "What you type is never " +
                    "changed: a value that would put a cut off the shaft or partly over " +
                    "another cut (sit fully inside it or fully clear) stays in the card, and " +
                    "the status pill says why it cannot be recorded yet. Tap \"Record cut\" " +
                    "on the pill to put a new cut in the record.\n\n" +
                    "Undo and Redo sit in the " +
                    "top bar, beside Preview and Drawing options — the preview of the sheet " +
                    "and its options panel, without leaving the zoomed page (the tab has the " +
                    "same pair under its drawing). Deleting a cut offers Undo — from the open cut's card and " +
                    "from the tab's list alike.\n\n" +
                    "\"Next cut\" at the foot of the card confirms the cut and " +
                    "opens a new one butted to its far end; \"More ways to place this cut\" " +
                    "below it holds the whole-liner shortcut (offered only while the liner " +
                    "has no other cut) and the \"On the AFT side of\" / \"On the FWD side " +
                    "of\" butt-against chips. How far a cut runs past its liner shows as " +
                    "a line under Length — information only, nothing to type.\n\n" +
                    "The list below the drawing " +
                    "groups the cuts under one heading per section (each liner, each " +
                    "bare-shaft area; a cut no section holds sits under \"Not on the " +
                    "shaft\") and numbers them from 1 within each section — the same numbers " +
                    "that section's zoomed detail page shows inside each cut, to help you " +
                    "find it; they never print — and flags a cut with no Ø (\"Ø not entered\"); printing or " +
                    "exporting while one has no Ø asks first (\"Print without diameters?\").\n\n" +
                    "Cut depth on the drawing " +
                    "is exaggerated for visibility (real cuts are hairline-thin at scale) — " +
                    "the printed Ø values are always your typed numbers. The \"Drawn depth\" " +
                    "slider (under \"Drawing settings\" below the list) only changes the " +
                    "drawing, never the data.\n\n" +
                    "\"Page\" (Auto | Landscape | Portrait, the " +
                    "first row of \"Drawing settings\" and in the sheet's PDF options) sets " +
                    "the printed page: Auto stacks two or more sections down a portrait page " +
                    "and prints a single section landscape.\n\n" +
                    "\"Distance to S.E.T.\" (Nearest | " +
                    "AFT S.E.T. | FWD S.E.T., right after \"Page\") chooses the S.E.T. that each " +
                    "strip's title and the short dimension at its edge measure to; \"Split " +
                    "cuts at liner edges\" sits directly under it.\n\n" +
                    "\"Sections " +
                    "to print\" (above the Print buttons when the sheet has two or more " +
                    "sections) is a checkbox per section; untick the ones you do not want on " +
                    "this printout, for example to send one liner's cuts to the welder by " +
                    "itself.\n\n" +
                    "A short cut whose value does not fit its span is set above the " +
                    "chain line, under the overall dimension, with a leader down to its span " +
                    "when the strip has room; on a crowded sheet it drops below the line.",
                manualAnchor = "recording-undercut",
            ),
            HelpTopic(
                "Export, print, and blank templates",
                "Each document (Shaft, Wear, Runout, Undercut) exports to PDF or prints " +
                    "directly from its own tab. The preview shows the actual page.\n\n" +
                    "Template " +
                    "mode prints the shaft only (no dimensions); Blank draft prints the " +
                    "drawing with empty write-in value slots.\n\n" +
                    "Drawing options sit on each " +
                    "document's PDF Options panel, and also in Settings → Drawing and " +
                    "Settings → PDF Export Options.\n\n" +
                    "The Final " +
                    "Schematic tab prints the same two ways from the shaft's finished " +
                    "geometry, and its sheets are marked Final.",
                manualAnchor = "documents-print-export",
            ),
            HelpTopic(
                "Consolidated output and Export all",
                "The Consolidated Output tab prints the schematic's dimensions, runout " +
                    "bubbles, and wear info together on one sheet — choose All three, " +
                    "Schematic + Runout, or Schematic + Wear, and preview before printing. " +
                    "Worn sections (measured areas whose Ø values print inside the profile) " +
                    "are added on this tab. Export all writes every checked document to one " +
                    "folder in a single step.\n\n" +
                    "The Shaft height slider sets the drawn shaft's " +
                    "height on paper directly, in inches, on the schematic and " +
                    "consolidated/runout sheets — $HEIGHT_FLOOR_LABEL_IN to $HEIGHT_CAP_LABEL_IN — and the Standard " +
                    "button restores the default size. That default follows a sizing curve " +
                    "you can adjust in Settings → Drawing → Default drawing size: set " +
                    "what a 4 in and an 8 in shaft draw, and sizes in between follow.\n\n" +
                    "Liner & taper compression (next to the height slider) keeps the " +
                    "measured components readable: check \"Keep liners and tapers " +
                    "proportional lengthwise\" to ask for them at true scale, or set how " +
                    "far they may shorten with the slider. Liners and tapers move " +
                    "together, so they stay in proportion with each other. The page " +
                    "balances the request — they keep as much true length as fits, and " +
                    "the runs between them always keep their relative " +
                    "lengths readable.\n\n" +
                    "Settings → Drawing → Body S-break sets how far a " +
                    "body run may be shortened before it prints the S-break symbol — set " +
                    "it to Never to hide compression entirely, or higher to mark it sooner.",
                manualAnchor = "documents-consolidated",
            ),
            HelpTopic(
                "Back up and restore",
                "Settings → Data → \"Back up all shafts\" saves every shaft as one zip " +
                    "wherever you choose (Drive, Downloads, SD card). \"Restore from backup\" " +
                    "imports a zip without ever overwriting — name collisions are renamed. " +
                    "\"Restore sample shafts\" re-adds the bundled examples.",
                manualAnchor = "help-backup",
            ),
            HelpTopic(
                "Customize colors and theme",
                "Settings → Appearance picks the app theme: System, Light, or Dark, plus a " +
                    "High contrast option for better visibility.\n\n" +
                    "Settings → Preview Colors " +
                    "styles the editor preview (outline, fills, black/white only) and the " +
                    "Undercut Drawing (shade color, intensity, or full line art). Drawing " +
                    "sheets and printed PDFs always stay white with dark ink, whatever the " +
                    "app theme.\n\n" +
                    "Every Settings field is listed in the Settings Reference " +
                    "section below.",
                manualAnchor = "settings-appearance",
            ),
        )
    ),
    HelpSection(
        "Glossary",
        listOf(
            HelpTopic(
                "AFT / FWD",
                "The two ends of the shaft. AFT is always the left end of the drawing, FWD the " +
                    "right. \"Measure From\" chips on a component pick which end its distance is " +
                    "quoted from — the same component, described from either side.",
                illustration = { AftFwdFigure() },
                manualAnchor = "intro-aft-fwd",
            ),
            HelpTopic(
                "Blank draft",
                "The write-in mode: the drawing prints with its dimension lines but the values " +
                    "left empty, so measurements can be filled in by hand on the job. It is a " +
                    "per-sheet choice that resets each session, and it never changes the data.",
                manualAnchor = "documents-blank",
            ),
            HelpTopic(
                "Body S-break",
                "The pair of curved breaks drawn through a body run, the drafting symbol for " +
                    "\"length removed here\". It appears when a run had to be shortened on paper " +
                    "so the whole shaft fits at a readable height; Settings → Drawing → \"Body " +
                    "S-break\" sets how much squeeze earns it. Dimension values always print the " +
                    "true lengths.",
                illustration = { SBreakFigure() },
                manualAnchor = "reading-sbreak",
            ),
            HelpTopic(
                "Captured keyway",
                "A keyway that does not run out the end of its host as an open slot: it is " +
                    "inset from the S.E.T. (taper) or the chosen end face (body), so both ends " +
                    "are rounded by the mill. Switch on \"Captured keyway\" on the card or in the " +
                    "Add dialog to enter the inset; the footer prints it under the KW line. A " +
                    "captured keyway cannot be spooned.",
                manualAnchor = "components-keyways",
            ),
            HelpTopic(
                "Coupling face",
                "The end view shops hand-sketch on a runout sheet, taken looking forward: the " +
                    "coupling OD, the pilot bore with its keyseat, and the bolt circle with its " +
                    "holes. Elect it onto the runout and consolidated sheets from their options; " +
                    "the pilot runout is recorded inside the bore like any other reading.",
                manualAnchor = "recording-coupling",
            ),
            HelpTopic(
                "Dual units",
                "Prints every dimension in both units — the primary value with the converted one " +
                    "beside it or stacked under it (Settings → Drawing → \"Dual-unit layout\"). " +
                    "Both terms always carry their unit, and the converted one is a courtesy " +
                    "reading, never a second measurement.",
                manualAnchor = "reading-units",
            ),
            HelpTopic(
                "Final Schematic",
                "The drawing the shaft leaves with — a second schematic of the same job, " +
                    "started as a copy of the original and then adjusted: a liner moved, " +
                    "lengthened, or shortened once the wear and undercut work is known. " +
                    "Editing it never changes the original, so the two read as the before and " +
                    "the after.\n\n" +
                    "It prints its own schematic — with runout bubbles if you " +
                    "elect them on its PDF options sheet — and a blank runout sheet to take " +
                    "the finished shaft's readings on, all marked Final; every other " +
                    "document keeps drawing the original.",
                manualAnchor = "documents-final",
            ),
            HelpTopic(
                "L.E.T. / S.E.T.",
                "Large End of Taper and Small End of Taper — the two ends of a taper, by " +
                    "diameter rather than by which way they face. Both diameters print in the " +
                    "footer for each taper, and the S.E.T. datums are what undercut and worn " +
                    "section distances can be quoted from.",
                manualAnchor = "intro-set-let",
            ),
            HelpTopic(
                "Liner & taper compression",
                "The per-job pair beside the Shaft height slider that asks for liners and " +
                    "tapers at true length: check \"Keep liners and tapers proportional " +
                    "lengthwise\", or set with the slider how far they may shorten. Both kinds " +
                    "move together, so the measured components stay in proportion with each " +
                    "other; tapers never shorten past 70% of true length whatever the slider " +
                    "says. The page keeps as much true length as fits; the request never " +
                    "changes the drawn shaft height.",
                manualAnchor = "documents-height",
            ),
            HelpTopic(
                "Measurement reference",
                "Which end printed dimensions measure from — \"Auto (closest end)\" (the " +
                    "default, anchoring each dimension to whichever end is nearer), \"AFT (force " +
                    "AFT SET)\", or \"FWD (force FWD SET)\". Same name in Settings → PDF Export " +
                    "Options and on the schematic and consolidated PDF Options sheets.",
                manualAnchor = "settings-pdf-export",
            ),
            HelpTopic(
                "OAL (overall length)",
                "The shaft's overall length, end to end, and the span every drawing is laid out " +
                    "against. Any part of it you don't cover with a component is filled with " +
                    "bare shaft (an auto-body), so the drawing is always continuous. Parts " +
                    "measured from FWD — tapers, liners, explicit bodies and bolt rows — move " +
                    "with the FWD end when it changes; a message then " +
                    "says which moved and how far, with Undo.",
                manualAnchor = "editor-oal",
            ),
            HelpTopic(
                "Runout station / bubble",
                "A station is one dial-indicator position on a component; its bubble is the " +
                    "circle drawn under the shaft where the reading is written. " +
                    "Stations are per component, not per drawn run, so a body split by liners " +
                    "still numbers straight through AFT to FWD.",
                manualAnchor = "recording-stations",
            ),
            HelpTopic(
                "Shade in Components",
                "The PDF option group that fills bodies, tapers, or liners with light grey " +
                    "instead of leaving them outlined. It is the default for each kind; a " +
                    "component card's \"Shade on drawing\" toggle overrides it for that one " +
                    "component, in either direction.",
                manualAnchor = "settings-pdf-export",
            ),
            HelpTopic(
                "Shaft height",
                "The per-job slider that sets how tall the shaft draws on paper, in inches, " +
                    "from $HEIGHT_FLOOR_LABEL_IN to $HEIGHT_CAP_LABEL_IN — shrink a long shaft to uncramp the sheet, " +
                    "or grow it for room to write in. It is a drawing size only: no diameter, " +
                    "length, or printed value changes with it, and \"Standard (…)\" returns to " +
                    "the size the Default drawing size setting picks.",
                manualAnchor = "documents-height",
            ),
            HelpTopic(
                "TIR (total indicated runout)",
                "The full sweep an indicator shows at one station — the difference between its " +
                    "high and low readings as the shaft is turned. It is typed into that " +
                    "station's bubble, with the high spot marked as a clock position; the sheet " +
                    "prints which way the indicator was read (\"TIR's taken looking AFT / " +
                    "FORWARD\").",
                manualAnchor = "recording-runout",
            ),
            HelpTopic(
                "Trace depth exaggeration",
                "Wear measured on a liner dips the drawn surface line through the measured " +
                    "diameters. Real wear is far too shallow to see at drawing scale, so the dip " +
                    "is drawn deeper than true (never shallower) — set the amount per job on the " +
                    "Wear sheet, or its default in Settings → Drawing. The printed Ø values are " +
                    "always the measured numbers you typed.",
                manualAnchor = "recording-trace",
            ),
        )
    ),
    HelpSection(
        "Settings Reference",
        listOf(
            HelpTopic(
                "Units",
                "\"Millimeters\" or \"Inches\" — default Millimeters. Every NEW drawing starts " +
                    "in this unit: a metric shop sets Millimeters once and each new drawing " +
                    "is metric. A drawing owns its unit from then on — a saved drawing keeps " +
                    "the unit it was saved in and opens in it whatever this setting says.\n\n" +
                    "While a drawing is open, the chips also switch how it is shown and " +
                    "typed: fields, badges, drawings, and PDFs.\n\n" +
                    "Nothing in the file is converted — the model is always millimeters — so " +
                    "you can switch back and forth as often as you like with no drift. Inch " +
                    "fields also accept fractions like 3 1/2 or 3-1/2, and any length can name " +
                    "its own unit: 260mm on an inch drawing is 260 millimeters.",
                manualAnchor = "settings-units",
            ),
            HelpTopic(
                "Appearance",
                "The app's color scheme. Affects app screens only — drawing sheets and " +
                    "printed PDFs are always white paper with dark ink.\n\n" +
                    "• \"System\" / \"Light\" / \"Dark\" — default Light, which is the app's " +
                    "original look. System follows the device's own dark-mode switch.\n" +
                    "• \"High contrast\" — default off. Boosts figure/ground separation for " +
                    "bright sunlight or low-vision use.",
                manualAnchor = "settings-appearance",
            ),
            HelpTopic(
                "Drawing — Component names on drawing",
                "Settings → Drawing, first row. Default on. The DEFAULT for every " +
                    "component's \"Show name on drawing\" switch: a card you haven't set " +
                    "follows it, and the line under that switch says \"Following Settings → " +
                    "Drawing → Component names\".\n\n" +
                    "A card you set yourself overrides it in " +
                    "either direction — a name switched on there prints even with this off, " +
                    "and one switched off stays hidden even with this on.\n\n" +
                    "Turn it off when " +
                    "drawings normally go out without names and you only name the odd " +
                    "section. The schematic's PDF options carry the same setting as the " +
                    "\"Component names\" chip.",
                manualAnchor = "settings-component-names",
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
                    "result live.\n\n" +
                    "No drawing ever exceeds $HEIGHT_CAP_LABEL_IN of shaft height on paper. " +
                    "\"Standard (0.5″ / 1″)\" restores the shipped pair, which is the " +
                    "proportional hand-sheet rule.\n\n" +
                    "If you set the 8 in value below the 4 in " +
                    "value a warning appears and drawings simply flatten at the 4 in height — " +
                    "a larger shaft never draws smaller than a smaller one.\n\n" +
                    "The per-job " +
                    "\"Shaft height\" slider works on top of this default.",
                manualAnchor = "settings-drawing-size",
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
                    "• Dimension values always print true lengths, whatever the drawing does.",
                manualAnchor = "settings-drawing-size",
            ),
            HelpTopic(
                "Drawing — Output font",
                "Settings → Drawing. The typeface every exported PDF is set in — dimension " +
                    "values, callouts, component names and the footer alike.\n\n" +
                    "• \"Standard\" — the default, and the historical look: the device's " +
                    "regular sans.\n" +
                    "• \"Condensed\" — a narrower sans. Values take less room, so more of them " +
                    "seat inside the dimension line instead of above it.\n" +
                    "• \"Serif\" — for shops whose paperwork is set that way.\n" +
                    "• \"Monospace\" — fixed pitch, so digits line up in a column.\n\n" +
                    "App-wide like the other Drawing settings: one choice serves every " +
                    "document, and a saved Drawing profile captures it along with the rest of " +
                    "the look. \"Restore Drawing defaults\" puts it back to Standard.\n\n" +
                    "Nothing on a sheet collides or shifts when you change it — fractions, " +
                    "dual-unit stacks and every rail break are measured from the face as it " +
                    "will actually print, not from a fixed width. A device that does not carry " +
                    "one of these families simply prints in Standard.\n\n" +
                    "App screens are unaffected; this styles the paper only.",
                manualAnchor = "settings-drawing-lines",
            ),
            HelpTopic(
                "Drawing — $WEAR_AREA_STYLE_TITLE",
                "Settings → Drawing, the last of the wear rows, and the Output tab's PDF options " +
                    "(Shading). How each measured wear area is marked on the consolidated sheet:\n\n" +
                    "None — not marked; the measured values still print.\n" +
                    "Lines — vertical lines across the wear area, the hand-drawn mark.\n" +
                    "Grey (default) — a light grey highlight over the liner; the measured " +
                    "diameters print straight on it with no white box.\n" +
                    "Both — the vertical lines over the grey, so a wear area cannot be mistaken " +
                    "for a shaded liner.\n\n" +
                    "A line that runs under a value is always interrupted at the value. The wear " +
                    "sheet keeps its own \"Wear area shade\". App-wide and captured by Drawing " +
                    "profiles; \"Restore Drawing defaults\" puts it back to Grey.",
                manualAnchor = "settings-drawing-wear",
            ),
            HelpTopic(
                "Drawing — $UNITS_NOTE_SETTING_TITLE",
                "Settings → Drawing, under \"Dual-unit layout\". Default off. On, every " +
                    "printed sheet — schematic (Final included), runout, consolidated, wear and " +
                    "undercut — carries a small \"Units in inches\" or \"Units in mm\" in its " +
                    "lower-right corner, below the drawing and the footer, so nothing on the " +
                    "sheet moves.\n\n" +
                    "It names the drawing's own unit. A component printed in the other unit, or " +
                    "the converted second value on a dual-units job, still carries its own unit " +
                    "suffix.\n\n" +
                    "App-wide and captured by Drawing profiles; \"Restore Drawing defaults\" " +
                    "turns it off. Settings only — it is not on the PDF options panel.",
                manualAnchor = "settings-drawing-lines",
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
                    "Selectable only while the arrows switch is on.",
                manualAnchor = "settings-editor",
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
                    "stay sensible in light and dark.",
                manualAnchor = "settings-preview-colors",
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
                    "intensity.\n\n" +
                    "The printed sheet has its own separate line-art setting in Settings → PDF " +
                    "Export; turning line art on here changes the screen only.",
                manualAnchor = "settings-undercut-style",
            ),
            HelpTopic(
                "PDF Export — printing and shading",
                "Settings → PDF Export Options. These apply to every document you export or " +
                    "print.\n\n" +
                    "• \"Open PDF after export\" — default off. Hands the finished file to your " +
                    "PDF viewer as soon as it is written.\n" +
                    "• \"Shade in Components\": \"Bodies\", \"Tapers\", \"Liners\" — tapers and " +
                    "liners on, bodies off by default. Fills those sections with light grey " +
                    "instead of leaving them " +
                    "outlined. \"Explicit bodies only\", under \"Bodies\", narrows the fill to " +
                    "sections you added: auto (bare-shaft) runs stay unshaded. The same group " +
                    "appears in each document's PDF Options sheet, folded behind its heading. " +
                    "On a consolidated sheet that prints Ø values inside the profile, " +
                    "that sheet shows its Liners box unchecked and greyed — the values sit on " +
                    "white halos a fill would fight — and your setting comes straight back on " +
                    "every other document.\n" +
                    "• \"Undercut drawing: line art (no shading)\" — default off. The printed " +
                    "undercut sheet drops every fill, including the detail strips' grey liner, " +
                    "and the cut sections read from their faces and floor lines. It changes " +
                    "that one document, and it is separate from the on-screen Undercut Drawing " +
                    "line-art style in Preview Colors.\n" +
                    "• \"Liner outline\" — default on, beside the line-art row. A faint dotted " +
                    "outline of each liner's original edges and outside diameter is drawn over " +
                    "the cuts, so a cut that runs from the liner into the body still shows " +
                    "where the liner was. It applies to the printed undercut sheet and the " +
                    "Undercut Drawing screens.\n" +
                    "• \"Undercut drawing: split cuts at liner edges\" — default off. The " +
                    "chain of dimensions prints each cut's full length, because the shop " +
                    "measures cut lengths. Turn it on and a cut machined past a liner edge " +
                    "is dimensioned as two spans that meet at the edge. On the undercut " +
                    "sheet's PDF options and the Undercut tab's \"Drawing settings\" it sits " +
                    "directly under \"Distance to S.E.T.\", with the other dimensioning " +
                    "choice — the same app-wide value as this Settings row.\n" +
                    "• \"Undercut drawing: total length of cuts\" — default on. A section with " +
                    "two or more cuts prints one dimension across all of them, above the cut " +
                    "lengths. This is the DEFAULT each job follows until its own \"Total length " +
                    "of cuts\" switch on the Undercut tab is used; turn it off here and every " +
                    "job you have not set yourself prints without it.",
                manualAnchor = "settings-pdf-export",
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
                    "and consolidated PDF Options sheets.",
                manualAnchor = "settings-pdf-export",
            ),
            HelpTopic(
                "Achievements",
                "• \"Enable Achievements\" — default off. Turns on the app's unlockable " +
                    "milestones.\n" +
                    "• \"View Achievements\" — opens the list. It stays greyed out, with a " +
                    "note, until achievements are enabled.",
                manualAnchor = "settings-achievements",
            ),
            HelpTopic(
                "Data — back up, restore, samples",
                "• \"Back up all shafts…\" — writes every saved shaft into one zip at a " +
                    "location you pick (Drive, Downloads, SD card).\n" +
                    "• \"Restore from backup…\" — imports shafts from a backup zip. It never " +
                    "overwrites: a name that already exists is brought in under a new name.\n" +
                    "• \"Restore sample shafts\" — re-adds the bundled examples to Saved, " +
                    "again without overwriting anything of yours.",
                manualAnchor = "settings-data",
            ),
            HelpTopic(
                "Help, About, and Developer Options",
                "• \"Help & FAQ\" — this screen. Search at the top narrows it to the topics " +
                    "that contain every word you type (\"undercuts\" finds \"undercut\" too; " +
                    "words like \"how\" and \"the\" are skipped). Topics with the word in their " +
                    "title come first, then the ones that only mention it, each category " +
                    "ordered by its best match; every match opens as you search with the " +
                    "words highlighted, and clearing the box puts the list back as it was. " +
                    "The Runout, Wear, " +
                    "Undercut, and Consolidated Output tabs each carry a \"?\" button that " +
                    "opens this screen straight at that tab's guide.\n" +
                    "• \"About ShaftSchematic\" — app version and build, plus the note that all " +
                    "geometry is stored in millimeters. Tapping App Version seven times there " +
                    "unlocks Developer Options.\n" +
                    "• \"Developer Options\" — appears in the Settings list only once " +
                    "unlocked. Debug overlays (OAL labels and helper lines, component labels, " +
                    "render layout and OAL markers) and verbose logging switches for render, " +
                    "OAL, PDF, and storage. Nothing there changes a drawing's data, and " +
                    "turning the master switch off hides the row and clears the debug flags.",
                manualAnchor = "settings-help-about",
            ),
            HelpTopic(
                "Drawing controls that live on the document",
                "Each document's PDF Options sheet mixes two kinds of control, and the sheet " +
                    "says which is which: its header notes that drawing settings are " +
                    "app-wide, and the controls saved with the job say so in their caption.\n\n" +
                    "Under the Content chips the controls are grouped into sections you open " +
                    "and close with a tap on the heading, the most used first and in the same " +
                    "order on every sheet: \"Shaft\" (open), \"Wear\" (open, wear sheet " +
                    "only), \"Runout bubbles\", \"Thread\" (only when the shaft has a thread), " +
                    "\"Lines & text\", \"Shade in Components\", \"Measurement reference\", and " +
                    "\"Units\" last. A sheet leaves out any section it has nothing for.\n\n" +
                    "Saved with this job — a reopened document prints exactly as it did:\n" +
                    "• \"Shaft height\" — on the Consolidated Output tab and in the schematic, " +
                    "runout, and wear PDF Options sheets. Sets the drawn shaft height on paper " +
                    "by value in inches, anywhere from $HEIGHT_FLOOR_LABEL_IN to $HEIGHT_CAP_LABEL_IN — shrink " +
                    "a long shaft to uncramp the sheet, or grow it for room to write in; " +
                    "\"Standard (…)\" returns to the " +
                    "size the Default drawing size setting picks. One value behind the " +
                    "schematic, runout, consolidated, and wear sheets.\n" +
                    "• \"Keep liners and tapers proportional lengthwise\" and \"Liner & taper " +
                    "compression\" — same two places. Ask for liners and tapers at true " +
                    "length, or set how far they may shorten when the page needs the room; " +
                    "both kinds move together, and the line underneath reports how much true " +
                    "length the page can actually afford. Neither ever changes the drawn " +
                    "shaft height.\n" +
                    "• \"Blank draft\" — a Content chip on each document's PDF Options sheet, " +
                    "and a chip over the schematic preview. Prints the drawing with values " +
                    "blanked for handwriting. The one exception in this group — it is " +
                    "session-only: not saved; it resets each session. On the " +
                    "schematic, \"Ø callouts\" beside it decides whether that blank sheet " +
                    "still carries Ø leaders to fill in.\n" +
                    "• \"Page\" — on the Undercut Drawing, first under \"Drawing settings\" and " +
                    "in its PDF options. Auto, Landscape or Portrait; Auto prints portrait when " +
                    "the sheet has two or more sections to print. Saved with this job.\n" +
                    "• \"Distance to S.E.T.\" — on the Undercut Drawing, right after \"Page\" and " +
                    "in its PDF options. Nearest, AFT S.E.T. or FWD S.E.T.: the S.E.T. each " +
                    "strip's title and the dimension at its edge measure to. Saved with this job.\n" +
                    "• \"Sections to print\" — on the Undercut Drawing above the Print buttons, " +
                    "and in its PDF options, when the sheet has two or more sections. Untick a " +
                    "section to leave it off the printout. Saved with this job.\n" +
                    "• \"Drawn depth\" — on the Undercut Drawing, under \"Drawing settings\". Changes only how " +
                    "deep cuts look, never the printed numbers.\n" +
                    "• \"Draw to the shaft end\" — on the Undercut Drawing, under \"Drawing " +
                    "settings\", beside the Drawn depth slider. A liner strip whose cuts end within 24 in of the S.E.T. draws " +
                    "out to the shaft end, and its dimension chain finishes with the run from the " +
                    "last cut to the S.E.T. Saved with this job.\n" +
                    "• \"Total length of cuts\" — on the Undercut Drawing, under \"Drawing " +
                    "settings\", and in its PDF options. Whether a section with two or more cuts " +
                    "prints the overall run across them above the cut lengths. Saved with this " +
                    "job; until you change it, the job follows the Settings → PDF Export default.\n" +
                    "• Worn sections — on the Consolidated Output tab. (That tab's \"Sheet " +
                    "content\" choice is not saved: it starts at All three each session.)\n\n" +
                    "App-wide — one setting, wherever you move it (also in Settings → " +
                    "Drawing):\n" +
                    "• Each document's PDF Options sheet repeats Line thickness and Shade in " +
                    "Components, on the schematic and consolidated sheets Measurement " +
                    "reference, and — on the schematic and the runout/consolidated sheets, the " +
                    "drawings that can break — Body S-break: the same app-wide settings, " +
                    "reachable without leaving the drawing.\n" +
                    "• \"Bubble size\" and \"Bubble height\" — on the runout and consolidated " +
                    "sheets. How large the runout bubbles draw and how far they hang below the " +
                    "shaft; both move the canvas markers and the printed sheet together. " +
                    "There is no Settings row for these two — they only mean something on the " +
                    "runout drawings — but they are still app-wide and a Drawing profile " +
                    "captures them.\n" +
                    "• \"Dimension arrows\" — Small / Medium / Large arrowheads on the " +
                    "dimension rails, on the schematic, consolidated and undercut sheets (the " +
                    "undercut detail page's rail too). Heads point inward unless the span is " +
                    "too narrow to hold both; on the undercut strips a larger head also needs " +
                    "more room before a value fits in its line.\n" +
                    "• \"Thread slant\" — in the Thread section of every document's sheet, " +
                    "shown whenever the shaft has a thread. How far the thread " +
                    "hatch leans: it follows each thread's own pitch, 1× being the true crest " +
                    "angle. This one applies when you let go rather than while you drag.\n" +
                    "• \"Thread density\" — beside it. How many of the thread's crests the " +
                    "hatch draws, 100% being every crest at true pitch; fine threads stay at a " +
                    "legible minimum spacing. It also applies when you let go.\n\n" +
                    "• Drag any of those sliders and the preview updates as you drag — the " +
                    "sheet stops dimming the page and the drawing reshapes under your finger, " +
                    "so there is no need to pick a value, close the sheet, look, and reopen it. " +
                    "The picture sharpens when you let go, and only then is the value saved.\n" +
                    "• The page stays visible above the menu while you adjust. Opening the " +
                    "options sheet moves the drawing into a strip across the top of the " +
                    "screen — trimmed to the drawing itself, so blank paper doesn't take up " +
                    "the room — and the menu stops just below it. Any zoom you had is reset " +
                    "so the strip is in view; close the menu and the whole page fills the " +
                    "screen again, pinch and double-tap as before.",
                manualAnchor = "settings-per-job",
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
                    "values (auto-body spans, auto taper rate text) move on their own.\n\n" +
                    "A value a field cannot use — a 0 Ø, a 2.5 bolt count, a length that " +
                    "would run into another liner — is not applied: the field goes back to " +
                    "the stored value and says \"<what you typed> not applied\" and why.",
                manualAnchor = "intro-sacred",
            ),
            HelpTopic(
                "Why don't wear marks, runout readings, or undercuts change my shaft?",
                "They are reference features: notes on top of the drawing, like pencil marks " +
                    "on a print. They never affect overall length, component positions, or " +
                    "collision checks, and deleting a component never deletes your record of " +
                    "what was measured — orphaned marks simply stop drawing.\n\n" +
                    "Coupler bolt " +
                    "slots work the same way: drawn, but never part of the length or the " +
                    "collision check.",
                manualAnchor = "intro-reference",
            ),
            HelpTopic(
                "What does the warnings banner mean?",
                "The banner above the carousel lists every problem on the shaft by the part's " +
                    "name: overlaps first (\"FWD Liner overlaps AFT Taper\" — these block " +
                    "printing), then parts that run past the FWD end or start before the AFT " +
                    "end (\"Body #1 runs 4 13/16\" past the FWD end\"), then parts shorter than " +
                    "1 mm. Tap a line to select that part; the carousel turns to its card. The " +
                    "drawing marks the same problems in red.\n\n" +
                    "A part past an end often comes from an OAL change: parts measured from FWD " +
                    "move with the FWD end, and the message after the change says which ones " +
                    "moved and how far. Its Undo puts the old length back.",
                manualAnchor = "editor-warnings",
            ),
            HelpTopic(
                "Why is Export PDF greyed out?",
                "Two reasons, and the button's message says which. Either the shaft has no " +
                    "components yet — add at least one taper, thread, liner, or body, since " +
                    "coupler bolt slots alone give nothing to dimension — or two components " +
                    "overlap: the message names them (\"Can't export: FWD Liner overlaps AFT " +
                    "Taper\"), the warnings banner lists the same pair, and a tap on that line " +
                    "takes you to the part. Fix the positions and export re-enables itself.\n\n" +
                    "Bodies never trigger this: a body " +
                    "running under a liner or up against a taper is normal, and the drawing " +
                    "trims it around them.",
                manualAnchor = "documents-blocked",
            ),
            HelpTopic(
                "Why does the undercut look deeper than its numbers?",
                "A real undercut is a few thousandths deep — at drawing scale that is " +
                    "invisible. The drawing exaggerates cut depth (up to the sheet's " +
                    "exaggeration setting, never shallower than true) so the cut can be seen " +
                    "and tapped. Printed values are always the measured numbers you typed.",
                illustration = { UndercutDepthFigure() },
                manualAnchor = "recording-cut-depth",
            ),
            HelpTopic(
                "Why does a body print with a break symbol through it?",
                "The drawing shortens long plain runs so the whole shaft fits the page at a " +
                    "readable height. When a body run is squeezed past the point where it " +
                    "still reads honestly, it prints the S-break pair — the drafting symbol " +
                    "for \"length removed here\".\n\n" +
                    "The dimension values are always the true " +
                    "lengths. Settings → Drawing → \"Body S-break\" sets how much squeeze " +
                    "earns the symbol, from Never (compression stays hidden) to Always.",
                illustration = { SBreakFigure() },
                manualAnchor = "reading-sbreak",
            ),
            HelpTopic(
                "Why did a slider on one document's PDF Options change my other documents?",
                "Because the drawing controls on those sheets are the same app-wide settings " +
                    "Settings → Drawing keeps — reached from the page they change so a line " +
                    "weight or a shade can be judged against the drawing instead of guessed " +
                    "at from a settings list. One setting, every document, so a job's sheets " +
                    "always read as one family.\n\n" +
                    "The values that belong to one job say \"Saved with this job\" in their " +
                    "caption — Shaft height, liner compression, the coupling face, the wear " +
                    "strip choices, trace depth — and those never touch another document. " +
                    "There is deliberately no per-page copy of a look setting. When a control " +
                    "genuinely needs to differ between jobs it is moved to the job instead, " +
                    "the way trace depth already works: the slider pins this job, and " +
                    "\"Save as default\" writes the app-wide setting.",
                manualAnchor = "documents-options",
            ),
            HelpTopic(
                "Why can't this auto-body host a keyway?",
                "Auto-bodies are derived filler — they can move or vanish as neighbors " +
                    "change, which would silently move or delete a keyway. Promote the span " +
                    "to an explicit body first (tick \"Explicit body\"), then add the keyway. " +
                    "A body with a keyway is protected from being split into fragments.",
                manualAnchor = "editor-bodies",
            ),
            HelpTopic(
                "Where are my files?",
                "Shafts live in the app's private storage (they survive app updates but not " +
                    "an uninstall). Use Settings → Data → Back up to write a zip anywhere, " +
                    "including cloud drives. PDFs export wherever you choose at export time.",
                manualAnchor = "help-data",
            ),
            HelpTopic(
                "Does switching units convert my numbers?",
                "No — it only changes the display. The model is always millimeters, so " +
                    "3 in shows as 76.2 mm and back with no drift, no matter how many times " +
                    "you switch.",
                manualAnchor = "intro-units",
            ),
        )
    ),
)
