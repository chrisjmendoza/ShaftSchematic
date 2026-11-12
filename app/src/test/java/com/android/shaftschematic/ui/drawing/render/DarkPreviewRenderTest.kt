package com.android.shaftschematic.ui.drawing.render

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.model.Taper
import com.android.shaftschematic.model.Threads
import com.android.shaftschematic.ui.drawing.renderSpanSpec
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.resolved.shadedComponentIds
import com.android.shaftschematic.ui.theme.DarkColorScheme
import com.android.shaftschematic.ui.theme.HighContrastDarkColorScheme
import com.android.shaftschematic.ui.theme.LightColorScheme
import com.android.shaftschematic.ui.theme.OnWarnAmberDark
import com.android.shaftschematic.ui.theme.WarnAmberDark
import com.android.shaftschematic.util.DisplayUnits
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.UnitSystem
import com.android.shaftschematic.util.isDarkPreviewGround
import com.android.shaftschematic.util.previewShaftGrey
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Review renders of the editor preview's colours per theme — the real sheet-layout profile, rails
 * and renderer, coloured the way `ShaftDrawing` colours them (default presets: Steel outline,
 * hatch and taper fill, Bronze liner fill, bodies unfilled) over the scheme's background. `old`
 * is M3's baseline purple-black with the light-ground preset rule, for comparison. Gated: a no-op
 * unless `DARK_PREVIEW_RENDER_DIR` names the output directory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DarkPreviewRenderTest {

    private class Ink(val bg: Color, val outline: Color, val taper: Color, val liner: Color, val base: Color = Color.Transparent)

    private fun current(scheme: ColorScheme): Ink {
        val dark = scheme.isDarkPreviewGround()
        fun res(p: PreviewColorPreset) = p.resolve(scheme, PreviewColorRole.PRIMARY)
        return Ink(
            bg = scheme.background,
            outline = res(PreviewColorPreset.STEEL),
            taper = res(PreviewColorPreset.STEEL).copy(alpha = PreviewColorPreset.STEEL.fillAlpha(dark, 0.14f)),
            liner = res(PreviewColorPreset.BRONZE).copy(alpha = PreviewColorPreset.BRONZE.fillAlpha(dark, 0.16f)),
            base = scheme.previewShaftGrey() ?: Color.Transparent,
        )
    }

    private fun previous(): Ink {
        val s = darkColorScheme(
            primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
            tertiaryContainer = WarnAmberDark, onTertiaryContainer = OnWarnAmberDark,
        )
        val steel = lerp(s.surface, s.onSurface, 0.75f)
        return Ink(s.background, steel, steel.copy(alpha = 0.18f), s.tertiary.copy(alpha = 0.16f))
    }

    @Test
    fun `render preview per theme`() {
        val dir = System.getenv("DARK_PREVIEW_RENDER_DIR")
        assumeTrue("DARK_PREVIEW_RENDER_DIR not set; dark preview renders skipped", dir != null)
        val out = File(dir!!).apply { mkdirs() }
        val spec = fixture()
        for ((name, ink) in listOf(
            "old" to previous(),
            "new" to current(DarkColorScheme),
            "hc_dark" to current(HighContrastDarkColorScheme),
            "light" to current(LightColorScheme),
        )) {
            render(out, name, spec, ink, 936, 360, 2.6f)
        }
    }

    private fun render(out: File, name: String, spec: ShaftSpec, ink: Ink, w: Int, h: Int, density: Float) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(ink.bg.toArgb())
        val resolved = resolveComponents(spec)
        val safeSpec = spec.renderSpanSpec()
        val padX = 16f
        val padY = 8f
        val profile = previewSheetProfileForBox(
            spec = spec, layoutSpec = safeSpec, settings = PreviewSheetSettings(),
            leftPx = padX, topPx = padY, rightPx = w - padX, bottomPx = h - padY,
            densityPxPerPt = density, resolvedComponents = resolved,
        )
        val rails = PreviewRailSettings(displayUnits = DisplayUnits(UnitSystem.INCHES))
        val plan = planPreviewRails(
            rails = previewRailSpans(spec, rails)!!,
            settings = rails,
            boxTopPx = 0f, boxHeightPx = h.toFloat(), pxPerPt = density,
            railColor = ink.outline.toArgb(), haloColor = ink.bg.toArgb(),
            layoutFor = { reserve ->
                ShaftLayout.compute(
                    safeSpec, padX, padY + reserve, w - padX, h - padY,
                    resolvedComponents = resolved, sheet = profile,
                )
            },
        )
        val shaded = shadedComponentIds(
            spec, resolved, shadedBodies = false, shadedTapers = true, shadedLiners = true,
            shadeExplicitBodiesOnly = true,
        )
        val opts = RenderOptions(
            outlineColor = ink.outline.toArgb(),
            outlineWidthPx = 2f,
            bodyFillColor = Color.Transparent.toArgb(),
            taperFillColor = ink.taper.toArgb(),
            linerFillColor = ink.liner.toArgb(),
            threadFillColor = Color.Transparent.toArgb(),
            threadHatchColor = ink.outline.toArgb(),
            shadedComponentIds = shaded,
            fillFollowsShade = true,
            baseFillColor = ink.base.toArgb(),
            showDimensionRails = true,
            highlightEnabled = false,
        )
        val canvas = Canvas(android.graphics.Canvas(bmp))
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            with(ShaftRenderer) { draw(safeSpec, plan.layout, opts, resolved) }
            plan.draw(drawContext.canvas.nativeCanvas)
        }
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Two tapers, three liners, a thread hanging off each end, an explicit sealed body, bare shaft. */
    private fun fixture(): ShaftSpec {
        val inch = 25.4f
        return ShaftSpec(
            overallLengthMm = 300f * inch,
            tapers = listOf(
                Taper(id = "TA", startFromAftMm = 0f, lengthMm = 20f * inch, startDiaMm = 6f * inch, endDiaMm = 7.5f * inch,
                    keywayWidthMm = 1.5f * inch, keywayDepthMm = 0.75f * inch, keywayLengthMm = 14f * inch),
                Taper(id = "TF", startFromAftMm = 284f * inch, lengthMm = 16f * inch, startDiaMm = 8f * inch, endDiaMm = 6.5f * inch),
            ),
            threads = listOf(
                Threads(id = "THA", startFromAftMm = -6f * inch, lengthMm = 6f * inch, majorDiaMm = 4.5f * inch, tpi = 4f,
                    excludeFromOAL = true, isAftEnd = true),
                Threads(id = "THF", startFromAftMm = 300f * inch, lengthMm = 6f * inch, majorDiaMm = 4.5f * inch, tpi = 4f,
                    excludeFromOAL = true, isAftEnd = false),
            ),
            liners = listOf(
                Liner(id = "L1", startFromAftMm = 30f * inch, lengthMm = 30f * inch, odMm = 8.5f * inch),
                Liner(id = "L2", startFromAftMm = 140f * inch, lengthMm = 20f * inch, odMm = 8.5f * inch),
                Liner(id = "L3", startFromAftMm = 244f * inch, lengthMm = 30f * inch, odMm = 8.5f * inch),
            ),
            bodies = listOf(
                Body(id = "B1", startFromAftMm = 60f * inch, lengthMm = 20f * inch, diaMm = 8f * inch,
                    blendAftSeal = true, blendAftSealLenMm = 6f * inch, compressOnDrawing = false),
            ),
            autoBodyDiaMm = 8f * inch,
        )
    }
}
