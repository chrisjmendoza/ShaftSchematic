package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.RunoutComponentKind
import com.android.shaftschematic.geom.RunoutComponentSpan

/**
 * RunoutSpans — the ONE mapping from resolved components to runout station spans.
 *
 * Both draw sites go through this: `RunoutPdfComposer` (paper) and `RunoutRoute` (the live
 * canvas). They used to build spans independently and disagreed about identity — the canvas
 * keyed bodies by their BASE id while the PDF kept the resolved FRAGMENT id (`"<id>#2"`).
 * On a body split by a liner that meant the printed sheet missed the user's station-count
 * override (`overrides["X"]` vs a lookup for `"X#2"`) and could not find hand-entered TIR
 * readings (stored as `("X", 1)`, looked up as `("X#2", 1)`), so the value stayed in the file
 * and never reached the paper. Invisible on an unfragmented body, where the two ids are equal.
 *
 * Rule: **spans are always keyed by the component the user names** — the base body id, the
 * taper/liner id — and every drawn run of that component carries that same key.
 * `collectRunoutStations` then derives one count for the component and apportions it across
 * the runs. Threads are not measured for runout and never produce spans.
 */
fun runoutComponentSpans(resolved: List<ResolvedComponent>): List<RunoutComponentSpan> =
    resolved.mapNotNull { rc ->
        val lengthMm = rc.endMmPhysical - rc.startMmPhysical
        if (lengthMm <= 0f) return@mapNotNull null
        when (rc) {
            is ResolvedBody -> RunoutComponentSpan(
                id = resolvedBodyBaseId(rc.id),
                kind = RunoutComponentKind.BODY,
                startMm = rc.startMmPhysical,
                lengthMm = lengthMm,
            )
            is ResolvedTaper -> RunoutComponentSpan(
                id = rc.id,
                kind = RunoutComponentKind.TAPER,
                startMm = rc.startMmPhysical,
                lengthMm = lengthMm,
            )
            is ResolvedLiner -> RunoutComponentSpan(
                id = rc.id,
                kind = RunoutComponentKind.LINER,
                startMm = rc.startMmPhysical,
                lengthMm = lengthMm,
            )
            else -> null
        }
    }
