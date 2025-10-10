package com.urbanairship.devapp.home

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Image
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.devapp.R
import com.urbanairship.devapp.ui.theme.PreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NamedUserScreen(
    viewModel: NamedUserViewModel = viewModel(),
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.named_user))
                },
                navigationIcon = {
                    IconButton(onNavigateUp) {
                        Image(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddings ->
        Surface(Modifier.padding(paddings)) {
            ScreenContent(viewModel, onNavigateUp)
        }
    }
}

@Composable
private fun ScreenContent(viewModel: NamedUserViewModel, onNavigateUp: () -> Unit) {
    Column {

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = "A named user is an identifier that maps multiple devices and channels to a specific individual.",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Light,
            fontSize = 14.sp)

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp),
            value = viewModel.userId.value,
            onValueChange = { viewModel.userId.value = it.trim()},
            label = { Text("Named User") },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onAny = {
                viewModel.save()
                onNavigateUp()
            })
        )
    }
}

internal class NamedUserViewModel: ViewModel() {
    val userId = mutableStateOf("")

    init {
        Airship.shared {
            userId.value = it.contact.namedUserId ?: ""
        }
    }

    fun save() {
        if (!Airship.isFlying) {
            return
        }

        val update = userId.value.trim()

        if (update.isEmpty()) {
            Airship.shared().contact.reset()
        } else {
            Airship.shared().contact.identify(update)
        }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    PreviewTheme {
        NamedUserScreen()
    }
}
