package com.urbanairship.messagecenter.compose.ui

import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.Predicate
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.compose.ui.MessageCenterStoriesListViewModel.Action
import com.urbanairship.messagecenter.compose.ui.MessageCenterStoriesListViewModel.State

/**
 * State holder for the Message Center stories list screen.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Stable
public class MessageCenterStoriesState internal constructor(
    private val onAction: (Action) -> Unit
) {
    internal var viewState by mutableStateOf<State>(State.Loading)

    /** Whether the list is in loading state. */
    public val isLoading: Boolean by derivedStateOf {
        viewState is State.Loading
    }

    /** Whether the list is in error state. */
    public val isError: Boolean by derivedStateOf {
        viewState is State.Error
    }

    /** The list of messages when in content state, or empty list otherwise. */
    public val messages: List<Message> by derivedStateOf {
        (viewState as? State.Content)?.messages ?: emptyList()
    }

    /** Dispatches an action to the view model. */
    internal fun onAction(action: Action) {
        onAction.invoke(action)
    }
}

/**
 * Remembers a [MessageCenterStoriesState].
 *
 * @param predicate An optional predicate to filter messages.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
public fun rememberMessageCenterStoriesState(
    predicate: Predicate<Message>? = null,
    sortWith: Comparator<Message>? = null
): MessageCenterStoriesState {
    val viewModel: DefaultMessageCenterStoriesListViewModel = viewModel(
        factory = DefaultMessageCenterStoriesListViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(DefaultMessageCenterStoriesListViewModel.PREDICATE_KEY, predicate)
            set(DefaultMessageCenterStoriesListViewModel.SORT_COMPARATOR_KEY, sortWith)
        }
    )

    return rememberMessageCenterStoriesState(viewModel)
}

@Composable
internal fun rememberMessageCenterStoriesState(
    viewModel: MessageCenterStoriesListViewModel
): MessageCenterStoriesState {
    val state = remember { MessageCenterStoriesState(viewModel::handleAction) }

    LaunchedEffect(Unit) {
        viewModel.state.collect {
            state.viewState = it
        }
    }

    return state
}
