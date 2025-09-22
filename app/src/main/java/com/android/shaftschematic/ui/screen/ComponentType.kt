// file: com/android/shaftschematic/ui/screen/ComponentType.kt
package com.android.shaftschematic.ui.screen

/**
 * The four component kinds a user can add/edit in the UI.
 *
 * Keep this enum in a single place to avoid JVM redeclaration errors.
 * UI files (ShaftScreen, chooser sheets, add dialogs) should import this.
 */
enum class ComponentType {
    BODY,
    TAPER,
    THREAD,
    LINER
}
