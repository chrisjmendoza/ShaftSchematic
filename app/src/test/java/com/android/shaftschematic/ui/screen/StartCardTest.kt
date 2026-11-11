package com.android.shaftschematic.ui.screen

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.ui.util.buildLinerTitleById
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.ui.viewmodel.addLinerAt
import com.android.shaftschematic.ui.viewmodel.addThreadAt
import com.android.shaftschematic.ui.viewmodel.exportJson
import com.android.shaftschematic.ui.viewmodel.importJson
import com.android.shaftschematic.ui.viewmodel.removeLiner
import com.android.shaftschematic.ui.viewmodel.removeThread
import com.android.shaftschematic.ui.viewmodel.startFinalSpec
import com.android.shaftschematic.util.toMmOrNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor's start card, hosted through the whole editor so the wiring is what is pinned: it is
 * the carousel's first page whatever the shaft holds, a new or opened document lands on it, its
 * Overall Length commits the way every card field commits (the Save bar, IME Done, a blur after a
 * change — never per keystroke, never an empty value), its shaft diameter keeps the sections typed
 * on their own cards, its project information reaches the document, and on the Final tab its
 * geometry writes the FINAL drawing only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class StartCardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun freshVm() = ShaftViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun host(vm: ShaftViewModel) {
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

    private fun mm(text: String, vm: ShaftViewModel): Float = toMmOrNull(text, vm.unit.value)!!

    private fun fieldText(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performScrollTo()
        rule.onNodeWithTag(tag).requestFocus()
        rule.onNodeWithTag(tag).performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun save() {
        rule.onNodeWithTag(CARD_SAVE_TAG).performClick()
        rule.waitForIdle()
    }

    private fun assertOnStartCard() {
        rule.onNodeWithTag(START_CARD_TAG).assertIsDisplayed()
    }

    private fun assertOffStartCard() {
        rule.onAllNodesWithTag(START_CARD_TAG).assertCountEquals(0)
    }

    /* ── Always the first card ───────────────────────────────────────────────── */

    @Test
    fun `an empty document opens on the start card with no save bar`() {
        host(freshVm())

        assertOnStartCard()
        rule.onNodeWithTag(OAL_FIELD_TAG).assertIsDisplayed()
        rule.onNodeWithTag(START_CARD_SHAFT_DIA_TAG).assertIsDisplayed()
        rule.onAllNodesWithTag(CARD_SAVE_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun `an opened document lands on the start card`() {
        val built = freshVm().also {
            it.onSetOverallLengthMm(3000f)
            it.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
        }
        val vm = freshVm()
        host(vm)

        vm.importJson(built.exportJson())
        rule.waitForIdle()

        assertOnStartCard()
    }

    @Test
    fun `the start card stays first as components come and go`() {
        val vm = freshVm()
        host(vm)
        vm.onSetOverallLengthMm(3000f)

        // An add lands on the new component's card, one past the start card.
        vm.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
        rule.waitForIdle()
        assertOffStartCard()

        // A component AFT of everything is still never ahead of the start card.
        vm.addThreadAt(startMm = 0f, lengthMm = 100f, majorDiaMm = 100f, pitchMm = 4f, excludeFromOAL = false)
        rule.waitForIdle()
        assertOffStartCard()
        vm.selectComponentById(null)
        rule.waitForIdle()
        assertOnStartCard()

        val linerId = vm.spec.value.liners.single().id
        val threadId = vm.spec.value.threads.single().id
        vm.removeLiner(linerId)
        vm.removeThread(threadId)
        rule.waitForIdle()
        assertOnStartCard()
    }

    @Test
    fun `a selection from the drawing goes to that component's card, past the start card`() {
        val vm = freshVm().also {
            it.onSetOverallLengthMm(3000f)
            it.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
            it.selectComponentById(null)
        }
        host(vm)
        assertOnStartCard()

        // The preview tap's selection path.
        val linerId = vm.spec.value.liners.single().id
        vm.selectComponentById(linerId)
        rule.waitForIdle()

        assertOffStartCard()
        rule.onNodeWithText(buildLinerTitleById(vm.spec.value).getValue(linerId))
            .assertIsDisplayed()
    }

    @Test
    fun `the toolbar's project information button goes to the start card`() {
        val vm = freshVm().also {
            it.onSetOverallLengthMm(3000f)
            it.addLinerAt(startMm = 1200f, lengthMm = 400f, odMm = 160f)
        }
        host(vm)
        assertOffStartCard()

        rule.onNodeWithTag("toolbar_project_info").performClick()
        rule.waitForIdle()

        assertOnStartCard()
    }

    /* ── Overall Length commits like a card field ────────────────────────────── */

    @Test
    fun `a typed length waits for the save bar and lands once`() {
        val vm = freshVm().also { it.onSetOverallLengthMm(1000f) }
        host(vm)

        type(OAL_FIELD_TAG, "120")
        assertEquals("a keystroke commits nothing", 1000f, vm.spec.value.overallLengthMm)
        rule.onNodeWithTag(CARD_SAVE_BAR_TAG).assertIsDisplayed()
        rule.onAllNodes(hasAnyAncestor(hasTestTag(CARD_SAVE_BAR_TAG)) and hasText(START_CARD_TITLE, substring = true))
            .assertCountEquals(1)

        save()

        assertEquals(mm("120", vm), vm.spec.value.overallLengthMm)
        rule.onAllNodesWithTag(CARD_SAVE_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun `IME Done commits the length`() {
        val vm = freshVm()
        host(vm)

        type(OAL_FIELD_TAG, "96 1/2")
        rule.onNodeWithTag(OAL_FIELD_TAG).performImeAction()
        rule.waitForIdle()

        assertEquals(mm("96 1/2", vm), vm.spec.value.overallLengthMm)
    }

    @Test
    fun `cancel restores the stored length and commits nothing`() {
        val vm = freshVm().also { it.onSetOverallLengthMm(1000f) }
        host(vm)
        val stored = fieldText(OAL_FIELD_TAG)

        type(OAL_FIELD_TAG, "120")
        rule.onNodeWithTag(CARD_CANCEL_TAG).performClick()
        rule.waitForIdle()

        assertEquals(1000f, vm.spec.value.overallLengthMm)
        assertEquals(stored, fieldText(OAL_FIELD_TAG))
        rule.onAllNodesWithTag(CARD_SAVE_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun `an empty length commits nothing and the stored length returns`() {
        val vm = freshVm().also { it.onSetOverallLengthMm(1000f) }
        host(vm)
        val stored = fieldText(OAL_FIELD_TAG)

        rule.onNodeWithTag(OAL_FIELD_TAG).requestFocus()
        rule.onNodeWithTag(OAL_FIELD_TAG).performTextClearance()
        rule.onNodeWithTag(OAL_FIELD_TAG).performImeAction()
        rule.waitForIdle()

        assertEquals("never zeroes the shaft", 1000f, vm.spec.value.overallLengthMm)
        assertEquals(stored, fieldText(OAL_FIELD_TAG))
    }

    @Test
    fun `a length not typed yet is no error, one shorter than the parts is`() {
        val vm = freshVm()
        host(vm)
        rule.onNodeWithTag(OAL_FIELD_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))

        vm.importJson(
            freshVm().also {
                it.onSetOverallLengthMm(1000f)
                it.addLinerAt(startMm = 900f, lengthMm = 400f, odMm = 160f)
            }.exportJson()
        )
        rule.waitForIdle()

        rule.onNodeWithTag(OAL_FIELD_TAG).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    /* ── Shaft diameter keeps the sections typed on their cards ──────────────── */

    @Test
    fun `the shaft diameter writes the shaft-wide value and keeps a section's own`() {
        val vm = freshVm().also {
            it.onSetOverallLengthMm(1000f)
            it.addLinerAt(startMm = 400f, lengthMm = 200f, odMm = 200f)
            it.setAutoSectionDiaMm(600f, 1000f, 150f)
            it.selectComponentById(null)
        }
        host(vm)
        val sections = vm.spec.value.autoDiaOverrides

        type(START_CARD_SHAFT_DIA_TAG, "5")
        rule.onNodeWithTag(START_CARD_SHAFT_DIA_TAG).performImeAction()
        rule.waitForIdle()

        rule.onAllNodesWithTag("autobody_dia_scope_dialog").assertCountEquals(0)
        assertEquals(mm("5", vm), vm.spec.value.autoBodyDiaMm)
        assertEquals("a section typed on its card stays as typed", sections, vm.spec.value.autoDiaOverrides)
        rule.onNodeWithTag(START_CARD_DIA_NOTE_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `clearing the shaft diameter returns the shaft to the derived Ø`() {
        val vm = freshVm()
        vm.importJson(freshVm().also { it.onSetOverallLengthMm(1000f); it.setAutoBodyDiaMm(127f) }.exportJson())
        host(vm)

        rule.onNodeWithTag(START_CARD_SHAFT_DIA_TAG).requestFocus()
        rule.onNodeWithTag(START_CARD_SHAFT_DIA_TAG).performTextClearance()
        rule.onNodeWithTag(START_CARD_SHAFT_DIA_TAG).performImeAction()
        rule.waitForIdle()

        assertEquals(0f, vm.spec.value.autoBodyDiaMm)
        assertEquals("", fieldText(START_CARD_SHAFT_DIA_TAG))
    }

    /* ── Project information ─────────────────────────────────────────────────── */

    @Test
    fun `project fields reach the document through the card`() {
        val vm = freshVm()
        host(vm)

        type(PROJECT_INFO_CUSTOMER_TAG, "Acme Marine")
        assertEquals("typing alone commits nothing", "", vm.customer.value)
        rule.onNodeWithTag(CARD_SAVE_BAR_TAG).assertIsDisplayed()
        save()
        assertEquals("Acme Marine", vm.customer.value)

        // Moving on from a field lands it, as a blur does on every card field.
        type(PROJECT_INFO_VESSEL_TAG, "Northern Star")
        type(PROJECT_INFO_JOB_NUMBER_TAG, "24-1138")
        assertEquals("Northern Star", vm.vessel.value)
        save()
        assertEquals("24-1138", vm.jobNumber.value)

        type(PROJECT_INFO_NOTES_TAG, "wiped hard")
        save()
        assertEquals("wiped hard", vm.notes.value)

        rule.onNodeWithTag(PROJECT_INFO_POSITION_TAG).performScrollTo()
        rule.onNodeWithText(ShaftPosition.OTHER.uiLabel()).performClick()
        rule.onNodeWithText(ShaftPosition.STBD.uiLabel()).performClick()
        rule.waitForIdle()
        assertEquals(ShaftPosition.STBD, vm.shaftPosition.value)

        // And they read back from the document.
        assertEquals("Acme Marine", fieldText(PROJECT_INFO_CUSTOMER_TAG))
        assertEquals("24-1138", fieldText(PROJECT_INFO_JOB_NUMBER_TAG))
    }

    @Test
    fun `cancel puts a project field back and commits nothing`() {
        val vm = freshVm().also { it.setCustomer("Acme Marine") }
        host(vm)

        type(PROJECT_INFO_CUSTOMER_TAG, "Typo")
        rule.onNodeWithTag(CARD_CANCEL_TAG).performClick()
        rule.waitForIdle()

        assertEquals("Acme Marine", vm.customer.value)
        assertEquals("Acme Marine", fieldText(PROJECT_INFO_CUSTOMER_TAG))
    }

    /* ── The Final tab writes the FINAL drawing only ─────────────────────────── */

    @Test
    fun `on the final tab the length and diameter write the final drawing only`() {
        val vm = freshVm().also {
            it.onSetOverallLengthMm(1000f)
            it.startFinalSpec()
            it.selectComponentById(null)
        }
        val original = vm.spec.value
        rule.setContent { MaterialTheme { FinalRoute(vm = vm) } }
        rule.waitForIdle()
        assertOnStartCard()

        type(OAL_FIELD_TAG, "1200")
        save()
        type(START_CARD_SHAFT_DIA_TAG, "6")
        save()

        assertEquals(mm("1200", vm), vm.finalSpec.value!!.overallLengthMm)
        assertEquals(mm("6", vm), vm.finalSpec.value!!.autoBodyDiaMm)
        assertEquals("the original is untouched", original, vm.spec.value)
    }
}
