package com.android.shaftschematic.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Minimal project metadata used by the PDF footer.
 * Keep fields generic so routes can adapt easily.
 */
data class ProjectInfo(
    val customer: String = "",
    val vessel: String = "",
    val side: ShaftPosition = ShaftPosition.OTHER,
    val jobNumber: String = "",
    /**
     * Optional shaft designation ("Tail shaft", "Line shaft", …). Blank by default and
     * skipped entirely in printed headers/footers when blank — an optional field must not
     * print an orphan label. Blank-draft (write-in) sheets always rule a line for it.
     */
    val item: String = "",
    /**
     * Optional label naming WHICH drawing of the document this sheet is ("Final" for the
     * final schematic / final runout sheet). Blank by default — every existing caller — and
     * prints nothing anywhere, so untouched output stays byte-identical. Set, it prints a
     * "Drawing: <label>" line in the footer job block and the sheet header's job-info line,
     * plus a bold badge beside the Side badge on the schematic footer, so a sheet from a
     * non-original drawing can never be mistaken for one.
     */
    val drawingLabel: String = "",
    /**
     * Which date the sheet prints — see [SheetDateMode]. No print site reads it directly: every
     * site asks [printedDateOrNull], so the header, the runout header and the footer job block
     * cannot disagree.
     */
    val dateMode: SheetDateMode = SheetDateMode.TODAY,
    /** The typed date under [SheetDateMode.CUSTOM], printed verbatim; the other modes ignore it. */
    val dateText: String = "",
)

/**
 * The date a printed sheet carries, chosen per document on the start card (on-device request: the
 * day a sheet is made is not always the day the work was done).
 * - [TODAY]: the day the sheet is composed — every document saved before the choice existed.
 * - [CUSTOM]: the user's own text, printed exactly as typed (golden rule — never parsed or
 *   reformatted); a blank text prints today's date rather than an empty "Date:".
 * - [NONE]: no date at all — the "Date:" piece is left off the header line and the footer block.
 *
 * Blank drafts (write-in sheets) rule a "Date:" line whatever the mode: the draft is filled in by
 * hand. Serialized by NAME through [SheetDateModeSerializer], which decodes an unknown name to
 * [TODAY].
 */
@Serializable(with = SheetDateModeSerializer::class)
enum class SheetDateMode {
    TODAY, CUSTOM, NONE;

    companion object {
        /** Tolerant decode for a persisted name; an unknown or blank value is [TODAY]. */
        fun fromName(raw: String?): SheetDateMode =
            if (raw.isNullOrBlank()) TODAY
            else runCatching { valueOf(raw) }.getOrDefault(TODAY)
    }
}

/** Name-string serializer for [SheetDateMode] with [SheetDateMode.fromName]'s fallback. */
object SheetDateModeSerializer : KSerializer<SheetDateMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.android.shaftschematic.model.SheetDateMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SheetDateMode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): SheetDateMode =
        SheetDateMode.fromName(decoder.decodeString())
}

/**
 * The date string a printed (non-blank) sheet carries, or null when it carries none.
 *
 * The ONE resolution behind every print site — the wear/undercut header (`drawSheetHeader`), the
 * classic runout header (`drawRunoutHeader`) and the schematic/consolidated footer job block
 * (`planFooter`). [today] is the composing day's string, supplied by the caller so this stays pure.
 */
fun ProjectInfo.printedDateOrNull(today: String): String? = when (dateMode) {
    SheetDateMode.TODAY -> today
    SheetDateMode.CUSTOM -> dateText.takeIf { it.isNotBlank() } ?: today
    SheetDateMode.NONE -> null
}

/**
 * The printed sheets a job's date can be switched on or off for, one switch each (on-device
 * request: the schematic and the consolidated sheet carry the date; the runout, wear and undercut
 * sheets are working papers and usually do not). [FINAL] covers every sheet the Final tab makes —
 * the final schematic, the Original + Final sheet and the final blank runout sheet, with or
 * without bubbles.
 *
 * [printsDateByDefault] is the ONE defaults table. A job stores only its own choices, keyed by
 * enum NAME (`sheet_date_sheets` in the envelope); a sheet with no entry follows this table.
 */
enum class SheetDateSheet(val printsDateByDefault: Boolean) {
    SCHEMATIC(true),
    CONSOLIDATED(true),
    RUNOUT(false),
    WEAR(false),
    UNDERCUT(false),
    FINAL(true),
}

/**
 * Whether [sheet] prints the job's date: the job's own choice in [overrides] (keyed by enum name)
 * when it has one, else the shipped default. The ONE resolution — a name [overrides] carries that
 * is no sheet of this build is simply never asked for.
 */
fun sheetPrintsDate(sheet: SheetDateSheet, overrides: Map<String, Boolean>): Boolean =
    overrides[sheet.name] ?: sheet.printsDateByDefault

/**
 * The project information a composer of [sheet] is handed: this one while that sheet's "Date on
 * this sheet" switch is on, and a copy with the date off ([SheetDateMode.NONE]) while it is off,
 * so [printedDateOrNull] stays the single thing a print site reads. Every print, preview and
 * export site builds its composer's `ProjectInfo` through this. A blank draft rules its "Date:"
 * line either way.
 */
fun ProjectInfo.forSheet(sheet: SheetDateSheet, overrides: Map<String, Boolean>): ProjectInfo =
    if (sheetPrintsDate(sheet, overrides)) this else copy(dateMode = SheetDateMode.NONE)
