// file: com/android/shaftschematic/data/NoopShaftRepository.kt
package com.android.shaftschematic.data

import com.android.shaftschematic.model.ShaftSpec

object NoopShaftRepository : ShaftRepository {
    override suspend fun loadSpec(): ShaftSpec = ShaftSpec() // nothing stored yet
    override suspend fun saveSpec(spec: ShaftSpec) { /* no-op for now */ }
}
