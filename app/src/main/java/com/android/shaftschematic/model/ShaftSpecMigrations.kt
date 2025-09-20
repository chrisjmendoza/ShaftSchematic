// file: com/android/shaftschematic/model/ShaftSpecMigrations.kt
package com.android.shaftschematic.model

import java.util.UUID

object ShaftSpecMigrations {
    fun ensureIds(spec: ShaftSpec): ShaftSpec = spec.copy(
        bodies  = spec.bodies.map { if (it.id.isBlank()) it.copy(id = UUID.randomUUID().toString()) else it },
        tapers  = spec.tapers.map { if (it.id.isBlank()) it.copy(id = UUID.randomUUID().toString()) else it },
        threads = spec.threads.map { if (it.id.isBlank()) it.copy(id = UUID.randomUUID().toString()) else it },
        liners  = spec.liners.map { if (it.id.isBlank()) it.copy(id = UUID.randomUUID().toString()) else it },
    )
}

private fun String?.isBlank() = this == null || this.isEmpty()
