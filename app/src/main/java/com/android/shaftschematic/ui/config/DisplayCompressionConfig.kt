package com.android.shaftschematic.ui.config

/**
 * Display-only compression for long shafts (bodies only).
 * Never mutates model values—only affects drawn X lengths / scale.
 */
object DisplayCompressionConfig {
    // Enable for PDF
    const val ENABLED_PDF: Boolean = true

    // Target visual height band for the shaft silhouette (points; 72 pt = 1 inch).
    // We aim for MAX first (thicker), then fall back toward MIN if width won’t fit.
    const val TARGET_SHAFT_HEIGHT_MIN_PT: Float = 36f  // 0.5 in
    const val TARGET_SHAFT_HEIGHT_MAX_PT: Float = 72f  // 1.0 in

    // Which bodies are eligible for break-compression
    const val BODY_BREAK_MIN_MM: Float = 1016f         // ≈ 40 in

    // We compute the minimum compression factor needed.
    // Never compress a body below this fraction of its true length.
    const val BODY_COMPRESS_MIN_FACTOR: Float = 0.30f  // 30% of true X

    // Don’t let compressed bodies visually vanish
    const val MIN_BODY_DRAWN_PT: Float = 24f

    // Readability floor for labels
    const val MIN_PDF_PT: Float = 8f
}
