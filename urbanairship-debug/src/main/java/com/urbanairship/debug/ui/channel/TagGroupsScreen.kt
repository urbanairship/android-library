/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.channel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Provider
import com.urbanairship.UAirship
import com.urbanairship.channel.TagGroupsEditor
import com.urbanairship.debug.ui.channel.TagGroupViewModel.Action
import com.urbanairship.debug.ui.components.DebugScreen
import com.urbanairship.debug.ui.components.Section
import com.urbanairship.debug.ui.components.TopBarNavigation
import com.urbanairship.debug.ui.theme.AirshipDebugTheme

@Composable
internal fun TagGroupsScreen(
    editorProvider: Provider<TagGroupsEditor?>,
    viewModel: TagGroupViewModel = viewModel<DefaultTagGroupViewModel>(),
    onNavigateUp: () -> Unit = {}
) {
    DebugScreen(
        title = stringResource(id = ChannelInfoScreens.TagGroups.titleRes),
        navigation = TopBarNavigation.Back(onNavigateUp)
    ) {
        ScreenContent(viewModel = viewModel, editorProvider)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenContent(
    viewModel: TagGroupViewModel,
    editorProvider: Provider<TagGroupsEditor?>
) {
    Section(title = "Tag Info") {

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()) {
                Action.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = viewModel.action.value == entry,
                        onClick = { viewModel.action.value = entry },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Action.entries.size)
                    ) {
                        Text(text = entry.toString().lowercase().capitalize(Locale.current))
                    }
                }
            }

            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = viewModel.tag.value,
                onValueChange = { viewModel.tag.value = it.trim()},
                label = { Text("Tag") },
                keyboardOptions = KeyboardOptions(autoCorrect = false)
            )

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                value = viewModel.group.value,
                onValueChange = { viewModel.group.value = it.trim()},
                label = { Text("Group") },
                keyboardOptions = KeyboardOptions(autoCorrect = false)
            )

            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                enabled = viewModel.isComplete,
                onClick = {
                    viewModel.perform(editorProvider.get())
                }
            ) {
                Text(text = "Apply")
            }
        }

    }
}

internal interface TagGroupViewModel {
    val tag: MutableState<String>
    val group: MutableState<String>
    val action: MutableState<Action>

    val isComplete: Boolean
        get() = tag.value.isNotEmpty() && group.value.isNotEmpty()

    enum class Action {
        ADD, REMOVE
    }

    fun perform(editor: TagGroupsEditor?)

    companion object {
        fun preview(): TagGroupViewModel {
            return object : TagGroupViewModel {
                override val tag: MutableState<String> = mutableStateOf("sample tag")
                override val group: MutableState<String> = mutableStateOf("sample group")
                override val action: MutableState<Action> = mutableStateOf(Action.ADD)

                override fun perform(editor: TagGroupsEditor?) { }
            }
        }
    }
}

internal class DefaultTagGroupViewModel: TagGroupViewModel, ViewModel() {
    override val tag: MutableState<String> = mutableStateOf("")
    override val group: MutableState<String> = mutableStateOf("")
    override val action: MutableState<Action> = mutableStateOf(Action.ADD)

    override fun perform(editor: TagGroupsEditor?) {
        if (!UAirship.isFlying() || editor == null || tag.value.isEmpty() || group.value.isEmpty()) {
            return
        }

        when (action.value) {
            Action.ADD -> editor.addTag(group.value, tag.value)
            Action.REMOVE -> editor.removeTag(group.value, tag.value)
        }

        editor.apply()

        tag.value = ""
        group.value = ""
    }
}

@Preview("Light")
@Preview("Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AutomationScreenPreview() {
    AirshipDebugTheme {
        TagGroupsScreen(
            editorProvider = { null }
        )
    }
}
