package moe.youmu.avgps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class AvGpsAppTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsShowsMissingAccess() {
        compose.setContent {
            AvGpsApp(
                appState = AvGpsState(),
                unitSettings = UnitSettings(),
                missingAccess = listOf(RequiredAccess.BACKGROUND_LOCATION),
                showPermissionExplanation = false,
                onPermissionExplanationConfirmed = {},
                onPermissionExplanationDismissed = {},
                onOpenAccessSettings = {},
                onClearClients = {},
                onSetClientEnabled = { _, _ -> },
                onSetAltitudeUnit = {},
                onSetSpeedUnit = {},
                onSetDistanceUnit = {},
                onOpenSource = {},
            )
        }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Background location").assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsDisplayed()
    }
}
