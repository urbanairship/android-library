/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.UAirship
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.SwipeToDeleteRow
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.io.Serializable

@Composable
internal fun AddCustomEventScreen(
    viewModel: AddEventViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    createdProperty: () -> AddEventViewModel.PropertyValue? = { null }
) {

    createdProperty.invoke()?.let(viewModel::addProperty)

    DebugScreen(
        title = "Add Custom Event",
        navigation = TopBarNavigation.Back(onNavigateUp),
    ) {
        CreateEventView(
            viewModel = viewModel,
            onNavigate = onNavigate,
            onNavigateUp = onNavigateUp,
        )
    }
}

@Composable
internal fun CreateEventView(
    modifier: Modifier = Modifier.fillMaxWidth(),
    viewModel: AddEventViewModel,
    onNavigate: (String) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    Column(modifier.padding(horizontal = 8.dp)) {
        EventProperties(viewModel = viewModel, onNavigateUp = onNavigateUp)

        Spacer(modifier = Modifier.height(16.dp))

        Properties(viewModel = viewModel, onNavigate = onNavigate)
    }
}

@Composable
private fun EventProperties(viewModel: AddEventViewModel, onNavigateUp: () -> Unit = {},) {
    Section(
        title = "event properties",
        accessory = {
            IconButton(
                enabled = viewModel.isComplete,
                onClick = {
                    viewModel.save()
                    onNavigateUp()
                }) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        }
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            value = viewModel.name,
            onValueChange = { viewModel.name = viewModel.corrected(it) },
            label = { Text(text = "Event Name") },
            keyboardOptions = KeyboardOptions(autoCorrect = false)
        )

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            value = viewModel.eventValue,
            onValueChange = { viewModel.eventValue = viewModel.corrected(it) },
            label = { Text(text = "Event Value") },
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Number
            )
        )

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            value = viewModel.transactionId,
            onValueChange = { viewModel.transactionId = viewModel.corrected(it) },
            label = { Text(text = "Transaction ID") },
            keyboardOptions = KeyboardOptions(autoCorrect = false)
        )

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            value = viewModel.interactionId,
            onValueChange = { viewModel.interactionId = viewModel.corrected(it) },
            label = { Text(text = "Interaction ID") },
            keyboardOptions = KeyboardOptions(autoCorrect = false)
        )

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            value = viewModel.interactionType,
            onValueChange = { viewModel.interactionType = viewModel.corrected(it) },
            label = { Text(text = "Interaction Type") },
            keyboardOptions = KeyboardOptions(autoCorrect = false)
        )
    }
}

@Composable
private fun Properties(
    viewModel: AddEventViewModel,
    onNavigate: (String) -> Unit
) {
    Section(title = "Properties") {
        ListItem(
            modifier = Modifier.clickable { onNavigate(AnalyticsScreens.CreatePropertyAttribute.route) },
            headlineContent = { Text("Add Property", fontWeight = FontWeight.Medium) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")} )

        viewModel.properties.values.forEach { property ->
            SwipeToDeleteRow(
                content = { RowItem(title = property.key, details = property.stringValue) },
                onDelete = { viewModel.removeProperty(property) }
            )
        }
    }
}

internal class AddEventViewModel(): ViewModel() {
    var name by mutableStateOf("")
    var eventValue by mutableStateOf("1.0")
    var transactionId by mutableStateOf("")
    var interactionId by mutableStateOf("")
    var interactionType by mutableStateOf("")

    var properties by mutableStateOf(mapOf<String, PropertyValue>())
    val isComplete: Boolean
        get() { return name.trim().isNotEmpty() }

    fun corrected(value: String): String {
        return value.trim()
    }

    fun save() {
        if (!UAirship.isFlying || !isComplete) {
            return
        }

        val builder = CustomEvent.Builder(name)
            .setEventValue(eventValue.toDoubleOrNull() ?: 1.0)

        if (transactionId.isNotEmpty()) {
            builder.setTransactionId(transactionId)
        }

        if (interactionId.isNotEmpty() && interactionType.isNotEmpty()) {
            builder.setInteraction(interactionType, interactionId)
        }

        builder
            .setProperties(JsonMap(properties.mapValues { it.value.jsonValue }))
            .build()
            .track()
    }

    fun addProperty(property: PropertyValue) {
        properties = properties
            .toMutableMap()
            .apply { set(property.key, property) }
    }

    fun removeProperty(property: PropertyValue) {
        properties = properties
            .toMutableMap()
            .apply { remove(property.key) }
    }

    sealed class PropertyValue(val key: String): Serializable {
        class BoolValue(key: String, val value: Boolean): PropertyValue(key)
        class StringValue(key: String, val value: String): PropertyValue(key)
        class NumberValue(key: String, val value: Double): PropertyValue(key)
        class Json(key: String, val value: JsonValue): PropertyValue(key)

        val stringValue: String
            get() {
                return when(this) {
                    is BoolValue -> value.toString()
                    is Json -> value.toString()
                    is NumberValue -> value.toString()
                    is StringValue -> value
                }
            }

        val jsonValue: JsonValue
            get() {
                return when(this) {
                    is BoolValue -> JsonValue.wrap(value)
                    is Json -> value
                    is NumberValue -> JsonValue.wrap(value)
                    is StringValue -> JsonValue.wrap(value)
                }
            }
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddCustomEventPreview() {
    AirshipDebugTheme {
        AddCustomEventScreen()
    }
}
