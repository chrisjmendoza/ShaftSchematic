package com.android.shaftschematic.ui.drawing.render

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.shaftschematic.doc.ShaftDocCodec
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.LinerAuthoredReference
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.drawing.renderSpanSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.shadedComponentIds
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
 * Review renders of the editor preview with the sheet layout ON and OFF — the real
 * `ShaftLayout`/`ShaftRenderer`, the real sheet profile and the real rail plan, laid out exactly as
 * `ShaftDrawing` lays them out, onto a phone-shaped box (360 × 120 dp at 2.6 px/dp) and a
 * tablet-shaped one (800 × 200 dp at 2 px/dp). Gated: a no-op unless `PREVIEW_SHEET_RENDER_DIR`
 * names the output directory. `PREVIEW_RAILS_FIXTURES` adds `.shaft` files (`;`-separated).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewSheetLayoutRenderTest {

    @Test
    fun `render sheet layout on and off`() {
        val dir = System.getenv("PREVIEW_SHEET_RENDER_DIR")
        assumeTrue("PREVIEW_SHEET_RENDER_DIR not set; sheet layout renders skipped", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        val fixtures = buildList {
            add("synthetic" to PreviewSheetFixtures.longKeyedShaft())
            add("synthetic_optout" to PreviewSheetFixtures.longKeyedShaft().let { s ->
                s.copy(bodies = s.bodies.map { it.copy(compressOnDrawing = false) })
            })
            System.getenv("PREVIEW_RAILS_FIXTURES").orEmpty().split(';').filter { it.isNotBlank() }
                .map(::File).filter { it.isFile }
                .forEach { f -> add(f.nameWithoutExtension to ShaftDocCodec.decode(f.readText()).spec) }
        }
        for ((name, spec) in fixtures) {
            val resolved = resolveComponents(spec)
            // Select the longest body run, so the ring shows on a broken run.
            val selected = resolved.filterIsInstance<ResolvedBody>()
                .maxByOrNull { it.endMmPhysical - it.startMmPhysical }?.id
            for (sheet in listOf(true, false)) {
                val tag = if (sheet) "sheet" else "linear"
                render(out, "${name}_phone_$tag", spec, 936, 312, 2.6f, sheet, selected)
                render(out, "${name}_tablet_$tag", spec, 1600, 400, 2f, sheet, selected)
            }
        }
    }

    private fun render(
        out: File, name: String, spec: ShaftSpec, w: Int, h: Int, density: Float,
        sheet: Boolean, selectedId: String?,
    ) {
        val bg = 0xFFFFFBFE.toInt()
        val ink = 0xFF49454F.toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(bg)
        val resolved = resolveComponents(spec)
        val safeSpec = spec.renderSpanSpec()
        val padX = 16f
        val padY = 8f
        val profile = if (!sheet) null else previewSheetProfileForBox(
            spec = spec, layoutSpec = safeSpec, settings = PreviewSheetSettings(),
            leftPx = padX, topPx = padY, rightPx = w - padX, bottomPx = h - padY,
            pxPerPt = density, resolvedComponents = resolved,
        )
        val rails = PreviewRailSettings(displayUnits = DisplayUnits(UnitSystem.INCHES))
        val plan = planPreviewRails(
            rails = previewRailSpans(spec, rails)!!,
            settings = rails,
            boxTopPx = 0f, boxHeightPx = h.toFloat(), pxPerPt = density,
            railColor = ink, haloColor = bg,
            layoutFor = { reserve ->
                ShaftLayout.compute(
                    safeSpec, padX, padY + reserve, w - padX, h - padY,
                    resolvedComponents = resolved, sheet = profile,
                )
            },
        )
        val shaded = shadedComponentIds(spec, resolved, shadedBodies = true, shadedTapers = true, shadedLiners = true, shadeExplicitBodiesOnly = false)
        val opts = RenderOptions(
            outlineColor = ink,
            outlineWidthPx = 2f,
            bodyFillColor = Color(0xFF607D8B).copy(alpha = 0.18f).toArgb(),
            taperFillColor = Color(0xFF607D8B).copy(alpha = 0.18f).toArgb(),
            linerFillColor = Color(0xFFB87333).copy(alpha = 0.16f).toArgb(),
            shadedComponentIds = shaded,
            fillFollowsShade = true,
            showDimensionRails = true,
            highlightEnabled = selectedId != null,
            highlightId = selectedId,
            highlightGlowColor = Color(0xFF6750A4),
            highlightGlowAlpha = 0.75f,
            highlightGlowExtraPx = 4f,
        )
        val canvas = Canvas(android.graphics.Canvas(bmp))
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            with(ShaftRenderer) { draw(safeSpec, plan.layout, opts, resolved) }
            plan.draw(drawContext.canvas.nativeCanvas)
        }
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Shared fixtures for the sheet-layout tests. */
internal object PreviewSheetFixtures {
    private const val IN = 25.4f

    /**
     * A 25' shaft: a keyed AFT taper, an excluded AFT thread, a liner, a long explicit body with an
     * AFT-end keyway, and a FWD taper — every kind the sheet layout maps.
     */
    fun longKeyedShaft(): ShaftSpec = ShaftSpec(
        overallLengthMm = 300f * IN,
        tapers = listOf(
            com.android.shaftschematic.model.Taper(
                id = "TA", startFromAftMm = 0f, lengthMm = 20f * IN, startDiaMm = 6f * IN, endDiaMm = 7.5f * IN,
                keywayWidthMm = 1.5f * IN, keywayDepthMm = 0.75f * IN, keywayLengthMm = 14f * IN,
            ),
            com.android.shaftschematic.model.Taper(
                id = "TF", startFromAftMm = 284f * IN, lengthMm = 16f * IN, startDiaMm = 8f * IN, endDiaMm = 6.5f * IN,
            ),
        ),
        threads = listOf(
            com.android.shaftschematic.model.Threads(
                id = "TH", startFromAftMm = -6f * IN, lengthMm = 6f * IN, majorDiaMm = 4.5f * IN, tpi = 4f,
                excludeFromOAL = true, isAftEnd = true,
            ),
        ),
        liners = listOf(
            com.android.shaftschematic.model.Liner(id = "L1", startFromAftMm = 30f * IN, lengthMm = 30f * IN, odMm = 8.5f * IN),
        ),
        bodies = listOf(
            Body(
                id = "B1", startFromAftMm = 60f * IN, lengthMm = 224f * IN, diaMm = 8f * IN,
                keywayWidthMm = 2f * IN, keywayDepthMm = 1f * IN, keywayLengthMm = 12f * IN,
                keywayEnd = LinerAuthoredReference.FWD, keywayOffsetFromEndMm = 4f * IN,
            ),
        ),
        autoBodyDiaMm = 8f * IN,
    )
}
