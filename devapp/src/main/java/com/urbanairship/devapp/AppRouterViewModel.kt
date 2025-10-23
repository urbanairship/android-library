package com.urbanairship.devapp

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.urbanairship.devapp.debug.DebugScreen
import com.urbanairship.devapp.home.HomeScreen
import com.urbanairship.devapp.home.QuickAccess
import com.urbanairship.devapp.messagecenter.MessageCenterScreen
import com.urbanairship.devapp.preferencecenter.PreferenceCenterScreen
import com.urbanairship.devapp.thomas.ThomasLayoutNavigation
import com.urbanairship.messagecenter.compose.ui.theme.MessageCenterTheme
import com.urbanairship.messagecenter.compose.ui.MessageCenterScreen
import com.urbanairship.messagecenter.compose.ui.rememberMessageCenterState
import java.io.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface Destination: NavKey {
    fun serialize(): String
    fun navigationEntry(
        onNavigate: (Destination) -> Unit,
        onPopBackStack: () -> Unit,
    ): NavEntry<Destination>
}

class AppRouterViewModel(
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    private val deserializers: List<(String) -> Destination?> = listOf(
        TopLevelDestination::restore,
        QuickAccess::restore,
        ThomasLayoutNavigation::restore
    )

    private val backStacks: MutableStateFlow<Map<TopLevelDestination, SnapshotStateList<Destination>>>

    private val _selectedItem: MutableStateFlow<TopLevelDestination>
    val selectedTopLevel: StateFlow<TopLevelDestination>
        get() = _selectedItem.asStateFlow()

    private var _activeBackStack: MutableStateFlow<SnapshotStateList<Destination>>
    val activeBackStack: StateFlow<SnapshotStateList<Destination>>
        get() = _activeBackStack.asStateFlow()

    init {
        val selected = savedStateHandle
            .get<String>(ACTIVE_TOP_LEVEL_ITEM)
            ?.let(TopLevelDestination::restore)
            ?: TopLevelDestination.Home
        _selectedItem = MutableStateFlow(selected)

        val saved = savedStateHandle.get<Map<String, List<String>>>(NAV_STACK_KEY) ?: emptyMap()

        val restored = TopLevelDestination.entries.associateWith { topLevel ->
            var stack = saved[topLevel.serialize()]
                ?.mapNotNull { serialized ->
                    deserializers.firstNotNullOfOrNull { it(serialized) }
                }

            if (stack.isNullOrEmpty()) {
                stack = listOf(topLevel)
            }

            stack.toMutableStateList()
        }
            .toMutableMap()

        _activeBackStack = MutableStateFlow(restored.getOrPut(selected) { listOf(selected).toMutableStateList() })
        backStacks = MutableStateFlow(restored)
    }

    fun pop() {
        if (_activeBackStack.value.size < 2) {
            return
        }

        _activeBackStack.update { it.dropLast(1).toMutableStateList() }
        saveState()
    }

    fun navigate(destination: Destination) {
        if (destination is TopLevelDestination) {
            val clearStack = destination == selectedTopLevel.value
            changeTopLevel(destination)
            if (clearStack && _activeBackStack.value.size > 1) {
                _activeBackStack.update {
                    it.removeRange(1, it.size)
                    it
                }
            }
        } else {
            _activeBackStack.update { (it + destination).toMutableStateList() }
            backStacks.update {
                it.toMutableMap()
                    .apply { put(selectedTopLevel.value, _activeBackStack.value) }
                    .toMap()
            }
        }

        saveState()
    }

    fun navigateStack(stack: List<Destination>) {
        if (stack.isEmpty()) {
            return
        }

        val topLevel = (stack.first() as? TopLevelDestination) ?: return

        changeTopLevel(topLevel)
        _activeBackStack.update { stack.toMutableStateList() }
        backStacks.update {
            it.toMutableMap().apply { put(selectedTopLevel.value, _activeBackStack.value) }.toMap()
        }
    }


    fun navigationEntry(destination: Destination): NavEntry<Destination> {
        return destination.navigationEntry(
            onNavigate = { navigate(it) },
            onPopBackStack = ::pop
        )
    }

    private fun changeTopLevel(destination: TopLevelDestination) {
        _selectedItem.update { destination }
        _activeBackStack.update { getBackStackFor(destination) }
    }

    private fun getBackStackFor(topLevel: TopLevelDestination): SnapshotStateList<Destination> {
        val current = backStacks.value[topLevel]
        if (current != null) {
            return current
        }

        val result: SnapshotStateList<Destination> = listOf(topLevel).toMutableStateList()
        backStacks.update {
            it + (topLevel to result)
        }
        return result
    }

    private fun saveState() {
        savedStateHandle[ACTIVE_TOP_LEVEL_ITEM] = selectedTopLevel.value.serialize()
        savedStateHandle[NAV_STACK_KEY] = backStacks.value.mapValues { (_, stack) ->
            stack.map { it.serialize() }
        }
    }

    sealed class TopLevelDestination(private val value: String): Destination, Serializable {
        data object Home: TopLevelDestination("home")
        data class Message(val messageId: String? = null): TopLevelDestination(NAME) {
            override fun serialize(): String {
                val message = messageId ?: return NAME
                return "$NAME/$message"
            }

            companion object {
                const val NAME = "message"
            }
        }
        data object PreferenceCenter: TopLevelDestination("preference")
        data object Settings: TopLevelDestination("settings")

        override fun serialize(): String  = this.value

        override fun navigationEntry(
            onNavigate: (Destination) -> Unit,
            onPopBackStack: () -> Unit,
        ): NavEntry<Destination> {
            return when (this) {
                is Home -> NavEntry(this) { HomeScreen(onNavigate) }
                is PreferenceCenter -> NavEntry(this) { PreferenceCenterScreen("app_default") }
                is Settings -> NavEntry(this) { DebugScreen() }
                is Message -> NavEntry(this) {
                    MessageCenterScreen(
                        state = rememberMessageCenterState(messageId = messageId)
                    )
                }
            }
        }

        companion object {

            val entries = listOf(Home, Message(), PreferenceCenter, Settings)

            fun restore(saved: String): TopLevelDestination? {
                val parts = saved.split("/").toMutableList()
                if (parts.isEmpty()) {
                    return null
                }

                val top = parts.removeAt(0)
                if (parts.isEmpty()) {
                    return entries.firstOrNull { it.serialize() == top }
                }

                when(top) {
                    Message.NAME -> {
                        val messageId = parts.removeAt(0)
                        return Message(messageId)
                    }
                    else -> { return null }
                }
            }
        }
    }

    companion object {
        private const val NAV_STACK_KEY = "nav_stack"
        private const val ACTIVE_TOP_LEVEL_ITEM = "selected_stack"

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppRouterViewModel(createSavedStateHandle())
            }
        }
    }
}
