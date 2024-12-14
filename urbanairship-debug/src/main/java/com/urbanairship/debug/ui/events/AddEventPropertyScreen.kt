/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.json.JsonValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEventPropertyScreen(
    screenViewModel: EventPropertiesViewModel = viewModel(),
    onNavigateUp: (created: AddEventViewModel.PropertyValue?) -> Unit = {}
) {

    DebugScreen(
        title = "Add Property",
        navigation = TopBarNavigation.Back { onNavigateUp(null) }) {
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = screenViewModel.key,
                onValueChange = { screenViewModel.key = it},
                label = { Text("Name") }
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EventPropertiesViewModel.Types.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = screenViewModel.inputType == entry,
                        onClick = { screenViewModel.inputType = entry },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = EventPropertiesViewModel.Types.entries.size)
                    ) {
                        Text(text = entry.toString().lowercase().capitalize(Locale.current))
                    }
                }
            }

            ValueView(viewModel = screenViewModel)

            screenViewModel.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Light)
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                enabled = screenViewModel.canCreate,
                onClick = {
                    screenViewModel.makePayload()?.let {
                        onNavigateUp(it)
                    }
                }) {
                Text(text = "Create")
            }
        }
    }
}

@Composable
private fun ValueView(viewModel: EventPropertiesViewModel) {
    when(viewModel.inputType) {
        EventPropertiesViewModel.Types.BOOL -> {
            ListItem(
                headlineContent = { Text(viewModel.boolValue.toString()) },
                trailingContent = {
                    Switch(
                        checked = viewModel.boolValue,
                        onCheckedChange = { viewModel.boolValue = it }
                    )
                }
            )
        }
        EventPropertiesViewModel.Types.STRING -> {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.stringValue,
                onValueChange = { viewModel.stringValue = it.trim()},
                label = { Text(text = "String") },
                keyboardOptions = KeyboardOptions(autoCorrect = false)
            )
        }
        EventPropertiesViewModel.Types.NUMBER -> {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.numberValue,
                onValueChange = { viewModel.numberValue = it.trim()},
                label = { Text(text = "Number") },
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Number
                )
            )
        }
        EventPropertiesViewModel.Types.JSON -> {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.jsonValue,
                onValueChange = { viewModel.jsonValue = it},
                label = { Text(text = "JSON") },
                keyboardOptions = KeyboardOptions(autoCorrect = false)
            )
        }
    }
}

internal class EventPropertiesViewModel: ViewModel() {

    var key by mutableStateOf("")
    var inputType by mutableStateOf(Types.BOOL)

    var boolValue by mutableStateOf(false)
    var stringValue by mutableStateOf("")
    var numberValue by mutableStateOf("0")
    var jsonValue by mutableStateOf("")

    var error by mutableStateOf<String?>(null)

    val canCreate: Boolean
        get() {
            if (key.trim().isEmpty()) {
                return false
            }

            return when(inputType) {
                Types.BOOL -> true
                Types.STRING -> stringValue.trim().isNotEmpty()
                Types.NUMBER -> numberValue.trim().toDoubleOrNull() != null
                Types.JSON -> jsonValue.trim().isNotEmpty()
            }
        }

    fun makePayload(): AddEventViewModel.PropertyValue? {
        if (!canCreate) {
            return null
        }

        val propKey = key.trim()

        return when(inputType) {
            Types.BOOL -> AddEventViewModel.PropertyValue.BoolValue(propKey, boolValue)
            Types.STRING -> AddEventViewModel.PropertyValue.StringValue(propKey, stringValue.trim())
            Types.NUMBER -> AddEventViewModel.PropertyValue.NumberValue(propKey, numberValue.toDoubleOrNull() ?: 1.0)
            Types.JSON -> {
                try {
                    AddEventViewModel.PropertyValue.Json(propKey, JsonValue.parseString(jsonValue.trim()))
                } catch (ex: Exception) {
                    error = "Failed to parse the JSON"
                    null
                }
            }
        }
    }

    enum class Types {
        BOOL, STRING, NUMBER, JSON
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddEventPropertyPreview() {
    AirshipDebugTheme {
        AddEventPropertyScreen()
    }
}
