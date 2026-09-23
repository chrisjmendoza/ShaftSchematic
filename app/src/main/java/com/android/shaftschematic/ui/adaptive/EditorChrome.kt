// file: app/src/main/java/com/android/shaftschematic/ui/adaptive/EditorChrome.kt
package com.android.shaftschematic.ui.adaptive

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the editor sidebar is PERMANENT — laid out beside the tab content — rather than the
 * phone's slide-in overlay. `ShaftEditorRoute` provides it (true in an EXPANDED window); every
 * tab toolbar reads it to hide its hamburger, since there is nothing to open.
 *
 * A CompositionLocal rather than a parameter because six tab routes would otherwise each carry
 * a boolean whose only consumer is one icon; the routes stay unaware of the layout decision.
 * Default false, so a tab hosted outside the editor (a test, a preview) keeps its menu button.
 */
val LocalSidebarPermanent = compositionLocalOf { false }
