package com.android.shaftschematic.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.ProjectInfo
import com.android.shaftschematic.settings.PDF_TEXT_SCALES
import com.android.shaftschematic.settings.PdfPrefs
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Review render of a real job's undercut sheet, for checking where the liner-end rail captions
 * land. Skipped unless `UNDERCUT_CAPTION_JOB` names a `.shaft` file and
 * `UNDERCUT_CAPTION_RENDER_DIR` a directory; the job is read in place and never copied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UndercutCaptionRenderTest {

    @Test
    fun `render the job's undercut sheet`() {
        val job = System.getenv("UNDERCUT_CAPTION_JOB")
        val dir = System.getenv("UNDERCUT_CAPTION_RENDER_DIR")
        assumeTrue("UNDERCUT_CAPTION_JOB / UNDERCUT_CAPTION_RENDER_DIR not set; render skipped", job != null && dir != null)
        val doc = ShaftDocCodec.decode(File(job!!).readText())
        val spec = doc.spec
        val resolved = resolveComponents(spec)
        val unit = doc.preferredUnit ?: UnitSystem.INCHES
        val record = doc.undercutRecord
        val project = ProjectInfo(
            customer = doc.customer, vessel = doc.vessel, jobNumber = doc.jobNumber, side = doc.shaftPosition,
        )
        val o = undercutSheetOrientation(spec, resolved, record, false)
        val out = File(dir!!).apply { mkdirs() }
        // Every "Dimension text size" step: the sheet's own prefs are device settings, not the job's.
        PDF_TEXT_SCALES.forEach { textScale ->
            val scale = 3f
            val bmp = Bitmap.createBitmap((o.widthPt * scale).toInt(), (o.heightPt * scale).toInt(), Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            composeUndercutPdf(
                pageOnCanvas(c, o.widthPt, o.heightPt), spec, project, unit,
                displayUnits = DisplayUnits(unit, doc.unitOverrides, doc.dualUnits),
                pdfPrefs = PdfPrefs(undercutDimTextScale = textScale),
                resolvedComponents = resolved, undercutRecord = record,
            )
            val name = "undercut_captions_${(textScale * 100).toInt()}.png"
            FileOutputStream(File(out, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
