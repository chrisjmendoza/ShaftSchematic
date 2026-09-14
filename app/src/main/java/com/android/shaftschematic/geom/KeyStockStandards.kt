// file: app/src/main/java/com/android/shaftschematic/geom/KeyStockStandards.kt
package com.android.shaftschematic.geom

import com.android.shaftschematic.util.UnitSystem

/**
 * KeyStockStandards — the standard key-stock tables behind the keyway "Standard size…" picker.
 *
 * Pure data + lookup, no Android: the picker is UI, but which sizes exist is geometry the
 * composers, cards and dialogs all read the same way.
 *
 * [KeyStockSize.depthMm] is the **shaft keyseat depth** — what the app's `keywayDepthMm` means
 * and what the machinist cuts into the shaft — NOT the key's overall height. For the square and
 * rectangular ANSI keys that is half the key height; DIN publishes it directly as `t1`.
 *
 * A pick is a user action choosing values: it writes W and D through the same commit path typing
 * them uses, and from that moment the numbers are authored and sacred (golden rule). Nothing here
 * fills a field on its own.
 */
data class KeyStockSize(
    /** Shaft Ø range, EXCLUSIVE lower bound (mm) — the standards are written "over … to". */
    val shaftDiaMinMm: Float,
    /** Shaft Ø range, INCLUSIVE upper bound (mm). */
    val shaftDiaMaxMm: Float,
    val widthMm: Float,
    /** Keyseat depth cut into the SHAFT, not the key height. */
    val depthMm: Float,
    /** Shop label, in the table's own units ("1 1/4", "1 3/4 × 1 1/2", "8 × 7"). */
    val label: String,
)

private const val MM_PER_IN_F = 25.4f

/**
 * ANSI B17.1 (inch) plain parallel keys, for keyways typed and printed in inches.
 *
 * Square keys up to 6 1/2" shaft Ø (W = H); rectangular above it. Shaft keyseat depth is half
 * the key height in both cases. Ranges are "over the low bound, to and including the high one".
 *
 * PROVISIONAL — chosen without shop input, the [LINER_SHOULDER_STD_RADII_IN] posture. Adjust
 * freely; nothing derives from these numbers except what the user picks off the menu.
 */
val KEY_STOCK_ANSI_IN: List<KeyStockSize> = listOf(
    // minIn, maxIn, widthIn, keyHeightIn, label
    ansi(5f / 16f, 7f / 16f, 3f / 32f, 3f / 32f, "3/32"),
    ansi(7f / 16f, 9f / 16f, 1f / 8f, 1f / 8f, "1/8"),
    ansi(9f / 16f, 7f / 8f, 3f / 16f, 3f / 16f, "3/16"),
    ansi(7f / 8f, 1.25f, 1f / 4f, 1f / 4f, "1/4"),
    ansi(1.25f, 1.375f, 5f / 16f, 5f / 16f, "5/16"),
    ansi(1.375f, 1.75f, 3f / 8f, 3f / 8f, "3/8"),
    ansi(1.75f, 2.25f, 1f / 2f, 1f / 2f, "1/2"),
    ansi(2.25f, 2.75f, 5f / 8f, 5f / 8f, "5/8"),
    ansi(2.75f, 3.25f, 3f / 4f, 3f / 4f, "3/4"),
    ansi(3.25f, 3.75f, 7f / 8f, 7f / 8f, "7/8"),
    ansi(3.75f, 4.5f, 1f, 1f, "1"),
    ansi(4.5f, 5.5f, 1.25f, 1.25f, "1 1/4"),
    ansi(5.5f, 6.5f, 1.5f, 1.5f, "1 1/2"),
    ansi(6.5f, 7.5f, 1.75f, 1.5f, "1 3/4 × 1 1/2"),
    ansi(7.5f, 9f, 2f, 1.5f, "2 × 1 1/2"),
    ansi(9f, 11f, 2.5f, 1.75f, "2 1/2 × 1 3/4"),
    ansi(11f, 13f, 3f, 2f, "3 × 2"),
    ansi(13f, 15f, 3.5f, 2.5f, "3 1/2 × 2 1/2"),
)

/** Inch row → canonical mm, keyseat depth = half the key height. */
private fun ansi(
    minIn: Float,
    maxIn: Float,
    widthIn: Float,
    keyHeightIn: Float,
    label: String,
): KeyStockSize = KeyStockSize(
    shaftDiaMinMm = minIn * MM_PER_IN_F,
    shaftDiaMaxMm = maxIn * MM_PER_IN_F,
    widthMm = widthIn * MM_PER_IN_F,
    depthMm = keyHeightIn / 2f * MM_PER_IN_F,
    label = label,
)

/**
 * DIN 6885-1 / ISO 773 (metric) parallel keys, for keyways typed and printed in millimetres.
 *
 * Shaft Ø ranges are "over d1, up to d2"; the key is b × h and the standard publishes the shaft
 * keyseat depth as `t1` — used directly, no halving.
 *
 * PROVISIONAL — chosen without shop input, same posture as the inch table above.
 */
val KEY_STOCK_DIN_MM: List<KeyStockSize> = listOf(
    KeyStockSize(6f, 8f, 2f, 1.2f, "2 × 2"),
    KeyStockSize(8f, 10f, 3f, 1.8f, "3 × 3"),
    KeyStockSize(10f, 12f, 4f, 2.5f, "4 × 4"),
    KeyStockSize(12f, 17f, 5f, 3.0f, "5 × 5"),
    KeyStockSize(17f, 22f, 6f, 3.5f, "6 × 6"),
    KeyStockSize(22f, 30f, 8f, 4.0f, "8 × 7"),
    KeyStockSize(30f, 38f, 10f, 5.0f, "10 × 8"),
    KeyStockSize(38f, 44f, 12f, 5.0f, "12 × 8"),
    KeyStockSize(44f, 50f, 14f, 5.5f, "14 × 9"),
    KeyStockSize(50f, 58f, 16f, 6.0f, "16 × 10"),
    KeyStockSize(58f, 65f, 18f, 7.0f, "18 × 11"),
    KeyStockSize(65f, 75f, 20f, 7.5f, "20 × 12"),
    KeyStockSize(75f, 85f, 22f, 9.0f, "22 × 14"),
    KeyStockSize(85f, 95f, 25f, 9.0f, "25 × 14"),
    KeyStockSize(95f, 110f, 28f, 10.0f, "28 × 16"),
    KeyStockSize(110f, 130f, 32f, 11.0f, "32 × 18"),
    KeyStockSize(130f, 150f, 36f, 12.0f, "36 × 20"),
    KeyStockSize(150f, 170f, 40f, 13.0f, "40 × 22"),
    KeyStockSize(170f, 200f, 45f, 15.0f, "45 × 25"),
    KeyStockSize(200f, 230f, 50f, 17.0f, "50 × 28"),
    KeyStockSize(230f, 260f, 56f, 20.0f, "56 × 32"),
    KeyStockSize(260f, 290f, 63f, 20.0f, "63 × 32"),
    KeyStockSize(290f, 330f, 70f, 22.0f, "70 × 36"),
    KeyStockSize(330f, 380f, 80f, 25.0f, "80 × 40"),
    KeyStockSize(380f, 440f, 90f, 28.0f, "90 × 45"),
    KeyStockSize(440f, 500f, 100f, 31.0f, "100 × 50"),
)

/**
 * The table the picker offers, chosen by the KEYWAY's unit (the "Keyway in" chip, resolved by
 * `DisplayUnits.keywayUnitFor`) — that is the unit the keyway is typed AND printed in, so an
 * inch keyway on a metric document still wants inch key stock.
 */
fun keyStockTable(unit: UnitSystem): List<KeyStockSize> =
    if (unit == UnitSystem.MILLIMETERS) KEY_STOCK_DIN_MM else KEY_STOCK_ANSI_IN

/**
 * The entry whose shaft-Ø range holds [shaftDiaMm] — lower bound exclusive, upper inclusive, the
 * way the standards are written. `null` for a Ø outside every range (the picker then simply
 * offers the table in order, with nothing marked suggested).
 */
fun suggestedKeyStock(table: List<KeyStockSize>, shaftDiaMm: Float): KeyStockSize? =
    table.firstOrNull { shaftDiaMm > it.shaftDiaMinMm && shaftDiaMm <= it.shaftDiaMaxMm }
