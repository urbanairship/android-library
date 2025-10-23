/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.contacts.EmailRegistrationOptions
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.io.Serializable
import java.util.Date

@Composable
internal fun CreateEmailChannelScreen(
    viewModel: CreateEmailChannelViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    createdProperty: () -> CreateEmailChannelViewModel.PropertyValue? = { null }
) {

    createdProperty()?.let(viewModel::addProperty)

    DebugScreen(
        title = stringResource(id = ContactScreens.AddChannel.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel, onNavigateUp, onNavigate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(
    viewModel: CreateEmailChannelViewModel,
    onNavigateUp: () -> Unit,
    onNavigate: (String) -> Unit
) {
    LazyColumn {

        item {
            Section(title = "Channel Info") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        value = viewModel.email.value,
                        onValueChange = { viewModel.email.value = it.trim() },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false, keyboardType = KeyboardType.Email
                        )
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CreateEmailChannelViewModel.RegistrationType.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = viewModel.registrationType.value == entry,
                                onClick = { viewModel.registrationType.value = entry },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = CreateEmailChannelViewModel.RegistrationType.entries.size
                                )
                            ) {
                                Text(text = entry.toString().lowercase().capitalize(Locale.current))
                            }
                        }
                    }
                }
            }
        }

        item {
            if (viewModel.registrationType.value == CreateEmailChannelViewModel.RegistrationType.COMMERCIAL) {
                Spacer(Modifier.padding(top = 16.dp))

                Section(title = "Commercial Options") {
                    RowItem(
                        title = "Double Opt-In",
                        accessory = {
                            Switch(
                                checked = viewModel.doubleOptIn.value,
                                onCheckedChange = { viewModel.doubleOptIn.value = it }
                            )
                        }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.padding(top = 16.dp))

            Section(title = "Properties") {
                RowItem(
                    modifier = Modifier.clickable { onNavigate(ContactChannelScreens.EmailChannelAddProperty.route) },
                    title = "Add Property",
                    accessory = { Icon(painterResource(R.drawable.ic_chevron), contentDescription = "display") }
                )

                viewModel.properties.value.forEach { entry ->
                    RowItem(
                        title = "${entry.key}:${entry.value.stringValue}",
                        accessory = {
                            IconButton(onClick = { viewModel.removeProperty(entry.value) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = "delete")
                            }
                        }
                    )
                }
            }
        }

        item {
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

internal class CreateEmailChannelViewModel: ViewModel() {
    val email = mutableStateOf("")
    val doubleOptIn = mutableStateOf(false)
    val registrationType = mutableStateOf(RegistrationType.TRANSACTIONAL)
    val properties = mutableStateOf<Map<String, PropertyValue>>(emptyMap())

    val isComplete: Boolean
        get() = email.value.isNotEmpty()

    fun addProperty(value: PropertyValue) {
        val updated = properties.value.toMutableMap().apply { put(value.key, value) }
        properties.value = updated
    }

    fun removeProperty(property: PropertyValue) {
        val updated = properties.value.toMutableMap().apply { remove(property.key) }
        properties.value = updated
    }

    fun perform() {
        if (!Airship.isFlying || !isComplete) {
            return
        }

        val jsonProperties = JsonMap(properties.value.mapValues { it.value.jsonValue })
        val date = Date()

        val options = when(registrationType.value) {
            RegistrationType.TRANSACTIONAL -> EmailRegistrationOptions.options(
                transactionalOptedIn = date,
                doubleOptIn = false,
                properties = jsonProperties)
            RegistrationType.COMMERCIAL -> {
                if (doubleOptIn.value) {
                    EmailRegistrationOptions.options(
                        transactionalOptedIn = date,
                        doubleOptIn = true,
                        properties = jsonProperties)
                } else {
                    EmailRegistrationOptions.commercialOptions(
                        commercialOptedIn = date,
                        transactionalOptedIn = date,
                        properties = jsonProperties
                    )
                }
            }
        }

        Airship.contact.registerEmail(email.value, options)
    }

    sealed class PropertyValue(val key: String): Serializable {
        class BoolValue(key: String, val value: Boolean): PropertyValue(key)
        class StringValue(key: String, val value: String): PropertyValue(key)
        class NumberValue(key: String, val value: Double): PropertyValue(key)

        val stringValue: String
            get() {
                return when(this) {
                    is BoolValue -> value.toString()
                    is NumberValue -> value.toString()
                    is StringValue -> value
                }
            }

        val jsonValue: JsonValue
            get() {
                return when(this) {
                    is BoolValue -> JsonValue.wrap(value)
                    is NumberValue -> JsonValue.wrap(value)
                    is StringValue -> JsonValue.wrap(value)
                }
            }
    }

    enum class RegistrationType {
        TRANSACTIONAL, COMMERCIAL
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        CreateEmailChannelScreen()
    }
}
