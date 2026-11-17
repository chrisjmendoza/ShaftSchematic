package com.android.shaftschematic.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One section sheet of a job: a stretch of the shaft printed alone, as its own document — the
 * shop's "FWD end finish" sketch (on-device request).
 *
 * Print metadata, never geometry: a section names the components it runs between and nothing
 * else, so it reaches neither the overall length, resolution nor collision, and no value typed
 * into a component is touched by it. [firstId] / [lastId] are STORED component ids (a taper,
 * thread, liner or explicit body); null means "from the AFT end" / "to the FWD end". The window
 * they resolve to is derived each time the sheet is made (`resolveSheetSection`), so a section
 * follows its components through every edit. A section naming a component that no longer
 * exists is kept and simply cannot print until it is pointed at one that does — never pruned.
 *
 * [final] names the drawing the section is of: the original schematic (false) or the Final
 * schematic. [name] prints as the sheet's title and names its file.
 */
@Serializable
data class SchematicSection(
    val id: String,
    val name: String = "",
    @SerialName("first_id") val firstId: String? = null,
    @SerialName("last_id") val lastId: String? = null,
    val final: Boolean = false,
)
