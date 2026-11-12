package com.android.shaftschematic.ui.theme

import androidx.compose.ui.graphics.Color

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ── Advisory (caution) tier ──────────────────────────────────────────────────
// The app runs a three-rung severity ladder — error / caution / neutral — and maps the caution
// rung onto `tertiaryContainer` (per-card warning chips and the advisory warnings banner step
// errorContainer → tertiaryContainer → surface). Leaving that role unassigned makes every scheme
// inherit M3's baseline `#FFD8E4` — a pale PINK sitting a few degrees of hue from
// `errorContainer`, which puts caution and error at almost the same colour and makes an advisory
// banner read as an error (on-device report).
//
// Amber instead: unmistakably caution, unmistakably not danger, and far from the app's purple
// primary. `tertiary` itself is deliberately NOT touched — it is the preview-color "Bronze"
// preset (`PreviewColorPreset.BRONZE` → `scheme.tertiary`) and its light value is pinned in
// `SheetInk.LinerTint`.
val WarnAmberLight = Color(0xFFFFE2A9)
val OnWarnAmberLight = Color(0xFF4A3200)
// Dark: a deep amber that sits just BELOW the card's lightness, so the dark ladder steps in
// lightness as well as hue — caution under the neutral card, error ([DarkErrorContainer]) above
// it. A protanope sees M3's dark error red and an amber of equal lightness as one olive.
val WarnAmberDark = Color(0xFF3E2F00)
val OnWarnAmberDark = Color(0xFFFFDFA0)

// ── Dark scheme: neutral grey ground ─────────────────────────────────────────
// The dark scheme sets every ground role itself instead of inheriting M3's baseline
// purple-black, on which the editor preview's liner and taper fills sank into the ground
// (on-device report). The ground is a neutral dark grey with a steel-blue accent. Containers step
// up in lightness from Lowest to Highest, and the surface tint is a neutral grey so tonal
// elevation lightens a surface without tinting it.
val DarkGreyBackground = Color(0xFF1F2023)
val DarkGreyOnBackground = Color(0xFFE8E8EA)
val DarkGreySurfaceDim = Color(0xFF1A1B1D)
val DarkGreySurfaceBright = Color(0xFF3A3B40)
val DarkGreySurfaceContainerLowest = Color(0xFF18191B)
val DarkGreySurfaceContainerLow = Color(0xFF232427)
val DarkGreySurfaceContainer = Color(0xFF27282C)
val DarkGreySurfaceContainerHigh = Color(0xFF303136)
val DarkGreySurfaceContainerHighest = Color(0xFF3A3B41)
val DarkGreySurfaceVariant = Color(0xFF3A3B41)
// 7:1 on the `surfaceVariant` carousel card, where every field label sits.
val DarkGreyOnSurfaceVariant = Color(0xFFCDCED5)
// The tint stays the previous onSurfaceVariant: a neutral grey, so elevation never tints.
val DarkGreySurfaceTint = Color(0xFFC6C7CE)
val DarkGreyOutline = Color(0xFF9A9BA4)
// Dividers and default chip borders: visible on the ground (2.4:1) without ruling the page.
val DarkGreyOutlineVariant = Color(0xFF5A5B62)
val DarkGreyInverseSurface = Color(0xFFE3E3E6)
val DarkGreyInverseOnSurface = Color(0xFF2F3033)

// Dark `tertiary` — the preview-color "Bronze" preset on the grey ground: a warm bronze that
// reads as bronze beside the neutral Steel fill. Besides that preset (and the Custom "Tertiary"
// role) only the developer dimension-debug overlay reads `tertiary`.
val DarkBronze = Color(0xFFE2B36B)
val OnDarkBronze = Color(0xFF452B00)

// Dark accent: a steel blue. Lavender filled buttons read as glaring slabs on the grey, and a
// cool blue sits far from every other hue the app carries meaning in — bronze liners, amber
// caution, red error — for normal and colour-deficient vision alike. Tone ~70 rather than M3's
// 80 and low in chroma, so a full-width filled button is a calm block; 7.1:1 on the ground.
val DarkSteelBlue = Color(0xFF8AAED8)
val OnDarkSteelBlue = Color(0xFF0B2848)
val DarkSteelBlueContainer = Color(0xFF2C4A6B)
val OnDarkSteelBlueContainer = Color(0xFFD6E5FA)
// The accent on the light inverse surface — the snackbar's action.
val DarkInverseSteelBlue = Color(0xFF2F5F9A)
val DarkSlate = Color(0xFFB4C1D1)
val OnDarkSlate = Color(0xFF1E2B3A)
val DarkSlateContainer = Color(0xFF34465B)
val OnDarkSlateContainer = Color(0xFFD9E5F3)

// Dark error container: a red LIGHTER than the card, so error stands above the neutral card in
// lightness as caution ([WarnAmberDark]) stands below it — the two still part under protanopia.
val DarkErrorContainer = Color(0xFFA52A22)
val OnDarkErrorContainer = Color(0xFFFFFFFF)

// The ✓ that accepts an edit, on a dark ground: the light theme's #2E7D32 is 3.2:1 there.
val AcceptGreenDark = Color(0xFF81C995)

// ── High-contrast palette ────────────────────────────────────────────────────
// Pure black/white grounds with one strong accent per scheme. The accents must read
// on BOTH the scheme's own surfaces and the paper-white sheet canvases (undercut /
// wear / runout), which stay white in every theme — that rules out white-ish accents
// in the dark scheme and pale accents in the light one.

// Light scheme accent: deep saturated blue — reads on white surfaces and the white sheet.
val HcBlue = Color(0xFF0B3EC8)

// Dark scheme accent: strong amber — reads on black surfaces and, at full saturation,
// still carries on the white sheet canvases.
val HcAmber = Color(0xFFFFC400)

// Warm accent (tertiary) per scheme — the preview-color "Bronze" preset resolves to
// tertiary, so these keep that preset legible in high contrast.
val HcBronzeDark = Color(0xFF5C3D00)
val HcBronzeBright = Color(0xFFFFB300)

val HcErrorLight = Color(0xFF9F0000)
val HcErrorDark = Color(0xFFFF5252)
