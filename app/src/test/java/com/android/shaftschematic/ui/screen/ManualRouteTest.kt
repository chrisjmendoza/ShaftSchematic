package com.android.shaftschematic.ui.screen

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The manual screen opens the bundled page where Help asked, with JavaScript off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ManualRouteTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun host(anchor: String? = null, find: String? = null) {
        rule.setContent {
            MaterialTheme {
                ManualRoute(onBack = {}, initialAnchor = anchor, initialFind = find)
            }
        }
        rule.waitForIdle()
    }

    private fun findWebView(v: View): WebView? = when (v) {
        is WebView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findWebView(v.getChildAt(it)) }
        else -> null
    }

    private fun webView(): WebView {
        val view = findWebView(rule.activity.window.decorView)
        assertNotNull("ManualRoute should host a WebView", view)
        return view!!
    }

    @Test
    fun `opens the bundled page at the requested section`() {
        host(anchor = "editor-oal")

        assertEquals("$MANUAL_ASSET_URL#editor-oal", shadowOf(webView()).lastLoadedUrl)
        assertFalse(webView().settings.javaScriptEnabled)
    }

    @Test
    fun `opens at the start with no section`() {
        host()

        assertEquals(MANUAL_ASSET_URL, shadowOf(webView()).lastLoadedUrl)
        rule.onNodeWithText("User Manual").assertIsDisplayed()
    }

    @Test
    fun `contents button jumps to the contents`() {
        host(anchor = "editor-oal")

        rule.onNodeWithContentDescription("Contents").performClick()

        assertEquals(manualUrl(MANUAL_CONTENTS_ANCHOR), shadowOf(webView()).lastLoadedUrl)
    }

    @Test
    fun `a find query opens the find bar on it`() {
        host(find = "pilot runout")

        rule.onNodeWithTag(MANUAL_FIND_TAG).assertIsDisplayed()
        rule.onNodeWithText("pilot runout").assertIsDisplayed()

        rule.onNodeWithContentDescription("Close find").performClick()
        rule.onNodeWithTag(MANUAL_FIND_TAG).assertDoesNotExist()
    }
}
