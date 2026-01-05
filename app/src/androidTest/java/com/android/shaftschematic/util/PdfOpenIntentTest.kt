package com.android.shaftschematic.util

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfOpenIntentTest {

    @Test
    fun buildOpenPdfIntent_includesActionTypeFlagsAndClipData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = Uri.parse("content://com.android.shaftschematic.test/exported.pdf")

        val intent = buildOpenPdfIntent(context, uri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals(uri, intent.data)

        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val clip = intent.clipData
        assertNotNull(clip)
        assertEquals(1, clip!!.itemCount)
        assertEquals(uri, clip.getItemAt(0).uri)
    }
}
