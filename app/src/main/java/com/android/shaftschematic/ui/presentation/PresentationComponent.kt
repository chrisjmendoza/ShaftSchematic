package com.android.shaftschematic.ui.presentation

import com.android.shaftschematic.model.AutoBodyOverride
import com.android.shaftschematic.ui.resolved.ResolvedComponent

/**
 * Presentation-layer wrapper for resolved components shown in UI lists and editors.
 *
 * Why this exists:
 * - UI needs a stable, user-facing model that can group one or more resolved parts.
 * - Auto-derived pieces need display metadata without altering resolved geometry.
 * - Editing affordances (editable vs read-only) should not leak into render/layout models.
 *
 * Problems it solves:
 * - Prevents UI from depending on layout- and render-specific details.
 * - Keeps auto-body overrides and grouping logic out of the ViewModel for now.
 * - Provides a single contract for list rows, edit panels, and selection state.
 *
 * MUST NEVER:
 * - Mutate the underlying spec or resolved geometry.
 * - Perform geometry math or layout calculations.
 * - Be used as an input to rendering or PDF output.

  * Invariant:
 * - All resolvedParts must share the same authoredSourceId.
 * - UI code may assume this is true.
 */
sealed class PresentationComponent {
    abstract val id: String
    abstract val kind: PresentationComponentKind
    abstract val editable: Boolean
    abstract val source: PresentationComponentSource
    abstract val resolvedParts: List<ResolvedComponent>

    data class Body(
        override val id: String,
        override val editable: Boolean,
        override val source: PresentationComponentSource,
        override val resolvedParts: List<ResolvedComponent>,
        val autoBodyOverride: AutoBodyOverride? = null,
    ) : PresentationComponent() {
        override val kind: PresentationComponentKind = PresentationComponentKind.BODY
    }

    data class Taper(
        override val id: String,
        override val editable: Boolean,
        override val source: PresentationComponentSource,
        override val resolvedParts: List<ResolvedComponent>,
    ) : PresentationComponent() {
        override val kind: PresentationComponentKind = PresentationComponentKind.TAPER
    }

    data class Thread(
        override val id: String,
        override val editable: Boolean,
        override val source: PresentationComponentSource,
        override val resolvedParts: List<ResolvedComponent>,
    ) : PresentationComponent() {
        override val kind: PresentationComponentKind = PresentationComponentKind.THREAD
    }

    data class Liner(
        override val id: String,
        override val editable: Boolean,
        override val source: PresentationComponentSource,
        override val resolvedParts: List<ResolvedComponent>,
    ) : PresentationComponent() {
        override val kind: PresentationComponentKind = PresentationComponentKind.LINER
    }

    companion object {
        /**
         * Build presentation components from resolved geometry and optional auto-body overrides.
         *
         * This mapping is UI-focused only. It must not change geometry or spec state.
         */
        fun fromResolved(
            resolved: List<ResolvedComponent>,
            overrides: Map<String, AutoBodyOverride>,
        ): List<PresentationComponent> {
            if (resolved.isEmpty()) return emptyList()

            data class Group(
                val id: String,
                val kind: PresentationComponentKind,
                val source: PresentationComponentSource,
                val authoredId: String?,
                val autoBodyKey: String?,
                val parts: MutableList<ResolvedComponent>,
            )

            fun toKind(type: com.android.shaftschematic.ui.resolved.ResolvedComponentType): PresentationComponentKind =
                when (type) {
                    com.android.shaftschematic.ui.resolved.ResolvedComponentType.BODY,
                    com.android.shaftschematic.ui.resolved.ResolvedComponentType.BODY_AUTO -> PresentationComponentKind.BODY
                    com.android.shaftschematic.ui.resolved.ResolvedComponentType.TAPER -> PresentationComponentKind.TAPER
                    com.android.shaftschematic.ui.resolved.ResolvedComponentType.THREAD -> PresentationComponentKind.THREAD
                    com.android.shaftschematic.ui.resolved.ResolvedComponentType.LINER -> PresentationComponentKind.LINER
                }

            fun toSource(source: com.android.shaftschematic.ui.resolved.ResolvedComponentSource): PresentationComponentSource =
                when (source) {
                    com.android.shaftschematic.ui.resolved.ResolvedComponentSource.EXPLICIT -> PresentationComponentSource.EXPLICIT
                    com.android.shaftschematic.ui.resolved.ResolvedComponentSource.AUTO -> PresentationComponentSource.AUTO
                    com.android.shaftschematic.ui.resolved.ResolvedComponentSource.DRAFT -> PresentationComponentSource.DRAFT
                }

            val groups = LinkedHashMap<String, Group>()

            resolved.forEach { comp ->
                val kind = toKind(comp.type)
                val source = toSource(comp.source)
                val isAutoBody = comp is com.android.shaftschematic.ui.resolved.ResolvedBody &&
                    comp.source == com.android.shaftschematic.ui.resolved.ResolvedComponentSource.AUTO
                val autoBodyKey = if (isAutoBody) {
                    requireNotNull(comp.autoBodyKey) { "AUTO body must have autoBodyKey" }.stableId()
                } else {
                    null
                }

                val groupId = when {
                    comp.source == com.android.shaftschematic.ui.resolved.ResolvedComponentSource.DRAFT -> comp.id
                    isAutoBody -> autoBodyKey!!
                    else -> comp.authoredSourceId
                }

                val bucketKey = when {
                    comp.source == com.android.shaftschematic.ui.resolved.ResolvedComponentSource.DRAFT -> "draft:${comp.id}"
                    isAutoBody -> "auto:$autoBodyKey"
                    else -> "authored:${comp.authoredSourceId}"
                }

                val group = groups.getOrPut(bucketKey) {
                    Group(
                        id = groupId,
                        kind = kind,
                        source = source,
                        authoredId = if (source == PresentationComponentSource.AUTO) null else comp.authoredSourceId,
                        autoBodyKey = if (isAutoBody) autoBodyKey else null,
                        parts = mutableListOf(),
                    )
                }
                require(group.kind == kind) { "Group kind mismatch for ${group.id}" }
                require(group.source == source) { "Group source mismatch for ${group.id}" }
                if (group.source == PresentationComponentSource.AUTO) {
                    require(autoBodyKey == group.autoBodyKey) { "Auto body key mismatch for ${group.id}" }
                } else {
                    require(comp.authoredSourceId == group.authoredId) { "Authored source mismatch for ${group.id}" }
                }
                group.parts.add(comp)
            }

            val components = groups.values.map { group ->
                val editable = when {
                    group.source == PresentationComponentSource.DRAFT -> true
                    group.kind == PresentationComponentKind.BODY && group.source == PresentationComponentSource.AUTO ->
                        group.autoBodyKey?.let { overrides.containsKey(it) } ?: false
                    else -> true
                }

                when (group.kind) {
                    PresentationComponentKind.BODY -> PresentationComponent.Body(
                        id = group.id,
                        editable = editable,
                        source = group.source,
                        resolvedParts = group.parts.toList(),
                        autoBodyOverride = group.autoBodyKey?.let { overrides[it] },
                    )
                    PresentationComponentKind.TAPER -> PresentationComponent.Taper(
                        id = group.id,
                        editable = editable,
                        source = group.source,
                        resolvedParts = group.parts.toList(),
                    )
                    PresentationComponentKind.THREAD -> PresentationComponent.Thread(
                        id = group.id,
                        editable = editable,
                        source = group.source,
                        resolvedParts = group.parts.toList(),
                    )
                    PresentationComponentKind.LINER -> PresentationComponent.Liner(
                        id = group.id,
                        editable = editable,
                        source = group.source,
                        resolvedParts = group.parts.toList(),
                    )
                }
            }

            return components.sortedWith(
                compareBy<PresentationComponent>(
                    { it.resolvedParts.minOf { part -> part.startMmPhysical } },
                    { kindSortKey(it.kind) },
                    { it.id }
                )
            )
        }
    }
}

private fun kindSortKey(kind: PresentationComponentKind): Int = when (kind) {
    PresentationComponentKind.BODY -> 0
    PresentationComponentKind.TAPER -> 1
    PresentationComponentKind.THREAD -> 2
    PresentationComponentKind.LINER -> 3
}

enum class PresentationComponentKind { BODY, TAPER, THREAD, LINER }

enum class PresentationComponentSource { EXPLICIT, AUTO, DRAFT }
