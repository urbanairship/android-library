/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Provider
import com.urbanairship.UAirship
import com.urbanairship.channel.AttributeEditor
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import java.util.Date

@Composable
internal fun AttributeEditScreen(
    editorProvider: Provider<AttributeEditor?>,
    viewModel: AttributeEditViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
) {
    viewModel.editorProvider = editorProvider

    DebugScreen(
        title = stringResource(id = ChannelInfoScreens.Attributes.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(viewModel: AttributeEditViewModel) {
    Section(title = "Attribute Info") {
        Column(modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)) {

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()) {
                AttributeEditViewModel.Action.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = viewModel.action.value == entry,
                        onClick = { viewModel.action.value = entry },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AttributeEditViewModel.Action.entries.size)
                    ) {
                        Text(text = entry.toString().lowercase().capitalize(Locale.current))
                    }
                }
            }

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                value = viewModel.name.value,
                onValueChange = { viewModel.name.value = it.trim()},
                label = { Text("Attribute") },
                keyboardOptions = KeyboardOptions(autoCorrect = false)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()) {
                AttributeEditViewModel.DataType.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = viewModel.dataType.value == entry,
                        onClick = { viewModel.dataType.value = entry },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AttributeEditViewModel.DataType.entries.size)
                    ) {
                        Text(text = entry.toString().lowercase().capitalize(Locale.current))
                    }
                }
            }

            inputValue(viewModel = viewModel)

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                enabled = viewModel.isComplete,
                onClick = { viewModel.perform() }
            ) {
                Text(text = "Apply")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun inputValue(viewModel: AttributeEditViewModel) {
    when(viewModel.dataType.value) {
        AttributeEditViewModel.DataType.TEXT -> {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                value = viewModel.textValue.value,
                onValueChange = { viewModel.textValue.value = it.trim()},
                label = { Text("Text") },
                keyboardOptions = KeyboardOptions(autoCorrect = false)
            )
        }
        AttributeEditViewModel.DataType.NUMBER -> {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                value = viewModel.numberValue.value,
                onValueChange = { viewModel.numberValue.value = it.trim()},
                label = { Text("Number") },
                keyboardOptions = KeyboardOptions(autoCorrect = false, keyboardType = KeyboardType.Number)
            )
        }
        AttributeEditViewModel.DataType.DATE -> {
            val state = rememberDatePickerState( viewModel.dateValue.value.time )

            state.selectedDateMillis?.let {
                viewModel.dateValue.value = Date(it)
            }

            DatePicker(state = state)
        }
    }
}

internal class AttributeEditViewModel: ViewModel() {
    val action = mutableStateOf(Action.ADD)
    val name = mutableStateOf("")
    val dataType = mutableStateOf(DataType.TEXT)

    val textValue = mutableStateOf("")
    val numberValue = mutableStateOf("0")
    val dateValue = mutableStateOf(Date())

    var editorProvider: Provider<AttributeEditor?>? = null

    val isComplete: Boolean
        get() {
            if (name.value.isEmpty()) {
                return false
            }

            return when(action.value) {
                Action.REMOVE -> true
                Action.ADD -> {
                    when(dataType.value) {
                        DataType.TEXT -> textValue.value.isNotEmpty()
                        DataType.NUMBER -> numberValue.value.toDoubleOrNull() != null
                        DataType.DATE -> true
                    }
                }
            }
        }

    enum class Action {
        ADD, REMOVE
    }

    enum class DataType {
        TEXT, NUMBER, DATE
    }

    fun perform() {
        if (!UAirship.isFlying() || !isComplete) {
            return
        }

        val editor = editorProvider?.get() ?: return

        when(action.value) {
            Action.REMOVE -> editor.removeAttribute(name.value)
            Action.ADD -> {
                when(dataType.value) {
                    DataType.TEXT -> editor.setAttribute(name.value, textValue.value)
                    DataType.NUMBER -> editor.setAttribute(name.value, numberValue.value.toDoubleOrNull() ?: 0.0)
                    DataType.DATE -> editor.setAttribute(name.value, dateValue.value)
                }
            }
        }
        editor.apply()

        name.value = ""
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        AttributeEditScreen({ null })
    }
}
