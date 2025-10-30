/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Airship
import com.urbanairship.debug.R
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Composable
internal fun AnalyticsIdentifiersScreen(
    viewModel: AnalyticsIdentifierViewModel = viewModel<DefaultAnalyticsIdentifierViewModel>(),
    onNavigateUp: () -> Unit = {}
) {

    val showAddDialog = remember { mutableStateOf(false) }

    DebugScreen(
        title = stringResource(id = AnalyticsScreens.AssociatedIdentifiers.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp),
        actionButton = {
            FloatingActionButton(
                onClick = { showAddDialog.value = true },
                shape = CircleShape) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add tag")
            }
        }
    ) {
        ScreenContent(viewModel = viewModel)

        if (showAddDialog.value) {
            AddIdentifierDialog { identifier ->
                identifier?.let(viewModel::addIdentifier)

                showAddDialog.value = false
            }
        }
    }
}

@Composable
private fun ScreenContent(viewModel: AnalyticsIdentifierViewModel) {
    val identifiers = viewModel.identifiers
        .collectAsState(emptyList())
        .value

    Section(title = "Identifiers") {
        identifiers.forEach { identifier ->
            RowItem(
                title = "${identifier.name}: ${identifier.value}",
                accessory = {
                    IconButton(onClick = { viewModel.remove(identifier) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                    }
                }
            )
        }
    }
}

@Composable
private fun AddIdentifierDialog(onDismiss: (Identifier?) -> Unit) {
    val nameState = remember { mutableStateOf<String?>(null) }
    val valueState = remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = "add-identifier") {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { onDismiss(null) },
        confirmButton = {
            TextButton(onClick = {
                val name = nameState.value
                val value = valueState.value

                if (name != null && value != null) {
                    onDismiss(Identifier(name, value))
                } else {
                    onDismiss(null)
                }
            }) {
                Text("Create")
            }
        },
        title = { Text(text = "Add New Identifier") },
        text = {
            Column {
                TextField(
                    modifier = androidx.compose.ui.Modifier.focusRequester(focusRequester),
                    value = nameState.value ?: "",
                    label = { Text("Name") },
                    onValueChange = { nameState.value = it.trim() }
                )
                TextField(
                    value = valueState.value ?: "",
                    label = { Text("Value") },
                    onValueChange = { valueState.value = it.trim() }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(null) }) {
                Text("Cancel")
            }
        }
    )
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IdentifiersScreenPreview() {
    AirshipDebugTheme {
        AnalyticsIdentifiersScreen()
    }
}

internal interface AnalyticsIdentifierViewModel {
    val identifiers: Flow<List<Identifier>>

    fun addIdentifier(item: Identifier)
    fun remove(item: Identifier)
}

internal class DefaultAnalyticsIdentifierViewModel: AnalyticsIdentifierViewModel, ViewModel() {

    private val _identifiersFlow = MutableStateFlow<List<Identifier>>(emptyList())
    override val identifiers: Flow<List<Identifier>> = _identifiersFlow.asStateFlow()

    init {
        Airship.onReady {
            refresh()
        }
    }

    override fun addIdentifier(item: Identifier) {
        if (!Airship.isFlying || _identifiersFlow.value.contains(item)) {
            return
        }

        Airship.analytics.editAssociatedIdentifiers {
            addIdentifier(item.name, item.value)
        }

        refresh()
    }

    override fun remove(item: Identifier) {
        if (!Airship.isFlying ||  !_identifiersFlow.value.contains(item)) {
            return
        }

        Airship.analytics.editAssociatedIdentifiers {
            removeIdentifier(item.name)
        }

        refresh()
    }

    private fun refresh() {
        if (!Airship.isFlying) {
            return
        }

        val analytics = Airship.analytics
        _identifiersFlow.update { analytics.associatedIdentifiers.ids.map { Identifier(it.key, it.value) } }
    }
}

internal data class Identifier(
    val name: String,
    val value: String
)
