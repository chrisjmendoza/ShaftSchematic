package com.android.shaftschematic.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import com.android.shaftschematic.model.Body
import com.android.shaftschematic.model.ShaftPosition
import com.android.shaftschematic.model.ShaftSpec
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorSetting
import com.android.shaftschematic.util.UnitSystem
import org.junit.Rule
import org.junit.Test

class EditorTopBarExportPdfTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exportPdf_disabledWhenNoComponents_andShowsMessageOnTap() {
        val emptySpec = ShaftSpec(overallLengthMm = 0f)

        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            ShaftScreen(
                resetNonce = 0,
                spec = emptySpec,
                unit = UnitSystem.MILLIMETERS,
                overallIsManual = false,
                unitLocked = false,
                customer = "",
                vessel = "",
                jobNumber = "",
                shaftPosition = ShaftPosition.OTHER,
                notes = "",
                showGrid = false,
                showOalDebugLabel = false,
                showOalHelperLine = false,
                showOalInPreviewBox = false,
                showComponentDebugLabels = false,
                showRenderLayoutDebugOverlay = false,
                showRenderOalMarkers = false,
                showComponentArrows = false,
                componentArrowWidthDp = 40,
                previewOutline = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewBodyFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewTaperFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewLinerFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewThreadFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewThreadHatch = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewBlackWhiteOnly = false,
                sessionAddDefaults = com.android.shaftschematic.ui.viewmodel.SessionAddDefaults.initial(),
                onSetUnit = {},
                onToggleGrid = {},
                onSetCustomer = {},
                onSetVessel = {},
                onSetJobNumber = {},
                onSetShaftPosition = {},
                onSetNotes = {},
                onSetOverallLengthRaw = {},
                onSetOverallLengthMm = {},
                onSetOverallIsManual = {},
                onAddBody = { _, _, _ -> },
                onAddTaper = { _, _, _, _ -> },
                onAddThread = { _, _, _, _, _ -> },
                onAddLiner = { _, _, _ -> },
                onUpdateBody = { _, _, _, _ -> },
                onUpdateTaper = { _, _, _, _, _ -> },
                onUpdateTaperKeyway = { _, _, _, _, _ -> },
                onUpdateThread = { _, _, _, _, _ -> },
                onUpdateLiner = { _, _, _, _ -> },
                onUpdateLinerLabel = { _, _ -> },
                onSetThreadExcludeFromOal = { _, _ -> },
                onRemoveBody = {},
                onRemoveTaper = {},
                onRemoveThread = {},
                onRemoveLiner = {},
                snackbarHostState = snackbarHostState,
                onNavigateHome = {},
                onNew = {},
                onOpen = {},
                onSave = {},
                onExportPdf = {},
                onOpenSettings = {},
                onSendFeedback = {},
                onOpenDeveloperOptions = {},
                devOptionsEnabled = false,
                canUndo = false,
                canRedo = false,
                onUndo = {},
                onRedo = {},
            )
        }

        composeRule.onNodeWithTag("toolbar_export_pdf").assertIsNotEnabled()

        // Even though the IconButton is disabled, we attach a pointer handler to show a snackbar.
        composeRule.onNodeWithTag("toolbar_export_pdf_container").performTouchInput { click() }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                "Please add at least 1 component",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(
            "Please add at least 1 component",
            substring = true
        ).assertExists()
    }

    @Test
    fun exportPdf_enabledWhenAtLeastOneComponentExists() {
        val specWithBody = ShaftSpec(
            overallLengthMm = 50f,
            bodies = listOf(
                Body(startFromAftMm = 0f, lengthMm = 10f, diaMm = 10f)
            )
        )

        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            ShaftScreen(
                resetNonce = 0,
                spec = specWithBody,
                unit = UnitSystem.MILLIMETERS,
                overallIsManual = false,
                unitLocked = false,
                customer = "",
                vessel = "",
                jobNumber = "",
                shaftPosition = ShaftPosition.OTHER,
                notes = "",
                showGrid = false,
                showOalDebugLabel = false,
                showOalHelperLine = false,
                showOalInPreviewBox = false,
                showComponentDebugLabels = false,
                showRenderLayoutDebugOverlay = false,
                showRenderOalMarkers = false,
                showComponentArrows = false,
                componentArrowWidthDp = 40,
                previewOutline = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewBodyFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewTaperFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewLinerFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewThreadFill = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewThreadHatch = PreviewColorSetting(preset = PreviewColorPreset.TRANSPARENT),
                previewBlackWhiteOnly = false,
                sessionAddDefaults = com.android.shaftschematic.ui.viewmodel.SessionAddDefaults.initial(),
                onSetUnit = {},
                onToggleGrid = {},
                onSetCustomer = {},
                onSetVessel = {},
                onSetJobNumber = {},
                onSetShaftPosition = {},
                onSetNotes = {},
                onSetOverallLengthRaw = {},
                onSetOverallLengthMm = {},
                onSetOverallIsManual = {},
                onAddBody = { _, _, _ -> },
                onAddTaper = { _, _, _, _ -> },
                onAddThread = { _, _, _, _, _ -> },
                onAddLiner = { _, _, _ -> },
                onUpdateBody = { _, _, _, _ -> },
                onUpdateTaper = { _, _, _, _, _ -> },
                onUpdateTaperKeyway = { _, _, _, _, _ -> },
                onUpdateThread = { _, _, _, _, _ -> },
                onUpdateLiner = { _, _, _, _ -> },
                onUpdateLinerLabel = { _, _ -> },
                onSetThreadExcludeFromOal = { _, _ -> },
                onRemoveBody = {},
                onRemoveTaper = {},
                onRemoveThread = {},
                onRemoveLiner = {},
                snackbarHostState = snackbarHostState,
                onNavigateHome = {},
                onNew = {},
                onOpen = {},
                onSave = {},
                onExportPdf = {},
                onOpenSettings = {},
                onSendFeedback = {},
                onOpenDeveloperOptions = {},
                devOptionsEnabled = false,
                canUndo = false,
                canRedo = false,
                onUndo = {},
                onRedo = {},
            )
        }

        composeRule.onNodeWithTag("toolbar_export_pdf").assertIsEnabled()
    }
}
