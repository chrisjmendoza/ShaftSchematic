package com.android.shaftschematic.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every Output font must print as its OWN face on a real device — through the real path a sheet
 * takes: [PdfDocument] embeds the face, [PdfRenderer] rasterizes it back. On-device report: the
 * old system-family faces (Standard, Condensed, Serif, Monospace) printed identically on a Samsung
 * while the bundled Hyperlegible came through — which is why every face is now bundled. Robolectric
 * cannot answer this (it renders no real PDF), so this runs on a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class OutputFontPdfProbeTest {

    private val sample = "KW: 8 1/4\" x 3/4\" Rate 1:12 Ø 10.990\" 0O 1lI 5S"

    private fun rasterize(font: OutputFont): IntArray {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        BundledFonts.init(ctx)
        val file = File(ctx.cacheDir, "font_probe_${font.name}.pdf")
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(400, 60, 1).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font.typeface()
            textSize = 14f
            color = Color.BLACK
        }
        page.canvas.drawText(sample, 6f, 36f, paint)
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val bmp = Bitmap.createBitmap(1600, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { p -> p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
            }
        }
        return IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
    }

    @Test
    fun everyOutputFontPrintsAsItsOwnFace() {
        val pages = OutputFont.entries.associateWith { rasterize(it) }
        val alike = pages.entries.flatMapIndexed { i, a ->
            pages.entries.drop(i + 1).filter { b -> a.value.contentEquals(b.value) }.map { b -> "${a.key}=${b.key}" }
        }
        assertTrue("faces printed identically: $alike", alike.isEmpty())
    }
}
