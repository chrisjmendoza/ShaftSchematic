package com.android.shaftschematic.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)

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
val WarnAmberDark = Color(0xFF4A3800)
val OnWarnAmberDark = Color(0xFFFFDFA0)

// ── Dark scheme: neutral grey ground ─────────────────────────────────────────
// The dark scheme sets every ground role itself instead of inheriting M3's baseline
// purple-black, on which the editor preview's liner and taper fills sank into the ground
// (on-device report). The ground is a neutral dark grey; the purple primary/secondary stay as the
// brand accent. Containers step up in lightness from Lowest to Highest, and the surface tint is a
// neutral grey so tonal elevation lightens a surface without turning it purple.
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
val DarkGreyOnSurfaceVariant = Color(0xFFC6C7CE)
val DarkGreyOutline = Color(0xFF9A9BA4)
val DarkGreyOutlineVariant = Color(0xFF4C4D54)
val DarkGreyInverseSurface = Color(0xFFE3E3E6)
val DarkGreyInverseOnSurface = Color(0xFF2F3033)

// Dark `tertiary` — the preview-color "Bronze" preset on the grey ground: a warm bronze that
// reads as bronze beside the neutral Steel fill. Besides that preset (and the Custom "Tertiary"
// role) only the developer dimension-debug overlay reads `tertiary`.
val DarkBronze = Color(0xFFE2B36B)
val OnDarkBronze = Color(0xFF452B00)

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
