// file: com/android/shaftschematic/data/ShaftRepository.kt
package com.android.shaftschematic.data

import com.android.shaftschematic.model.ShaftSpec

interface ShaftRepository {
    /** Load the current spec from storage (or return default). */
    suspend fun loadSpec(): ShaftSpec

    /** Persist the given spec. */
    suspend fun saveSpec(spec: ShaftSpec)
}