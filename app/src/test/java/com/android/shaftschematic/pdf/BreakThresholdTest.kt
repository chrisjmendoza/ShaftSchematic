package com.android.shaftschematic.pdf

import com.android.shaftschematic.settings.PDF_SBREAK_THRESHOLD_DEFAULT
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [breakForCompression] — the body-only S-break threshold. A run keeps its plain outline
 * until it draws below the user's threshold fraction of its true width; past that, enough
 * shaft is hidden that the pair must mark the longer span (on-device report: a 6" run kept
 * at ~74% of true carried a full break pair under an any-foreshortening rule). The
 * threshold is Settings → PDF Export → "Body S-break" (`PdfPrefs.sBreakThresholdFrac`,
 * default half); the long-span trigger (COMPRESS_TRIGGER_PT at the draw sites) is
 * independent of it and fires at every setting.
 */
class BreakThresholdTest {

    // A 6" run (152.4 mm) at the 1" standard scale (~0.3543 pt/mm): true ≈ 54 pt.
    private val trueLenMm = 152.4f
    private val scale = 0.3543f
    private val trueWidthPt = trueLenMm * scale

    @Test
    fun `run kept at three quarters of true prints plain`() {
        assertFalse(breakForCompression(trueWidthPt * 0.74f, trueLenMm, scale, 0.5f))
    }

    @Test
    fun `run squeezed below half of true breaks`() {
        assertTrue(breakForCompression(trueWidthPt * 0.48f, trueLenMm, scale, 0.5f))
    }

    @Test
    fun `exactly half of true prints plain — the threshold is strict`() {
        assertFalse(breakForCompression(trueWidthPt * 0.5f, trueLenMm, scale, 0.5f))
    }

    @Test
    fun `deep page compression always breaks`() {
        assertTrue(breakForCompression(trueWidthPt * 0.1f, trueLenMm, scale, 0.5f))
    }

    @Test
    fun `zero true scale disables the check`() {
        assertFalse(breakForCompression(1f, trueLenMm, 0f, 0.5f))
    }

    @Test
    fun `true-scale draw never breaks`() {
        assertFalse(breakForCompression(trueWidthPt, trueLenMm, scale, 0.5f))
    }

    @Test
    fun `the shipped default is half of true`() {
        assertFalse(breakForCompression(trueWidthPt * 0.51f, trueLenMm, scale, PDF_SBREAK_THRESHOLD_DEFAULT))
        assertTrue(breakForCompression(trueWidthPt * 0.49f, trueLenMm, scale, PDF_SBREAK_THRESHOLD_DEFAULT))
    }

    // ── The slider's ends ────────────────────────────────────────────────────────

    @Test
    fun `threshold zero never breaks on compression — all hidden compression`() {
        // Even a run squeezed to 1% of true stays plain; only the long-span trigger,
        // which this predicate does not govern, can still break it.
        assertFalse(breakForCompression(trueWidthPt * 0.01f, trueLenMm, scale, 0f))
        assertFalse(breakForCompression(0f, trueLenMm, scale, 0f))
    }

    @Test
    fun `threshold one breaks on any shortfall`() {
        assertTrue(breakForCompression(trueWidthPt * 0.99f, trueLenMm, scale, 1f))
        // Drawn at true width is not a shortfall — the comparison stays strict.
        assertFalse(breakForCompression(trueWidthPt, trueLenMm, scale, 1f))
    }

    // ── The named intermediate positions ─────────────────────────────────────────

    @Test
    fun `threshold one quarter tolerates deep compression`() {
        assertFalse(breakForCompression(trueWidthPt * 0.3f, trueLenMm, scale, 0.25f))
        assertTrue(breakForCompression(trueWidthPt * 0.2f, trueLenMm, scale, 0.25f))
    }

    @Test
    fun `threshold three quarters breaks on mild compression`() {
        assertTrue(breakForCompression(trueWidthPt * 0.7f, trueLenMm, scale, 0.75f))
        assertFalse(breakForCompression(trueWidthPt * 0.8f, trueLenMm, scale, 0.75f))
    }
}
