package com.android.shaftschematic.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

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
