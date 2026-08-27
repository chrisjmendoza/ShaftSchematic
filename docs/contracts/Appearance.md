# Appearance — App Theme & Sheet Ink

Files: `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `ui/theme/SheetInk.kt`,
`settings/AppearancePrefs.kt`, `MainActivity.kt`; consumers: every `ui/screen` sheet canvas
Layer: UI (theme) + Settings
Version: v1.0 (2026-08-04)

## Contract

### Theme selection (Settings → Appearance)

- `AppThemeMode` (`SYSTEM` / `LIGHT` / `DARK`) + a separate **High contrast** boolean.
  Persisted in `SettingsStore` (`theme_mode`, `high_contrast`); exposed as
  `ShaftViewModel.themeMode` / `.highContrast`; collected by `MainActivity`, which wraps
  `AppNav` in `ShaftSchematicTheme(themeMode, highContrast)`.
- **Default is `LIGHT` + high contrast off**, and that combination must reproduce the app's
  historical presentation (the pre-Appearance bare `MaterialTheme` look). An app update must
  never change the app's look until the user opts in. `AppThemeMode.fromName` falls back to
  `LIGHT` on missing/corrupt values for the same reason. **One deliberate exception**, the
  caution tier below: its historical value was a baseline default nobody chose, and this clause
  guards what was DECIDED rather than freezing what was never decided.
- Four schemes: Light, Dark, High-contrast Light, High-contrast Dark (`Theme.kt`).
  High contrast = pure black/white grounds, bold containers, one strong accent per scheme
  (`HcBlue` light / `HcAmber` dark) chosen to read on that scheme's surfaces **and** on the
  white sheet canvases.

### The severity ladder: error → caution → neutral

The app runs a **three-rung** severity ladder in chrome, most visibly on the Free-to-End badge
(`ShaftPreviewPanel`): `errorContainer` when oversized → `tertiaryContainer` when snug →
`surface` when fine. Per-card component warnings (`ComponentCarousel`) and the spec-level banner
(`SpecWarningBanner`) use the same caution rung, and errors on a card use `errorContainer`.

`tertiaryContainer`/`onTertiaryContainer` therefore mean **caution**, and every scheme assigns
them (`Color.kt` → `WarnAmber*`; the high-contrast schemes reuse their own `HcBronze*` accent,
completing the `X`/`onX`/`XContainer`/`onXContainer` pattern the other three families already
follow). Before that they were the only container role left unset — including in the
high-contrast schemes, which set `primaryContainer`, `secondaryContainer` and `errorContainer`
and skipped this one — so all four schemes inherited M3's baseline `#FFD8E4`. A pale pink a few
degrees of hue from the error rung put "snug" and "oversized" at nearly the same colour on one
badge, and made an advisory banner read as an error (on-device report).

`tertiary` itself is **not** part of this ladder and must not be recoloured to match: it is the
preview-color **Bronze** preset (`PreviewColorPreset.BRONZE` → `scheme.tertiary`), and its
historical light value is pinned as `SheetInk.LinerTint`.
- **No dynamic (Material You) color.** Schemes are fixed so the preview-color presets
  (Stainless/Steel/Bronze — theme-lerped) resolve predictably. Revisit deliberately, never
  as a side effect.

### Sheet ink is theme-independent (critical invariant)

The five paper-sheet canvases — `UndercutRoute` overview, `UndercutWindowDetailOverlay`,
`WearRoute` overview, `LinerWearDetail` (`ComponentWearDetailOverlay`), `RunoutRoute`
preview — draw on a forced-white sheet (`background(Color.White)`). The Help screen's
figures (`ui/screen/HelpIllustrations.kt`) follow the same rule for the same reason: they
depict printed output, so each draws `SheetInk` on a white `Surface` in every theme, and
only the figure's frame and caption are theme-colored chrome. Their **ink** comes from
`ui/theme/SheetInk`, never from `MaterialTheme.colorScheme`:

- `SheetInk.Outline` (black) — profile outlines, rails, sheet text, hatches.
- `SheetInk.LinerTint` — liner tint on wear/runout sheets (pinned historical light tertiary).
- `SheetInk.WearRed` — wear tints/hatches/pit X's (pinned historical light error red).

Reason: in dark theme `onSurface` is near-white — theme-driven ink would print invisible
lines on the white sheet. The pins hold the exact colors the sheets always had in light
theme, so enabling dark/high-contrast themes changes app chrome only, never the drawings.
The undercut sheets' component fills are additionally user-styled via `UndercutStyle`
(see `UndercutDrawing.md`) — still fixed ink colors, never theme roles.

**Interactive affordances stay theme-driven** — tap tints, selection highlights, draft
notch outlines, badges use `colorScheme.primary`/`error`. They are UI, not ink. The
high-contrast accents were chosen to survive on white; the plain-dark accents (Purple80,
dark error) are weak-but-visible on the sheets — acceptable for an opt-in mode, pending an
on-device visual pass.

PDF output is unconditionally theme-independent (composers use fixed `android.graphics`
colors) — nothing in this contract touches it.

## Known follow-ups (deliberate, not bugs)

- Dark and high-contrast modes shipped **without an on-device visual pass** — the sheets
  are guaranteed correct by the ink pinning, but ordinary Compose chrome (cards, chips,
  dialogs across all screens) needs a walk-through before the modes are advertised.
  See `docs/SettingsCustomization_PLAN.md`.

## Do Nots

- Do not read `MaterialTheme.colorScheme` for anything drawn on a white sheet canvas.
- Do not re-introduce dynamic color casually (it breaks preset predictability).
- Do not change the default away from `LIGHT` without an explicit product decision.
