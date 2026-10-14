// file: app/src/main/java/com/android/shaftschematic/ui/screen/ManualRoute.kt
package com.android.shaftschematic.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.shaftschematic.util.AppLog

/** The bundled manual's page. `docs/manual/build.py --web` writes everything under `manual/`. */
const val MANUAL_ASSET_URL = "file:///android_asset/manual/index.html"

/** Id of the contents section on the bundled page — the toolbar's Contents button target. */
const val MANUAL_CONTENTS_ANCHOR = "contents"

/** Test tag for the find-in-manual field. */
const val MANUAL_FIND_TAG = "manual_find"

private const val MANUAL_TITLE = "ShaftSchematic User Manual"
private const val TAG = "Manual"

/** The bundled page, opened at section [anchor] when one is given. */
internal fun manualUrl(anchor: String?): String =
    if (anchor.isNullOrBlank()) MANUAL_ASSET_URL else "$MANUAL_ASSET_URL#$anchor"

/** True for a URL the bundled manual serves; anything else is handed to another app. */
internal fun isManualUrl(url: String): Boolean = url.startsWith("file:///android_asset/manual/")

/** "3 of 12" / "No matches" under the find field; blank while there is nothing to report. */
internal fun manualFindSummary(query: String, activeOrdinal: Int, count: Int): String = when {
    query.isBlank() -> ""
    count == 0 -> "No matches"
    else -> "${activeOrdinal + 1} of $count"
}

/**
 * Holds the live WebView so the saver can capture its history on a configuration change (a
 * tablet rotation recreates the view) and the new view can restore it instead of reloading at
 * the route's start anchor.
 */
private class ManualPageHolder(var restored: Bundle? = null) {
    var webView: WebView? = null
}

private val ManualPageSaver = Saver<ManualPageHolder, Bundle>(
    save = { holder -> Bundle().also { b -> holder.webView?.saveState(b) } },
    restore = { b -> ManualPageHolder(restored = b) },
)

/**
 * ManualRoute — the User Manual, bundled with the app as one reflowing web page and shown in a
 * WebView. Reached from Help: the "User manual" card at the top of the list, a topic's "Read
 * more in the manual" link, and "Search the user manual" under a search.
 *
 * The page is `assets/manual/`, written by `docs/manual/build.py --web` from the same chapters
 * as the printed PDF; `ManualAssetsTest` fails when the chapters changed and the page was not
 * rebuilt, and when a Help topic links to a section the page no longer has. Nothing loads from
 * outside the asset folder, so the manual works with no connection — the shop floor's normal
 * state. JavaScript stays off: the page needs none, and find-in-page is the WebView's own.
 *
 * [initialAnchor] opens the page at that section; [initialFind] opens the find bar on that
 * query. Back closes the find bar first, then steps back through the page's own history (a
 * cross-reference jump is a history entry), then leaves.
 *
 * The page stays light in a dark app theme, like the white sheet canvases: it shows the
 * printed sheets, whose ink is drawn for white paper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRoute(
    onBack: () -> Unit,
    initialAnchor: String? = null,
    initialFind: String? = null,
) {
    val context = LocalContext.current
    val holder = rememberSaveable(saver = ManualPageSaver) { ManualPageHolder() }
    var canGoBack by remember { mutableStateOf(false) }
    var pageLoaded by remember { mutableStateOf(false) }
    var findOpen by rememberSaveable { mutableStateOf(!initialFind.isNullOrBlank()) }
    var findQuery by rememberSaveable { mutableStateOf(initialFind.orEmpty()) }
    var activeMatch by remember { mutableStateOf(0) }
    var matchCount by remember { mutableStateOf(0) }
    val findFocus = remember { FocusRequester() }

    fun closeFind() {
        findOpen = false
        holder.webView?.clearMatches()
    }

    BackHandler(enabled = findOpen || canGoBack) {
        if (findOpen) closeFind() else holder.webView?.goBack()
    }

    // Find runs once the page has loaded, and again on every edit of the query.
    LaunchedEffect(findOpen, findQuery, pageLoaded) {
        val view = holder.webView ?: return@LaunchedEffect
        if (!pageLoaded || !findOpen) return@LaunchedEffect
        if (findQuery.isBlank()) {
            view.clearMatches()
            matchCount = 0
        } else {
            view.findAllAsync(findQuery)
        }
    }
    LaunchedEffect(findOpen) {
        if (findOpen) runCatching { findFocus.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Manual") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { holder.webView?.loadUrl(manualUrl(MANUAL_CONTENTS_ANCHOR)) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents")
                    }
                    IconButton(onClick = { if (findOpen) closeFind() else findOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Find in manual")
                    }
                    IconButton(onClick = { holder.webView?.let { printManual(context, it) } }) {
                        Icon(Icons.Filled.Print, contentDescription = "Print manual")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            if (findOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        singleLine = true,
                        label = { Text("Find in manual") },
                        supportingText = {
                            Text(manualFindSummary(findQuery, activeMatch, matchCount))
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { holder.webView?.findNext(true) },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(findFocus)
                            .testTag(MANUAL_FIND_TAG),
                    )
                    IconButton(
                        onClick = { holder.webView?.findNext(false) },
                        enabled = matchCount > 0,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(
                        onClick = { holder.webView?.findNext(true) },
                        enabled = matchCount > 0,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                    }
                    IconButton(onClick = { closeFind() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close find")
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        // Assets load regardless; this only closes file:// access elsewhere.
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (isManualUrl(request.url.toString())) return false
                                openOutside(ctx, request.url)
                                return true
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                pageLoaded = true
                                canGoBack = view.canGoBack()
                            }
                        }
                        setFindListener { ordinal, count, _ ->
                            activeMatch = ordinal
                            matchCount = count
                        }
                        val restored = holder.restored
                        if (restored != null && restoreState(restored) != null) {
                            holder.restored = null
                        } else {
                            loadUrl(manualUrl(initialAnchor))
                        }
                        holder.webView = this
                    }
                },
                onRelease = { view ->
                    if (holder.webView === view) holder.webView = null
                    view.destroy()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * Prints the manual through the Android print framework, laid out by the print stylesheet
 * (Letter pages, the full-bleed cover, running footers) rather than the phone layout.
 */
private fun printManual(context: Context, webView: WebView) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            MANUAL_TITLE,
            webView.createPrintDocumentAdapter(MANUAL_TITLE),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build(),
        )
    } catch (e: Exception) {
        AppLog.e(TAG, "print failed", e)
    }
}

/** A link that leaves the manual opens in whatever app handles it; none is a no-op. */
private fun openOutside(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        AppLog.w(TAG, "no app for an outside link")
    }
}
