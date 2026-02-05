package com.android.shaftschematic.model

import kotlinx.serialization.Serializable

/**
 * Stable key for a derived auto body based on its neighboring explicit components.
 *
 * - leftId: authored id of the explicit component immediately AFT of the gap (or null for AFT boundary)
 * - rightId: authored id of the explicit component immediately FWD of the gap (or null for FWD boundary)
 */
@Serializable
data class AutoBodyKey(
    val leftId: String? = null,
    val rightId: String? = null,
) {
    fun stableId(): String {
        val left = leftId ?: "AFT"
        val right = rightId ?: "FWD"
        return "$left->$right"
    }
}

/**
 * Persisted overrides for auto bodies. Geometry is derived; overrides only affect appearance/metadata.
 */
@Serializable
data class AutoBodyOverride(
    val diaMm: Float? = null,
    val label: String? = null,
    val material: String? = null,
    val notes: String? = null,
)
