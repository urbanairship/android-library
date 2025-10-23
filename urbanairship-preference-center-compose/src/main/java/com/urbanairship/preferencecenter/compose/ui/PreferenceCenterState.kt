package com.urbanairship.preferencecenter.compose.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanairship.preferencecenter.core.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * State holder for the Preference Center screen.
 */
@Stable
public class PreferenceCenterState internal constructor(
    private val context: Context,
    public val identifier: String,
    internal val onAction: (Action) -> Unit,
) {
    internal var viewState: ViewState by mutableStateOf(ViewState.Loading)

    internal var dialogs: ContactManagerDialog? by mutableStateOf(null)

    internal var errorsFlow: MutableStateFlow<String?> = MutableStateFlow(null)
    internal val errors: Flow<String?> = errorsFlow.asStateFlow()

    /** The title of the preference center. */
    public val title: String by derivedStateOf() {
        viewState.let {
            (it as? ViewState.Content)?.title ?: context.getString(R.string.ua_preference_center_label)
        }
    }

    internal fun onAction(action: Action) {
        onAction.invoke(action)
    }
}

/**
 * Remembers a [PreferenceCenterState].
 *
 * @param identifier The identifier of the preference center configuration to display.
 */
@Composable
public fun rememberPreferenceCenterState(identifier: String): PreferenceCenterState {

    val viewModel: DefaultPreferenceCenterViewModel = viewModel(
        factory = DefaultPreferenceCenterViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(DefaultPreferenceCenterViewModel.IDENTIFIER_KEY, identifier)
        }
    )

    return rememberPreferenceCenterState(viewModel)
}

@Composable
internal fun rememberPreferenceCenterState(
    viewModel: PreferenceCenterViewModel,
): PreferenceCenterState {
    val context = LocalContext.current

    val identifier = viewModel.identifier

    val state = remember { PreferenceCenterState(context, identifier, viewModel::handle) }

    LaunchedEffect(identifier) {
        withContext(Dispatchers.Default) {
            viewModel.states.collect { state.viewState = it }
        }
    }

    LaunchedEffect(identifier) {
        withContext(Dispatchers.Default) {
            viewModel.displayDialog.collect { state.dialogs = it }
        }
    }

    LaunchedEffect(identifier) {
        withContext(Dispatchers.Default) {
            viewModel.errors.collect { state.errorsFlow.emit(it) }
        }
    }

    return state
}
