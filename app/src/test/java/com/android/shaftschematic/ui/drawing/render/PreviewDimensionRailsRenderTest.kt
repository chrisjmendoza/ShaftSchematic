package com.android.shaftschematic.ui.drawing.render

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
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
 * Review renders of the editor preview with its dimension rails — the real `ShaftRenderer` and
 * the real rail plan onto a phone-sized and a tablet-sized box, light and dark. Gated: a no-op
 * unless `PREVIEW_RAILS_RENDER_DIR` names the output directory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewDimensionRailsRenderTest {

    private val inch = 25.4f

    private val spec = ShaftSpec(
        overallLengthMm = 300f * inch,
        tapers = listOf(
            Taper(id = "T1", startFromAftMm = 0f, lengthMm = 25.25f * inch, startDiaMm = 10.368f * inch, endDiaMm = 8.266f * inch),
            Taper(id = "T2", startFromAftMm = 284f * inch, lengthMm = 16f * inch, startDiaMm = 8f * inch, endDiaMm = 6.5f * inch),
        ),
        threads = listOf(
            Threads(id = "THA", startFromAftMm = -6f * inch, lengthMm = 6f * inch, majorDiaMm = 5.828f * inch, tpi = 4f, excludeFromOAL = true, isAftEnd = true),
        ),
        liners = listOf(
            Liner(id = "L1", startFromAftMm = 40f * inch, lengthMm = 30.5f * inch, odMm = 10.99f * inch),
            Liner(id = "L2", startFromAftMm = 230f * inch, lengthMm = 34f * inch, odMm = 10.99f * inch),
        ),
        bodies = listOf(Body(id = "B1", startFromAftMm = 70.5f * inch, lengthMm = 159.5f * inch, diaMm = 10f * inch)),
        autoBodyDiaMm = 10f * inch,
    )

    /** A short FWD taper beside a FWD liner just off the S.E.T. — two values crowd one row there. */
    private val crowdedFwd = ShaftSpec(
        overallLengthMm = 300f * inch,
        tapers = listOf(
            Taper(id = "TA", startFromAftMm = 0f, lengthMm = 20f * inch, startDiaMm = 10f * inch, endDiaMm = 8.5f * inch),
            Taper(id = "TF", startFromAftMm = 283.5f * inch, lengthMm = 16.5f * inch, startDiaMm = 8.5f * inch, endDiaMm = 7f * inch),
        ),
        liners = listOf(
            Liner(id = "LA", startFromAftMm = 30f * inch, lengthMm = 30f * inch, odMm = 10.5f * inch),
            Liner(id = "LM", startFromAftMm = 140f * inch, lengthMm = 20f * inch, odMm = 10.5f * inch),
            Liner(id = "LF", startFromAftMm = (300f - 40.53125f - 30f) * inch, lengthMm = 30f * inch, odMm = 10.5f * inch),
        ),
        couplerBoltSlots = listOf(
            com.android.shaftschematic.model.CouplerBoltSlot(
                id = "SF", startFromAftMm = (300f - 9.53125f) * inch, holeDiaMm = 1f * inch, count = 1,
                authoredReference = com.android.shaftschematic.model.SlotAuthoredReference.FWD,
                showDimensionRail = true,
            ),
        ),
        autoBodyDiaMm = 10f * inch,
    )

    @Test
    fun `render preview boxes`() {
        val dir = System.getenv("PREVIEW_RAILS_RENDER_DIR")
        assumeTrue("PREVIEW_RAILS_RENDER_DIR not set; preview rail renders skipped", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        // Phone column (411 dp wide, ~130 dp tall, 2.75 px/dp) and tablet band (4:1, 2 px/dp).
        render(out, "phone_light", PHONE_W, PHONE_H, 2.75f, dark = false)
        render(out, "phone_dark", PHONE_W, PHONE_H, 2.75f, dark = true)
        render(out, "tablet_light", 1600, 400, 2f, dark = false)
        render(out, "phone_crowded_fwd", PHONE_W, PHONE_H, 2.75f, dark = false, spec = crowdedFwd)
        // The bundled sample shafts and templates, plus any `.shaft` files named in
        // PREVIEW_RAILS_FIXTURES (`;`-separated files or directories), on the phone box.
        val extra = System.getenv("PREVIEW_RAILS_FIXTURES").orEmpty().split(';').filter { it.isNotBlank() }.map(::File)
        (listOf(File("src/main/assets/sample_shafts"), File("src/main/assets/templates")) + extra)
            .flatMap { f -> if (f.isDirectory) f.listFiles { x -> x.name.endsWith(".shaft") }.orEmpty().sorted() else listOf(f) }
            .filter { it.isFile }
            .forEach { f ->
                val s = ShaftDocCodec.decode(f.readText()).spec
                if (s.overallLengthMm > 0f) render(out, "phone_${f.nameWithoutExtension}", PHONE_W, PHONE_H, 2.75f, dark = false, spec = s)
            }
    }

    private fun render(out: File, name: String, w: Int, h: Int, density: Float, dark: Boolean, spec: ShaftSpec = this.spec) {
        val bg = if (dark) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
        val ink = if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(bg)
        val resolved = resolveComponents(spec)
        val settings = PreviewRailSettings(displayUnits = DisplayUnits(UnitSystem.INCHES))
        val plan = planPreviewRails(
            rails = previewRailSpans(spec, settings)!!,
            settings = settings,
            boxTopPx = 0f, boxHeightPx = h.toFloat(), pxPerPt = density,
            railColor = ink, haloColor = bg,
            layoutFor = { reserve ->
                ShaftLayout.compute(spec, 16f, 8f + reserve, w - 16f, h - 8f, resolvedComponents = resolved)
            },
        )
        val canvas = Canvas(android.graphics.Canvas(bmp))
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            with(ShaftRenderer) {
                draw(spec, plan.layout, RenderOptions(outlineColor = ink, outlineWidthPx = 2f, showDimensionRails = true), resolved)
            }
            plan.draw(drawContext.canvas.nativeCanvas)
        }
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val PHONE_W = 1130
        const val PHONE_H = 357
    }
}
