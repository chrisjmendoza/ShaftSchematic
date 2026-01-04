package com.android.shaftschematic.util

import android.util.Log

/**
 * VerboseLog
 *
 * Purpose:
 * - Provide opt-in, Developer-Options-gated logging.
 * - Avoid expensive string building unless the relevant category is enabled.
 *
 * Gate:
 * - Logging is enabled only when BOTH:
 *   1) Developer Options are enabled, and
 *   2) Master verbose logging is enabled.
 *
 * Categories:
 * - Fine-grained switches to avoid noisy logs.
 */
object VerboseLog {

    enum class Category {
        RENDER,
        OAL,
        PDF,
        IO,
    }

    @Volatile private var devOptionsEnabled: Boolean = false
    @Volatile private var verboseEnabled: Boolean = false

    @Volatile private var renderEnabled: Boolean = false
    @Volatile private var oalEnabled: Boolean = false
    @Volatile private var pdfEnabled: Boolean = false
    @Volatile private var ioEnabled: Boolean = false

    fun configure(
        devOptionsEnabled: Boolean,
        verboseEnabled: Boolean,
        renderEnabled: Boolean,
        oalEnabled: Boolean,
        pdfEnabled: Boolean,
        ioEnabled: Boolean,
    ) {
        this.devOptionsEnabled = devOptionsEnabled
        this.verboseEnabled = verboseEnabled
        this.renderEnabled = renderEnabled
        this.oalEnabled = oalEnabled
        this.pdfEnabled = pdfEnabled
        this.ioEnabled = ioEnabled
    }

    fun isEnabled(category: Category): Boolean {
        if (!devOptionsEnabled || !verboseEnabled) return false
        return when (category) {
            Category.RENDER -> renderEnabled
            Category.OAL -> oalEnabled
            Category.PDF -> pdfEnabled
            Category.IO -> ioEnabled
        }
    }

    inline fun d(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        Log.d(tag, msg())
    }

    inline fun i(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        Log.i(tag, msg())
    }

    inline fun w(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        Log.w(tag, msg())
    }

    inline fun e(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        Log.e(tag, msg())
    }
}
