/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.contact

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UAirship
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListEditor
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal interface ScopedSubscriptionListProvider {
    fun getEditor(): ScopedSubscriptionListEditor?
    suspend fun fetch(): Result<Map<String, Set<Scope>>>
}

@Composable
internal fun ScopedSubscriptionListsScreen(
    provider: ScopedSubscriptionListProvider,
    viewModel: ScopedSubscriptionListsViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
) {

    viewModel.provider = provider

    DebugScreen(
        title = stringResource(id = ContactScreens.SubscriptionList.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(viewModel: ScopedSubscriptionListsViewModel) {
    val subscriptions = viewModel.currentSubscriptions.collectAsState(initial = emptyMap()).value

    LazyColumn {
        item {

            Section(title = "Subscription Info") {
                Column(Modifier.padding(horizontal = 16.dp)) {

                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        value = viewModel.listId.value,
                        onValueChange = { viewModel.listId.value = it.trim()},
                        label = { Text("List ID") },
                        keyboardOptions = KeyboardOptions(autoCorrect = false)
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()) {
                        Scope.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = viewModel.subscriptionScope.value == entry,
                                onClick = { viewModel.subscriptionScope.value = entry },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = Scope.entries.size)
                            ) {
                                Text(text = entry.displayName())
                            }
                        }
                    }

                    HorizontalDivider()

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()) {
                        ScopedSubscriptionListsViewModel.Action.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = viewModel.action.value == entry,
                                onClick = { viewModel.action.value = entry },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ScopedSubscriptionListsViewModel.Action.entries.size)
                            ) {
                                Text(text = entry.toString().lowercase().capitalize(Locale.current))
                            }
                        }
                    }

                    HorizontalDivider()

                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        enabled = viewModel.isComplete,
                        onClick = { viewModel.perform() }
                    ) {
                        Text(text = "Apply")
                    }
                }
            }

            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }

        item {
            Section(title = "Subscriptions") {
                if (subscriptions.isEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No subscriptions")
                    }
                } else {
                    subscriptions.entries
                        .sortedBy { it.key }
                        .forEach { entry ->
                            RowItem(
                                title = entry.key,
                                details = entry.value
                                    .map { it.displayName() }
                                    .sorted()
                                    .joinToString(","))
                        }
                }
            }
        }
    }
}

internal class ScopedSubscriptionListsViewModel: ViewModel() {
    private val scope = CoroutineScope(AirshipDispatchers.IO)

    private val subscriptionsState = MutableStateFlow<Map<String, Set<Scope>>>(emptyMap())
    val currentSubscriptions: Flow<Map<String, Set<Scope>>> = subscriptionsState

    val listId = mutableStateOf("")
    val action = mutableStateOf(Action.SUBSCRIBE)
    val subscriptionScope = mutableStateOf(Scope.APP)

    var provider: ScopedSubscriptionListProvider? = null
        set(value) {
            field = value
            refresh()
        }

    val isComplete: Boolean
        get() { return listId.value.isNotEmpty() }

    init {
        refresh()
    }

    fun perform() {
        if (!UAirship.isFlying() || !isComplete) {
            return
        }

        val editor = provider?.getEditor() ?: return

        when(action.value) {
            Action.SUBSCRIBE -> editor.subscribe(listId.value, subscriptionScope.value)
            Action.UNSUBSCRIBE -> editor.unsubscribe(listId.value, subscriptionScope.value)
        }
        editor.apply()
        listId.value = ""
        refresh()
    }

    private fun refresh() {
        if (!UAirship.isFlying()) {
            return
        }

        val provider = provider ?: return

        scope.launch {
            val updates = provider.fetch().getOrNull()
            subscriptionsState.emit(updates ?: emptyMap())
        }
    }

    enum class Action {
        SUBSCRIBE, UNSUBSCRIBE
    }
}

internal fun Scope.displayName(): String {
    return when(this) {
        Scope.APP -> "App"
        Scope.WEB -> "Web"
        Scope.EMAIL -> "Email"
        Scope.SMS -> "SMS"
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        ScopedSubscriptionListsScreen(
            object : ScopedSubscriptionListProvider {
                override fun getEditor(): ScopedSubscriptionListEditor? { return null }
                override suspend fun fetch(): Result<Map<String, Set<Scope>>> {
                    return Result.success(
                        mapOf(
                            "test" to setOf(Scope.APP, Scope.WEB),
                            "test1" to setOf(Scope.EMAIL, Scope.SMS)
                        )
                    )
                }
            }
        )
    }
}
