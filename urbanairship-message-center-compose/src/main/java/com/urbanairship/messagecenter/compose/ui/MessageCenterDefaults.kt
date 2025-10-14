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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.urbanairship.messagecenter.compose.R
import com.urbanairship.R as CoreR
import com.urbanairship.messagecenter.core.R as MessageCenterR

/* Contains defaults to be used with Message Center composables. */
@Stable
public object MessageCenterDefaults {
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
            title = { Text(text = title) },
            navigationIcon = {
                navIcon?.let {
                    IconButton(onClick = onNavigateUp) {
                        Icon(navIcon, navIconDescription)
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            actions = actions,
            // TODO: Add custom theming support
//            colors = TopAppBarDefaults.topAppBarColors(
//                containerColor = MessageCenterTheme.colors.topBarBackground,
//                titleContentColor = MessageCenterTheme.colors.topBarTitleText,
//                navigationIconContentColor = MessageCenterTheme.colors.topBarIconTint
//            ),
        )
    }

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
                painter = if (isEditing) painterResource(R.drawable.ua_ic_edit_off_24)
                else painterResource(R.drawable.ua_ic_edit_24),
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
            onDismissRequest = { showMenu = false }
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
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
            // TODO: Add custom theming support
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
//                containerColor = MessageCenterTheme.colors.topBarBackground,
//                titleContentColor = MessageCenterTheme.colors.topBarTitleText,
//                navigationIconContentColor = MessageCenterTheme.colors.topBarIconTint
            ),
        )
    }

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
