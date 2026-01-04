package com.android.shaftschematic.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalStorageNormalizeJsonNameTest {

    @Test
    fun `blank names normalize to null`() {
        assertNull(InternalStorage.normalizeJsonName(""))
        assertNull(InternalStorage.normalizeJsonName("   "))
    }

    @Test
    fun `names without extension get json appended`() {
        assertEquals("Shaft_20260103_0730.json", InternalStorage.normalizeJsonName("Shaft_20260103_0730"))
        assertEquals("foo.json", InternalStorage.normalizeJsonName("foo"))
    }

    @Test
    fun `names with json extension are preserved but lowercased`() {
        assertEquals("foo.json", InternalStorage.normalizeJsonName("foo.json"))
        assertEquals("foo.json", InternalStorage.normalizeJsonName("foo.JSON"))
        assertEquals("foo.json", InternalStorage.normalizeJsonName("foo.JsOn"))
    }

    @Test
    fun `whitespace is trimmed before normalization`() {
        assertEquals("foo.json", InternalStorage.normalizeJsonName("  foo  "))
        assertEquals("foo.json", InternalStorage.normalizeJsonName("  foo.JSON  "))
    }
}
