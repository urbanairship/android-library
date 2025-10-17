/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.actions.Action.Situation
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import androidx.annotation.RestrictTo

/**
 * Class responsible for runtime-persisting actions and associating them
 * with names and predicates.
 */
public class ActionRegistry {
    private val lock: ReentrantLock = ReentrantLock()
    private val actionsMap = mutableMapOf<String, EntryHolder>()

    /**
     * Registers a new [Entry] with one or more names.
     *
     * @param names One or more names to associate with the action entry.
     * @param entry The action entry instance.
     * @throws IllegalArgumentException if no valid (non-empty) names are provided.
     */
    @Throws(IllegalArgumentException::class)
    public fun registerEntry(names: Set<String>, entry: Entry) {
        registerEntry(names) { entry }
    }

    /**
     * Registers a new [Entry] using an initialization block for lazy creation.
     *
     * @param names One or more names to associate with the action entry.
     * @param entryBlock A lambda that returns the [Entry] when first executed.
     * @throws IllegalArgumentException if no valid (non-empty) names are provided.
     */
    @Throws(IllegalArgumentException::class)
    public fun registerEntry(names: Set<String>, entryBlock: () -> Entry) {
        val namesToRegister = names.filter { it.isNotEmpty() }
        require(namesToRegister.isNotEmpty()) { "Invalid names: $names. At least one name is required" }

        val holder = EntryHolder(entryBlock)

        lock.withLock {
            actionsMap.putAll(namesToRegister.associateWith { holder })
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun registerActions(manifest: ActionsManifest) {
        lock.withLock {
            manifest.manifest.forEach { (names, entryBlock) ->
                registerEntry(names = names, entryBlock = entryBlock)
            }
        }
    }

    /**
     * Unregisters an action entry.
     *
     * All names currently mapped to the same internal entry object as the provided [name]
     * will also be removed.
     *
     * @param name The name of the action entry to remove.
     */
    public fun removeEntry(name: String) {
        lock.withLock {
            val entryHolderToRemove = actionsMap[name] ?: return

            val keysToRemove = actionsMap.filterValues { value ->
                entryHolderToRemove === value
            }.keys.toList()

            keysToRemove.forEach { key ->
                actionsMap.remove(key)
            }
        }
    }

    /**
     * Updates the default action or a situation-specific action override for a registered entry.
     *
     * @param name The name of the action entry to update.
     * @param action The new [Action].
     * @param situation The optional [Situation] to override. If `null` (default), the
     * default action is updated.
     * @return `true` if the entry was found and updated, `false` otherwise.
     */
    @JvmOverloads
    public fun updateEntry(name: String, action: Action, situation: Situation? = null): Boolean {
        return lock.withLock {
            val entry = actionsMap[name]?.entry ?: return false
            entry.updateAction(action, situation)
            true
        }
    }

    /**
     * Updates the predicate for a registered action entry.
     *
     * @param name The name of the action entry to update.
     * @param predicate The new [ActionPredicate]. Can be `null` to remove the predicate.
     * @return `true` if the entry was found and updated, `false` otherwise.
     */
    public fun updateEntry(name: String, predicate: ActionPredicate?): Boolean {
        return lock.withLock {
            val entry = actionsMap[name]?.entry ?: return false
            entry.updatePredicate(predicate)
            true
        }
    }

    /**
     * Gets an action entry for a given name.
     *
     * @param name The name of the action.
     * @return An [Entry] for the name, or `null` if no entry exists for the given name or if the name is empty.
     */
    public fun getEntry(name: String): Entry? {
        if (name.isEmpty()) return null
        return lock.withLock {
            actionsMap[name]?.entry
        }
    }

    /**
     * Gets the set of all unique action entries that are currently registered.
     *
     * Note: Accessing this property may trigger lazy initialization for any entries
     * that have not yet been accessed.
     */
    public val entries: Set<Entry>
        get() {
            return lock.withLock {
                actionsMap.values
                    .asSequence()
                    .map { it.entry }
                    .toSet()
            }
        }

    /**
     * A private holder class used to achieve lazy, thread-safe initialization of an [Entry].
     */
    private class EntryHolder(
        entryBlock: () -> Entry
    ) {
        val entry: Entry by lazy(entryBlock)
    }

    /**
     * An entry in the action registry, holding the action, its overrides, and its predicate.
     */
    public class Entry @JvmOverloads public constructor(
        action: Action,
        situationOverrides: Map<Situation, Action> = emptyMap(),
        predicate: ActionPredicate? = null
    ) {
        private var _action: Action = action
        private val _situationOverrides: MutableMap<Situation, Action> = situationOverrides.toMutableMap()
        private var _predicate: ActionPredicate? = predicate

        internal fun updateAction(action: Action, situation: Situation? = null) {
            if (situation != null) {
                _situationOverrides[situation] = action
            } else {
                _action = action
            }
        }

        internal fun updatePredicate(predicate: ActionPredicate?) {
            _predicate = predicate
        }

        /**
         * The default [Action] instance.
         */
        public val action: Action
            get() = this._action

        /**
         * Read-only map of action overrides for specific [Situation]s.
         */
        public val situationOverrides: Map<Situation, Action>
            get() = this._situationOverrides.toMap()


        /**
         * The optional [ActionPredicate] controlling when the action may be executed.
         */
        public val predicate: ActionPredicate?
            get() = this._predicate

        /**
         * Returns the action applicable for a given [Situation].
         *
         * @param situation The situation for which to retrieve the action.
         * @return The action defined for the situation override, or the default action if no override exists.
         */
        public fun getActionForSituation(situation: Situation?): Action {
            return _situationOverrides[situation] ?: _action
        }
    }
}
