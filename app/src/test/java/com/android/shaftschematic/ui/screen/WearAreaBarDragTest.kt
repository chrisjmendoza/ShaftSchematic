package com.android.shaftschematic.ui.screen

import android.app.Application
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.resolved.ResolvedBody
import com.android.shaftschematic.ui.resolved.resolveComponents
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addWearPit
import com.android.shaftschematic.ui.viewmodel.addWearSpot
import com.android.shaftschematic.ui.viewmodel.updateWearSpot
import com.android.shaftschematic.util.UnitSystem
import kotlin.math.abs
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The wear detail canvas's start / end bars, through the real page and the REAL ViewModel
 * mutators the Wear tab binds (on-device request: drag a wear area's bars instead of typing its
 * length). A drag that starts on a bar moves that edge and writes ONCE, on release; a press
 * released in place is still the ordinary canvas tap; a body's page carries the same section.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class WearAreaBarDragTest {

    @get:Rule
    val rule = createComposeRule()

    private val inch = 25.4f
    private val eighth = inch / 8f
    private val liner = Liner(id = "L1", startFromAftMm = 500f, lengthMm = 1000f, odMm = 200f)
    private val spec = ShaftSpec(overallLengthMm = 2000f, autoBodyDiaMm = 180f, liners = listOf(liner))
    private val resolved = resolveComponents(spec)
    private val fwdRun = resolved.filterIsInstance<ResolvedBody>().single { it.startMmPhysical == 1500f }

    private var spotWrites = 0
    private var pitsPlaced = 0

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun vm(): ShaftViewModel = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun show(vm: ShaftViewModel, componentId: String = "L1") {
        rule.setContent {
            val record by vm.wearRecord.collectAsState()
            MaterialTheme {
                ComponentWearDetailOverlay(
                    componentId = componentId,
                    spec = spec,
                    resolvedComponents = resolved,
                    unit = UnitSystem.INCHES,
                    wearRecord = record,
                    onAddSpot = { vm.addWearSpot(it) },
                    onUpdateSpot = { id, s, l, d, n -> spotWrites++; vm.updateWearSpot(id, s, l, d, n) },
                    onUpdateSpotReference = { _, _ -> },
                    onRemoveSpot = {},
                    onAddPit = { id, mm, frac, size -> pitsPlaced++; vm.addWearPit(id, mm, frac, size) },
                    onRemovePit = {},
                    onAddDiaReading = { _, _, _ -> },
                    onUpdateDiaReading = { _, _ -> },
                    onRemoveDiaReading = {},
                    onClose = {},
                )
            }
        }
        rule.waitForIdle()
    }

    /** The canvas's own layout, from the same function the page draws and hit-tests with. */
    private fun layout(hostLengthMm: Float, hostDiaMm: Float): SegDetailLayout {
        val widthPx = rule.onNodeWithTag("wear_detail_canvas").fetchSemanticsNode().size.width.toFloat()
        return with(rule.density) {
            computeSegDetailLayout(
                widthPx = widthPx,
                stubRowHeightPx = 140.dp.toPx(),
                stubWidthPx = 24.dp.toPx(),
                lenMm = hostLengthMm,
                maxOdMm = hostDiaMm,
                edgePadPx = SEG_EDGE_PAD_DP.dp.toPx(),
            )
        }
    }

    private fun onGrid(mm: Float) = abs(mm / eighth - (mm / eighth).roundToLong()) < 1e-3f

    private fun vmWithSpot(): ShaftViewModel {
        val vm = vm()
        vm.addWearSpot("L1")
        val id = vm.wearRecord.value.spots.single().id
        vm.updateWearSpot(id, startMm = 10f * inch, lengthMm = 10f * inch, minDiaMm = 0f, note = "")
        idle()
        return vm
    }

    @Test
    fun `dragging the end bar follows the finger and writes once, on release`() {
        val vm = vmWithSpot()
        show(vm)
        val lay = layout(liner.lengthMm, liner.odMm)
        val endBarX = lay.startPx + 20f * inch * lay.pxPerMm
        val dragPx = 40f

        rule.onNodeWithTag("wear_detail_canvas").performTouchInput {
            down(Offset(endBarX, lay.cy))
            moveBy(Offset(dragPx / 2f, 0f))
            moveBy(Offset(dragPx / 2f, 0f))
        }
        rule.waitForIdle()
        assertEquals("no write while the finger is down", 0, spotWrites)
        assertEquals(10f * inch, vm.wearRecord.value.spots.single().lengthMm, 0f)

        rule.onNodeWithTag("wear_detail_canvas").performTouchInput { up() }
        rule.waitForIdle()
        idle()

        assertEquals("ONE write for the whole drag", 1, spotWrites)
        assertEquals("a drag on a bar is not a tap", 0, pitsPlaced)
        val spot = vm.wearRecord.value.spots.single()
        assertEquals("the start bar was not touched", 10f * inch, spot.startMm, 0f)
        assertTrue("the length lands on the 1/8 in grid", onGrid(spot.lengthMm))
        val expected = 10f * inch + dragPx / lay.pxPerMm
        assertTrue(
            "length ${spot.lengthMm} follows the drag (≈ $expected)",
            abs(spot.lengthMm - expected) <= eighth,
        )
    }

    @Test
    fun `dragging the start bar keeps the end where it was`() {
        val vm = vmWithSpot()
        show(vm)
        val lay = layout(liner.lengthMm, liner.odMm)
        val startBarX = lay.startPx + 10f * inch * lay.pxPerMm

        rule.onNodeWithTag("wear_detail_canvas").performTouchInput {
            down(Offset(startBarX, lay.cy))
            moveBy(Offset(-15f, 0f))
            moveBy(Offset(-15f, 0f))
            up()
        }
        rule.waitForIdle()
        idle()

        assertEquals(1, spotWrites)
        val spot = vm.wearRecord.value.spots.single()
        assertTrue("the start moved AFT", spot.startMm < 10f * inch)
        assertTrue(onGrid(spot.startMm))
        assertEquals("the end stayed put", 20f * inch, spot.startMm + spot.lengthMm, 1e-3f)
    }

    @Test
    fun `a press on a bar released in place is still a tap - it places a pit and writes no spot`() {
        val vm = vmWithSpot()
        show(vm)
        val lay = layout(liner.lengthMm, liner.odMm)
        val endBarX = lay.startPx + 20f * inch * lay.pxPerMm

        rule.onNodeWithTag("wear_detail_canvas").performTouchInput {
            down(Offset(endBarX, lay.cy))
            up()
        }
        rule.waitForIdle()
        idle()

        assertEquals(0, spotWrites)
        assertEquals(1, pitsPlaced)
    }

    @Test
    fun `a drag that starts away from every bar writes nothing`() {
        val vm = vmWithSpot()
        show(vm)
        val lay = layout(liner.lengthMm, liner.odMm)
        // Mid-band: 15 in along, well outside both bars' reach.
        val midX = lay.startPx + 15f * inch * lay.pxPerMm
        assertTrue("premise: outside the bars' reach", 5f * inch * lay.pxPerMm > WEAR_BAR_REACH_DP)

        rule.onNodeWithTag("wear_detail_canvas").performTouchInput {
            down(Offset(midX, lay.cy))
            moveBy(Offset(30f, 0f))
            up()
        }
        rule.waitForIdle()
        idle()

        assertEquals(0, spotWrites)
        assertEquals(10f * inch, vm.wearRecord.value.spots.single().lengthMm, 0f)
    }

    @Test
    fun `a bare-shaft body's page carries a wear area and drags its end bar`() {
        val vm = vm()
        vm.addWearSpot(fwdRun.id)
        idle()
        show(vm, componentId = fwdRun.id)
        val added = vm.wearRecord.value.spots.single()
        assertEquals("keyed to the body run", fwdRun.id, added.linerId)
        // The host-edge references name the body here.
        rule.onNodeWithText("Body AFT").assertExists()
        rule.onNodeWithText("Body FWD").assertExists()

        val hostLen = fwdRun.endMmPhysical - fwdRun.startMmPhysical
        val lay = layout(hostLen, maxOf(fwdRun.diaMm, liner.odMm))
        val endBarX = lay.startPx + added.lengthMm * lay.pxPerMm
        rule.onNodeWithTag("wear_detail_canvas").performTouchInput {
            down(Offset(endBarX + 4f, lay.cy))
            moveBy(Offset(25f, 0f))
            moveBy(Offset(25f, 0f))
            up()
        }
        rule.waitForIdle()
        idle()

        assertEquals(1, spotWrites)
        val dragged = vm.wearRecord.value.spots.single()
        assertEquals(0f, dragged.startMm, 0f)
        assertTrue("the area grew", dragged.lengthMm > added.lengthMm)
        assertTrue(onGrid(dragged.lengthMm))
    }
}
