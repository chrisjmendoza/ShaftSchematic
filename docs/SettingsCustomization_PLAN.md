# Settings Customization & Accessibility — Plan

Date: 2026-08-04
Status: Phase 1 implemented (uncommitted, pending review); Phases 2+ are proposals.

Scope requested (on-device request): extend user-settings customization beyond the
existing preview "material colors" — undercut colors, a color-removal mode, app theme
colors including a high-contrast option, accessibility options, and an in-app help
section (how-tos, FAQ).

---

## 1. Shipped in Phase 1 (this change — see CHANGELOG 2026-08-04)

| Item | Where |
|---|---|
| Appearance setting: System / Light / Dark + High contrast | Settings main page; `Theme.kt`, `AppearancePrefs.kt`, `MainActivity` |
| Two hand-tuned high-contrast schemes (light: blue-on-white; dark: amber-on-black) | `Theme.kt`, `Color.kt` |
| Sheet-ink pinning — drawing sheets stay white-paper/fixed-ink in every theme | `SheetInk.kt` + the five sheet canvases |
| Undercut drawing style: shade color (Grey/Bronze/Blue), intensity (Light/Standard/Dark) | `util/UndercutStyle.kt`, Settings → Preview Colors |
| Color removal for undercuts: "Line art (no shading)" — all white, black outlines | same |
| Help & FAQ screen (3 sections, ~19 topics) | `HelpRoute.kt`, `help` nav route, Settings entry |

Defaults preserve today's exact look/behavior; everything is opt-in.

**Needs Chris's on-device pass before advertising:**
- Dark + high-contrast modes: sheets are guaranteed by ink pinning, but general chrome
  (cards, chips, dialogs, editor screens) needs a visual walk-through. Watch for any other
  screen that hard-codes a light-ish color on `surface`.
- High-contrast accent choices (`HcBlue`, `HcAmber`, bronze substitutes) are my first
  pass — pick nicer ones freely; they live in one file (`Color.kt`).
- Bronze/Blue undercut shade bases (`UndercutStyle.kt`) are subtle at Standard intensity
  by design (same ink weight as the grey). If you want them louder, either raise their
  alphas with a per-color boost or add a DARKER intensity step.

---

## 2. Considered and deferred — needs a product decision

### 2.1 Line-art / color options for the PRINTED undercut PDF
The shipped line-art mode is screen-only. Extending it to `UndercutPdfComposer` collides
with a settled product decision: detail strips **always** shade their liner (not gated on
`shadedLiners`) because the white notch voids need a shade to read against
(`docs/UndercutDrawing.md`). A printed line-art drawing would rely on the section faces +
floor lines alone. Options:
- a) PDF toggle "Line art undercut drawing" that overrides the always-shade rule (accepting
  the reduced readability), or
- b) keep print as-is (current choice), or
- c) line-art print keeps a *very* light liner shade (e.g. 10/255) as a compromise.
Recommendation: (b) until you look at a real print; then decide.

### 2.2 Color choices for wear / runout sheets
Same pattern as `UndercutStyle` would drop in cleanly (the wear sheet's liner tint and
wear-mark red are now pinned constants in `SheetInk`). Deferred because the wear document
has its own settled conventions (red = wear, bronze-ish = liner) and changing them is a
communication question, not a code question.

### 2.3 Custom RGB color picker
The preview-color system deliberately resolves theme roles/presets, not raw RGB (keeps
both themes sane). A true color-wheel picker would need: persisted ARGB per role,
contrast guardrails per theme, and a reset path. Moderate effort, low urgency — the
preset + palette system covers the practical range.

### 2.4 Material You (dynamic color)
`ShaftSchematicTheme` previously declared dynamic color but was never wired; the shipped
theme drops it deliberately so Stainless/Steel/Bronze presets resolve predictably. If
wallpaper-matched chrome is ever wanted, it should be a fifth Appearance choice
("Dynamic"), not the default, and the preset resolution needs a review against arbitrary
schemes.

### 2.5 Per-document line-thickness overrides
Global `lineThicknessScale` exists. Per-document (wear vs schematic) overrides were
considered and skipped — settings sprawl vs. one real use case so far.

---

## 3. Accessibility — audit plan (Phase 2 proposal)

High contrast (shipped) is the first step. The rest, in recommended order:

1. **Font-scale audit (cheap, high value).** The app uses `sp` via Material typography, so
   system font scaling mostly works; the risk points are fixed-height rows and the sheet
   canvases' `android.graphics.Paint` text (`textSize = 26f` raw pixels — does NOT follow
   the system setting). Decide: sheet text is "drawing ink" (fixed, like a paper print —
   my recommendation) vs. UI text (should scale). Then walk the app at 200% font scale and
   fix clipped rows (usually `Modifier.height(x)` → `heightIn(min = x)`).
2. **TalkBack / semantics pass.** Most icons have `contentDescription`s already (back
   arrows do; several `contentDescription = null` decoratives are correct). Gaps to fix:
   the drawing canvases are silent (add a summary semantics node per sheet: "Shaft
   drawing, 3 undercuts, OAL 2450 mm"), carousel cards need `semantics(mergeDescendants)`
   with a one-line summary, and the color swatch dots need labels ("Bronze swatch").
   Canvas *editing* via TalkBack (tap-to-place) is genuinely hard — offer the list rows
   (which already exist for undercuts) as the accessible path and document that.
3. **Touch-target minimums.** Material components are fine (48dp); custom hit-tests on
   canvases use ~12dp pads — consider widening the tap pad when
   `LocalAccessibilityManager` reports touch exploration, or simply widen globally.
4. **Color-independence.** Wear marks are red-only signals in places; the X/hatch shapes
   already carry the meaning, which is the right pattern — verify every state that is
   color-only also has a shape/text channel (the runout bubble editor and status pill
   look OK: filled/label differences exist).
5. **Reduced motion.** Only trivial animations exist (`animateContentSize` in Help,
   carousel snaps); low priority.

None of these change model/geometry code; all are UI-layer. Suggested slice: (1) alone,
then (2) as its own branch with a TalkBack walk-through.

---

## 4. Help section — growth path

Shipped: static expandable topics (Getting Started / How-To / FAQ) in `HelpRoute.kt`,
maintained next to the code it describes (contract: behavior change ⇒ update the topic in
the same PR — noted in `Navigation.md`).

Possible next steps, in rough order of value:
- **Search box** filtering topics by substring (trivial, all content is in memory).
- **Deep links from context**: a small "?" icon on complex screens (undercut editor,
  runout editor) opening HelpRoute scrolled to the matching topic (pass topic key as nav
  arg).
- **Images/diagrams**: worth it for the undercut reference conventions; would move
  content to drawable resources — only do once content stabilizes.
- **"What's new" panel** fed from CHANGELOG at version bump.
- Localization: all help text is inline English; if the app ever localizes, this screen
  is the biggest string surface — keep that in mind before growing it much further.

---

## 5. Settings screen structure (observation)

The main settings page is approaching the length where grouping pages pay off. Current
sub-pages: Preview Colors, PDF Export. If Phase 2 adds accessibility toggles, consider a
third sub-page ("Accessibility") rather than more main-page rows, and possibly moving the
Appearance block there too. No action taken — layout preference is yours.
