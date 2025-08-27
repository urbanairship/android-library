/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

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
import com.urbanairship.Airship
import com.urbanairship.channel.SubscriptionListEditor
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal interface SubscriptionListProvider {
    fun getEditor(): SubscriptionListEditor?
    suspend fun fetch(): Result<Set<String>>
}

@Composable
internal fun SubscriptionListsScreen(
    provider: SubscriptionListProvider,
    viewModel: SubscriptionListsViewModel = viewModel(),
    onNavigateUp: () -> Unit = {},
) {

    viewModel.provider = provider

    DebugScreen(
        title = stringResource(id = ChannelInfoScreens.SubscriptionLists.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(viewModel: SubscriptionListsViewModel) {
    val subscriptions = viewModel.currentSubscriptions.collectAsState(initial = emptyList()).value

    LazyColumn {
        item {
            Section(title = "Subscription Info") {
                Column(Modifier.padding(horizontal = 16.dp)) {

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()) {
                        SubscriptionListsViewModel.Action.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = viewModel.action.value == entry,
                                onClick = { viewModel.action.value = entry },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = SubscriptionListsViewModel.Action.entries.size)
                            ) {
                                Text(text = entry.toString().lowercase().capitalize(Locale.current))
                            }
                        }
                    }

                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        value = viewModel.listId.value,
                        onValueChange = { viewModel.listId.value = it.trim()},
                        label = { Text("List ID") },
                        keyboardOptions = KeyboardOptions(autoCorrect = false)
                    )

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
                    subscriptions.forEach { name ->
                        RowItem(title = name)
                    }
                }
            }
        }
    }
}

internal class SubscriptionListsViewModel: ViewModel() {
    private val scope = CoroutineScope(AirshipDispatchers.IO)

    private val subscriptionsState = MutableStateFlow<List<String>>(emptyList())
    val currentSubscriptions: Flow<List<String>> = subscriptionsState

    val listId = mutableStateOf("")
    val action = mutableStateOf(Action.SUBSCRIBE)

    var provider: SubscriptionListProvider? = null
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
        if (!Airship.isFlying || !isComplete) {
            return
        }

        val editor = provider?.getEditor() ?: return

        when(action.value) {
            Action.SUBSCRIBE -> editor.subscribe(listId.value)
            Action.UNSUBSCRIBE -> editor.unsubscribe(listId.value)
        }
        editor.apply()
        listId.value = ""
        refresh()
    }

    private fun refresh() {
        if (!Airship.isFlying) {
            return
        }

        val provider = provider ?: return

        scope.launch {
            val updates = provider.fetch().getOrNull()?.sorted()
            subscriptionsState.emit(updates ?: emptyList())
        }
    }

    enum class Action {
        SUBSCRIBE, UNSUBSCRIBE
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        SubscriptionListsScreen(
            object : SubscriptionListProvider {
                override fun getEditor(): SubscriptionListEditor? { return null }
                override suspend fun fetch(): Result<Set<String>> { return Result.success(emptySet()) }
            }
        )
    }
}
