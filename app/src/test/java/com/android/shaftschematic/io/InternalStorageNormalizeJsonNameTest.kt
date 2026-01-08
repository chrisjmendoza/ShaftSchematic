package com.android.shaftschematic.io

import com.android.shaftschematic.doc.SHAFT_DOT_EXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternalStorageNormalizeJsonNameTest {

    @Test
    fun `blank names normalize to null`() {
        assertNull(InternalStorage.normalizeShaftDocName(""))
        assertNull(InternalStorage.normalizeShaftDocName("   "))
    }

    @Test
    fun `names without extension get shaft appended`() {
        assertEquals("Shaft_20260103_0730" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("Shaft_20260103_0730"))
        assertEquals("foo" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("foo"))
    }

    @Test
    fun `names with legacy json extension are normalized to shaft`() {
        assertEquals("foo" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("foo.json"))
        assertEquals("foo" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("foo.JSON"))
        assertEquals("foo" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("foo.JsOn"))
    }

    @Test
    fun `whitespace is trimmed before normalization`() {
        assertEquals("foo" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("  foo  "))
        assertEquals("foo" + SHAFT_DOT_EXT, InternalStorage.normalizeShaftDocName("  foo.JSON  "))
    }
}
