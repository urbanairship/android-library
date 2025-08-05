/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.UAirship
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun NamedUserScreen(
    viewModel: NamedUserViewModel = viewModel(),
    onNavigateUp: () -> Unit = {}
) {

    DebugScreen(
        title = stringResource(id = ContactScreens.NamedUser.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel, onNavigateUp)
    }
}

@Composable
private fun ScreenContent(viewModel: NamedUserViewModel, onNavigateUp: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = "add-tag") {
        focusRequester.requestFocus()
    }
    Column {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp)
                .focusRequester(focusRequester),
            value = viewModel.userId.value,
            onValueChange = { viewModel.userId.value = it.trim()},
            label = { Text("Named User") },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onAny = {
                viewModel.save()
                onNavigateUp()
            })
        )

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = "An empty value does not indicate the device does not have a named user. The SDK only knows about the Named User ID if set through the SDK.",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Light,
            fontSize = 14.sp)
    }
}

internal class NamedUserViewModel: ViewModel() {
    val userId = mutableStateOf("")

    init {
        UAirship.shared {
            userId.value = it.contact.namedUserId ?: ""
        }
    }

    fun save() {
        if (!UAirship.isFlying) {
            return
        }

        val update = userId.value.trim()

        if (update.isEmpty()) {
            UAirship.shared().contact.reset()
        } else {
            UAirship.shared().contact.identify(update)
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        NamedUserScreen()
    }
}
