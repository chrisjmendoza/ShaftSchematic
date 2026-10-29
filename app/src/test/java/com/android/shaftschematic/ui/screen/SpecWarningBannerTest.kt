package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.Liner
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.ui.util.componentCardTitles
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.importJson
import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The warning banner names its parts and each line is a button to that part (on-device report: a
 * count gave no clue which part was wrong). The banner alone for the line mechanics, then the
 * whole editor on the on-device job for the selection the tap makes and the OAL move snackbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class SpecWarningBannerTest {

    @get:Rule
    val rule = createComposeRule()

    /** Five bodies each running past the 1000 mm end — five problems, AFT → FWD. */
    private val fivePast = ShaftSpec(
        overallLengthMm = 1000f,
        bodies = (1..5).map { i ->
            Body(id = "b$i", startFromAftMm = 900f + i, lengthMm = 200f, diaMm = 50f)
        },
    )

    private fun hostBanner(spec: ShaftSpec, onSelect: (String) -> Unit = {}) {
        rule.setContent {
            MaterialTheme { SpecWarningBanner(spec = spec, unit = UnitSystem.MILLIMETERS, onSelectComponent = onSelect) }
        }
        rule.waitForIdle()
    }

    @Test
    fun `lines name the parts, three at a time, and +N more opens the rest`() {
        hostBanner(fivePast)
        val titles = componentCardTitles(fivePast)

        rule.onNodeWithText("${titles["b1"]} runs 101 mm past the FWD end").assertIsDisplayed()
        rule.onAllNodesWithText("past the FWD end", substring = true).assertCountEquals(3)
        rule.onNodeWithTag("spec_warning_more").assert(hasText("+2 more"))

        rule.onNodeWithTag("spec_warning_more").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText("past the FWD end", substring = true).assertCountEquals(5)
        rule.onAllNodesWithTag("spec_warning_more").assertCountEquals(0)
    }

    @Test
    fun `a line is a 48 dp button that selects its part`() {
        val picked = mutableListOf<String>()
        hostBanner(fivePast) { picked += it }

        rule.onNodeWithTag(specWarningLineTag(1))
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(listOf("b2"), picked)
    }

    @Test
    fun `an overlap line selects the first part it names`() {
        val spec = ShaftSpec(
            overallLengthMm = 1000f,
            liners = listOf(
                Liner(id = "l1", startFromAftMm = 100f, lengthMm = 200f, odMm = 90f),
                Liner(id = "l2", startFromAftMm = 250f, lengthMm = 200f, odMm = 90f),
            ),
        )
        val picked = mutableListOf<String>()
        hostBanner(spec) { picked += it }
        val titles = componentCardTitles(spec)

        rule.onNodeWithText("${titles["l1"]} overlaps ${titles["l2"]}").performClick()

        assertEquals(listOf("l1"), picked)
    }

    @Test
    fun `dismiss hides the banner`() {
        hostBanner(fivePast)
        rule.onNodeWithTag("spec_warning_dismiss").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("spec_warning_banner").assertCountEquals(0)
    }

    // ── The whole editor, on the on-device job ────────────────────────────────

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/mid_taper_pac_fish.shaft")).readText()

    private fun hostEditor(vm: ShaftViewModel) {
        rule.setContent {
            MaterialTheme {
                ShaftEditorRoute(
                    vm = vm,
                    onNavigateHome = {},
                    onNew = {},
                    onOpen = {},
                    onSave = {},
                    onOpenSettings = {},
                    onOpenDeveloperOptions = {},
                    onExportPdf = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the OAL correction says what it moved, and the overlap line swings the carousel to the liner`() {
        val vm = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.importJson(fixture())
        hostEditor(vm)
        val fwdLiner = vm.spec.value.liners.maxBy { it.startFromAftMm }

        vm.onSetOverallLengthMm(234.1875f * 25.4f)
        rule.waitForIdle()

        rule.onNodeWithText("OAL 234 3/16\": moved FWD Liner 4 13/16\" aft (measured from FWD)").assertExists()
        rule.onAllNodesWithText("FWD Liner").assertCountEquals(0)

        rule.onNodeWithTag(specWarningLineTag(0)).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(fwdLiner.id, vm.selectedComponentId.value)
        rule.onNodeWithText("FWD Liner").assertExists()
    }
}
