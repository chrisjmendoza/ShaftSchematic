package com.android.shaftschematic.ui.resolved

import com.android.shaftschematic.geom.WearAreaHost

/**
 * The components that can carry wear areas, from the resolved list: every liner and every drawn
 * body run (explicit or bare shaft), keyed by resolved component id — the identity a wear pit
 * uses. Tapers and threads host none. The single resolved → [WearAreaHost] mapping behind every
 * whole-shaft wear-area draw site.
 */
fun wearAreaHosts(components: List<ResolvedComponent>): List<WearAreaHost> =
    components.mapNotNull { it.asWearAreaHost() }

/** This component as a wear-area host, or null when its kind carries none. */
fun ResolvedComponent.asWearAreaHost(): WearAreaHost? = when (this) {
    is ResolvedLiner -> WearAreaHost(id, startMmPhysical, endMmPhysical - startMmPhysical, odMm, isLiner = true)
    is ResolvedBody -> WearAreaHost(id, startMmPhysical, endMmPhysical - startMmPhysical, diaMm, isLiner = false)
    else -> null
}
