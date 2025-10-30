package com.urbanairship.messagecenter.compose.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.urbanairship.messagecenter.compose.R
import com.urbanairship.messagecenter.compose.ui.theme.MsgCenterTheme
import com.urbanairship.messagecenter.compose.ui.theme.TopAppBarColors
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.core.R as MessageCenterR

/* Contains defaults to be used with Message Center composables. */
@Stable
public object MessageCenterDefaults {

    /**
     * Top bar for the message list screen.
     *
     * @param title The title to display in the top bar.
     * @param isEditing Whether the list is in editing mode.
     * @param navIcon The navigation icon to display. If null, no navigation icon is displayed.
     * @param navIconDescription The content description for the navigation icon.
     * @param actions The actions to display in the top bar.
     * @param onNavigateUp The callback to be invoked when the navigation icon is clicked.
     * @param scrollBehavior The optional scroll behavior for the top bar.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    public fun listTopBar(
        title: String,
        isEditing: Boolean,
        navIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowBack,
        navIconDescription: String? = stringResource(CoreR.string.ua_back),
        actions: @Composable RowScope.() -> Unit = {},
        onNavigateUp: () -> Unit = {},
        scrollBehavior: TopAppBarScrollBehavior? = null
    ) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() }
                )
            },
            navigationIcon = {
                navIcon?.let {
                    IconButton(onClick = onNavigateUp) {
                        Icon(navIcon, navIconDescription)
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            actions = actions,
            colors = MsgCenterTheme.colors.listTopBar.toMaterials()
        )
    }

    /**
     * Actions for the message list top bar.
     *
     * @param state The [state][MessageCenterState] of the message list.
     */
    @Composable
    public fun listTopBarActions(
        state: MessageCenterListState,
    ) {
        val isEditing = state.isEditing
        val isShowingContent = state.viewState is MessageCenterListViewModel.State.Content

        var showMenu by remember { mutableStateOf(false) }

        IconButton(
            onClick = { state.onAction(MessageCenterListViewModel.Action.SetEditing(!isEditing)) },
            enabled = isShowingContent
        ) {
            val editButtonDescription = stringResource(
                if (isEditing) MessageCenterR.string.ua_leave_edit_mode else MessageCenterR.string.ua_enter_edit_mode
            )

            Icon(
                painter = painterResource(
                    if (isEditing) {
                        R.drawable.ua_ic_edit_off_24
                    } else {
                        R.drawable.ua_ic_edit_24
                    }
                ),
                contentDescription = editButtonDescription
            )
        }

        IconButton(
            onClick = { showMenu = !showMenu }
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MsgCenterTheme.colors.listTopBar.containerColor
        ) {
            DropdownMenuItem(
                enabled = isShowingContent,
                onClick = {
                    showMenu = false
                    state.onAction(MessageCenterListViewModel.Action.Refresh())
                },
                text = { Text(stringResource(CoreR.string.ua_refresh)) }
            )
        }
    }

    /**
     * Top bar for the message screen.
     *
     * @param title The title to display in the top bar.
     * @param navIcon The navigation icon to display. If null, no navigation icon is displayed.
     * @param navIconDescription The content description for the navigation icon.
     * @param actions The actions to display in the top bar.
     * @param onNavigateUp The callback to be invoked when the navigation icon is clicked.
     * @param scrollBehavior The optional scroll behavior for the top bar.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    public fun messageTopBar(
        title: String?,
        navIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowBack,
        navIconDescription: String? = stringResource(CoreR.string.ua_back),
        actions: @Composable RowScope.() -> Unit = {},
        onNavigateUp: () -> Unit = {},
        scrollBehavior: TopAppBarScrollBehavior? = null
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        MediumTopAppBar(
            title = {
                title?.let {
                    Text(
                        text = it,
                        style = MsgCenterTheme.typography.itemTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            },
            navigationIcon = {
                navIcon?.let {
                    IconButton(onClick = onNavigateUp) {
                        Icon(navIcon, navIconDescription)
                    }
                }
            },
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = MsgCenterTheme.colors.messageTopBar.toMaterials()
        )
    }

    /**
     * Actions for the message top bar.
     *
     * @param state The [state][MessageCenterMessageState] of the message.
     * @param canDelete Whether the delete action is enabled.
     * @param onMessageDeleted Optional callback invoked when the message is deleted.
     */
    @Composable
    public fun messageTopBarActions(
        state: MessageCenterMessageState,
        canDelete: Boolean = true,
        onMessageDeleted: () -> Unit = {},
    ) {
        IconButton(
            onClick = {
                state.onAction(MessageCenterMessageViewModel.Action.DeleteCurrentMessage)
                onMessageDeleted.invoke()
            },
            enabled = state.messageId != null && canDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(CoreR.string.ua_delete)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBarColors.toMaterials(): androidx.compose.material3.TopAppBarColors {
    return androidx.compose.material3.TopAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        navigationIconContentColor = navigationIconContentColor,
        titleContentColor = titleContentColor,
        subtitleContentColor = titleContentColor,
        actionIconContentColor = actionIconContentColor
    )
}
