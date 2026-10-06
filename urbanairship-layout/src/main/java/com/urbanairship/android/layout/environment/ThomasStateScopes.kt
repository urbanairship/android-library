/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The [ThomasState] of every controller scope in one layout, for reporting to
 * [ThomasListenerInterface.onStateChanged].
 *
 * The model factory records every build that uses each scope. A scope reports while anything
 * still uses it: the static tree, which never lets go, or an [Owner] for a build of async view
 * content that hasn't been released. One scope can have several users. A rebuild that gets the
 * same controller states back from a `LayoutStateStorage` uses the scope the build it replaces
 * used, and two async views can share controller identifiers, so releasing one user never
 * drops a scope another still uses.
 */
internal class ThomasStateScopes {

    /**
     * One build of async view content: a user of the scopes that build's nodes are in.
     *
     * Releasing an owner also releases the owners created under it with [child], so async
     * content nested in the released content lets go of its scopes too.
     */
    inner class Owner internal constructor(parent: Owner?) {

        private val children = mutableListOf<Owner>()

        /** Whether this owner has been released; adding scopes to it from then on does nothing. */
        var isReleased: Boolean = parent?.isReleased == true
            private set

        init {
            if (!isReleased) parent?.children?.add(this)
        }

        /**
         * Returns an owner for content built inside this owner's content.
         *
         * Nested async content can finish loading after the content around it was replaced, so a
         * child of a released owner starts out released.
         *
         * @return A new owner that's released along with this one.
         */
        fun child(): Owner = Owner(this)

        /**
         * Stops this owner and every owner under it using their scopes.
         *
         * A scope stops reporting once nothing uses it, and keeps reporting while the static
         * tree or an owner that hasn't been released still does.
         */
        fun release() {
            if (isReleased) return
            isReleased = true
            children.forEach(Owner::release)
            children.clear()
            _scopes.update { all ->
                all.mapValues { (_, users) -> users - this }.filterValues { it.isNotEmpty() }
            }
        }
    }

    /**
     * Each reporting scope and what uses it: owners of async content builds, and `null` for the
     * static tree. A scope with no users left is removed.
     */
    private val _scopes = MutableStateFlow<Map<StateFlow<ThomasState>, Set<Owner?>>>(emptyMap())

    /** Every scope that's reporting, in the order they were added. */
    val scopes: List<StateFlow<ThomasState>>
        get() = _scopes.value.keys.toList()

    /**
     * Returns a new top-level owner, for one build of async view content.
     *
     * @return An owner that isn't released with any other.
     */
    fun owner(): Owner = Owner(parent = null)

    /**
     * Records that [owner]'s build uses [scope], so [changes] reports it until nothing uses it.
     *
     * Adding a scope that's already reporting adds [owner] as another user, as when a rebuild
     * reuses the scope of the build it replaces. A released [owner] adds nothing.
     *
     * @param scope The scope's state, shared by every node built in it.
     * @param owner The async content build that uses [scope], or `null` for the static tree, which never lets go.
     */
    fun add(scope: StateFlow<ThomasState>, owner: Owner? = null) {
        if (owner?.isReleased == true) return
        _scopes.update { all ->
            val users = all[scope].orEmpty()
            if (owner in users) all else all + (scope to (users + owner))
        }
    }

    /**
     * Whether [scope] is reporting: something still uses it.
     *
     * @param scope The scope to look up.
     * @return `true` if [scope] is reporting.
     */
    operator fun contains(scope: StateFlow<ThomasState>): Boolean = scope in _scopes.value

    /**
     * Emits each scope's serialized state whenever it differs from that scope's previous one.
     *
     * A scope that starts out empty stays silent until it changes, as iOS scopes do: a layout
     * with no state controller, form, pager, video, async view or AI status reports nothing.
     *
     * @return A flow of serialized states from every reporting scope, interleaved in the order they change.
     */
    fun changes(): Flow<JsonValue> = channelFlow {
        val reporting = mutableMapOf<StateFlow<ThomasState>, Job>()
        _scopes.collect { all ->
            (reporting.keys - all.keys).forEach { released -> reporting.remove(released)?.cancel() }
            all.keys.filterNot(reporting::containsKey).forEach { scope ->
                reporting[scope] = launch {
                    scope.map { it.toJsonValue() }
                        .distinctUntilChanged()
                        .dropWhile { it == EMPTY_STATE }
                        .collect { send(it) }
                }
            }
        }
    }

    /**
     * Reports [changes] to [listener] from [coroutineScope].
     *
     * @param listener The listener to report to, if its [ThomasListenerInterface.wantsStateChanges] is `true`.
     * @param coroutineScope The scope that the reporting runs in.
     * @return The reporting job, or `null`, without collecting any state, if [listener] doesn't want state changes.
     */
    fun reportTo(listener: ThomasListenerInterface, coroutineScope: CoroutineScope): Job? {
        if (!listener.wantsStateChanges) return null
        return coroutineScope.launch {
            changes().collect { listener.onStateChanged(it) }
        }
    }

    private companion object {
        val EMPTY_STATE: JsonValue = JsonMap.EMPTY_MAP.toJsonValue()
    }
}
