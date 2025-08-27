/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.automations

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun AutomationRootScreen(
    viewModel: AutomationRootScreenViewModel = viewModel<DefaultAutomationRootScreenViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    DebugScreen(
        title = stringResource(id = AutomationScreens.Root.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel, onNavigate)
    }
}

@Composable
private fun ScreenContent(
    viewModel: AutomationRootScreenViewModel,
    onNavigate: (String) -> Unit
) {
    val displayInterval = viewModel.displayInterval

    Section(title = "In-App Automations") {
        Column {
            RowItem(
                modifier = Modifier.clickable { onNavigate(AutomationScreens.Automations.route) },
                title = "Automations",
                accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
            )

            RowItem(
                modifier = Modifier.clickable { onNavigate(AutomationScreens.Experiments.route) },
                title = "Experiments",
                accessory = { Icon(Icons.Default.ChevronRight, contentDescription = "display") }
            )

            ListItem(
                headlineContent = {
                    Text(text = "Display Interval", fontWeight = FontWeight.Medium)
                }, supportingContent = {
                    Slider(
                        modifier = Modifier.padding(start = 40.dp),
                        value = displayInterval.value.toFloat(),
                        onValueChange = { viewModel.setDisplayInterval(it.toLong()) },
                        steps = 200,
                        valueRange = 0F..200F
                    )
                }, trailingContent = {
                    Text(text = displayInterval.value.toString(), fontSize = 14.sp, fontWeight = FontWeight.Light)
                }
            )

            HorizontalDivider()
        }
    }

}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        AutomationRootScreen()
    }
}

internal interface AutomationRootScreenViewModel {
    val displayInterval: State<Long>
    fun setDisplayInterval(value: Long)
}

internal class DefaultAutomationRootScreenViewModel: AutomationRootScreenViewModel, ViewModel() {

    private val mutableState = mutableStateOf(0L)
    override val displayInterval: State<Long> = mutableState

    init {
        Airship.shared {
            mutableState.value = InAppAutomation.shared().inAppMessaging.displayInterval
        }
    }

    override fun setDisplayInterval(value: Long) {
        if (!Airship.isFlying) {
            return
        }

        InAppAutomation.shared().inAppMessaging.displayInterval = value
        mutableState.value = value
    }
}
