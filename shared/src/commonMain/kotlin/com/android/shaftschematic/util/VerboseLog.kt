package com.android.shaftschematic.util

import kotlin.concurrent.Volatile

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
 *
 * Sink:
 * - An enabled line goes to the platform log through [verboseLogD] / [verboseLogI] /
 *   [verboseLogW] / [verboseLogE] — `android.util.Log` on Android.
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
        verboseLogD(tag, msg())
    }

    inline fun i(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        verboseLogI(tag, msg())
    }

    inline fun w(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        verboseLogW(tag, msg())
    }

    inline fun e(category: Category, tag: String, crossinline msg: () -> String) {
        if (!isEnabled(category)) return
        verboseLogE(tag, msg())
    }
}

/** Debug-level platform log line — `Log.d(tag, msg)` on Android. */
@PublishedApi
internal expect fun verboseLogD(tag: String, msg: String)

/** Info-level platform log line — `Log.i(tag, msg)` on Android. */
@PublishedApi
internal expect fun verboseLogI(tag: String, msg: String)

/** Warning-level platform log line — `Log.w(tag, msg)` on Android. */
@PublishedApi
internal expect fun verboseLogW(tag: String, msg: String)

/** Error-level platform log line — `Log.e(tag, msg)` on Android. */
@PublishedApi
internal expect fun verboseLogE(tag: String, msg: String)
