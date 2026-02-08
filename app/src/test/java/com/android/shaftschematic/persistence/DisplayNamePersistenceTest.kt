package com.android.shaftschematic.persistence

import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.UnitSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayNamePersistenceTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `envelope round trip preserves display name`() {
        val spec = ShaftSpec(displayName = "Test Shaft")
        val raw = ShaftDocCodec.encodeV1(
            ShaftDocCodec.ShaftDocV1(
                preferredUnit = UnitSystem.INCHES,
                unitLocked = true,
                jobNumber = "",
                customer = "",
                vessel = "",
                notes = "",
                spec = spec,
            )
        )

        val decoded = ShaftDocCodec.decode(raw)
        assertEquals("Test Shaft", decoded.spec.displayName)
    }

    @Test
    fun `legacy spec without display name keeps null`() {
        val raw = json.encodeToString(ShaftSpec())
        val decoded = ShaftDocCodec.decode(raw)
        assertNull(decoded.spec.displayName)
    }
}
