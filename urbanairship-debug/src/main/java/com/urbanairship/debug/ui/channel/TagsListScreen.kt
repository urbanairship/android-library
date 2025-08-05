/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.UAirship
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.LoadingView
import com.urbanairship.debug.ui.components.RowItem
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

@Composable
internal fun TagsListScreen(
    viewModel: TagsScreenViewModel = viewModel<DefaultTagsScreenViewModel>(),
    onNavigateUp: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val showAddDialog = remember { mutableStateOf(false) }

    DebugScreen(
        title = stringResource(id = ChannelInfoScreens.Tags.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp),
        actionButton = {
            FloatingActionButton(
                onClick = { showAddDialog.value = true },
                shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Add tag")
            }
        }
    ) {
        ScreenContent(viewModel = viewModel, onNavigate)

        if (showAddDialog.value) {
            AddTagDialog { newTag ->
                if (newTag?.isNotEmpty() == true) {
                    viewModel.add(newTag)
                }

                showAddDialog.value = false
            }
        }
    }
}

@Composable
private fun ScreenContent(viewModel: TagsScreenViewModel, onNavigate: (String) -> Unit = {}) {

    val isLoaded = viewModel.isLoaded.collectAsState(initial = false).value
    if (!isLoaded) {
        LoadingView()
        return
    }

    val tags = viewModel.tags.collectAsState(initial = listOf()).value

    Section(title = "Current Tags") {
        if (tags.isEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(text = "No Tags")
            }
        } else {
            tags.forEach { tag ->
                RowItem(title = tag, accessory = {
                    IconButton(onClick = { viewModel.remove(tag) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                })
            }
        }
    }
}

@Composable
private fun AddTagDialog(onDismiss: (String?) -> Unit) {
    val tagName = remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = "add-tag") {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { onDismiss(null) },
        confirmButton = {
            TextButton(onClick = { onDismiss(tagName.value) }) {
                Text("Add")
            }
        },
        title = { Text(text = "Add New Tag") },
        text = {
            TextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = tagName.value ?: "",
                onValueChange = { tagName.value = it.trim() }
            )
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(null) }) {
                Text("Cancel")
            }
        }
    )
}

internal interface TagsScreenViewModel {
    val tags: Flow<List<String>>
    val isLoaded: Flow<Boolean>

    fun add(tag: String)
    fun remove(tag: String)

    companion object {
        fun preview(): TagsScreenViewModel {
            return object : TagsScreenViewModel {
                override val tags: Flow<List<String>> = MutableStateFlow(listOf("tag 1", "tag 2"))
                override val isLoaded: Flow<Boolean> = flowOf(true)

                override fun add(tag: String) { }
                override fun remove(tag: String) { }
            }
        }
    }
}

internal class DefaultTagsScreenViewModel: TagsScreenViewModel, ViewModel() {
    private val tagsState = MutableStateFlow<List<String>>(listOf())
    override val tags: Flow<List<String>> = tagsState

    private val isLoadedState = MutableStateFlow(false)
    override val isLoaded: Flow<Boolean> = isLoadedState

    init {
        UAirship.shared { airship ->
            isLoadedState.update { true }
            refresh()
        }
    }

    private fun refresh() {
        if (!UAirship.isFlying) {
            return
        }

        tagsState.update { UAirship.shared().channel.tags.sorted() }
    }

    override fun add(tag: String) {
        if (!UAirship.isFlying) {
            return
        }

        UAirship.shared().channel
            .editTags()
            .addTag(tag)
            .apply()

        refresh()
    }

    override fun remove(tag: String) {
        if (!UAirship.isFlying) {
            return
        }

        UAirship.shared().channel
            .editTags()
            .removeTag(tag)
            .apply()

        refresh()
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        TagsListScreen(
            viewModel = TagsScreenViewModel.preview()
        )
    }
}
