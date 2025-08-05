/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.UAirship
import com.urbanairship.contacts.SmsRegistrationOptions
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun CreateSmsChannelScreen(
    viewModel: CreateSmsChannelViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
) {

    DebugScreen(
        title = stringResource(id = ContactScreens.AddChannel.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel, onNavigateUp)
    }
}

@Composable
private fun ScreenContent(
    viewModel: CreateSmsChannelViewModel,
    onNavigateUp: () -> Unit
) {
    Section(title = "Channel Info") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                value = viewModel.msisdn.value,
                onValueChange = { viewModel.msisdn.value = it.trim()},
                label = { Text("MSISDN") },
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Phone)
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                value = viewModel.senderId.value,
                onValueChange = { viewModel.senderId.value = it.trim()},
                label = { Text("Sender ID") },
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Ascii)
            )

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                enabled = viewModel.isComplete,
                onClick = {
                    viewModel.perform()
                    onNavigateUp()
                }
            ) {
                Text(text = "Apply")
            }
        }
    }
}

internal class CreateSmsChannelViewModel: ViewModel() {
    val msisdn = mutableStateOf("")
    val senderId = mutableStateOf("")

    val isComplete: Boolean
        get() = msisdn.value.isNotEmpty() && senderId.value.isNotEmpty()

    fun perform() {
        if (!UAirship.isFlying || msisdn.value.isEmpty() || senderId.value.isEmpty()) {
            return
        }

        UAirship.shared().contact.registerSms(msisdn.value, SmsRegistrationOptions.options(senderId.value))
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        CreateSmsChannelScreen()
    }
}
