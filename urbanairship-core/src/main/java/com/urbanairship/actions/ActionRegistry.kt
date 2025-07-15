/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.XmlRes
import com.urbanairship.R
import com.urbanairship.actions.Action.Situation
import com.urbanairship.util.UAStringUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Class responsible for runtime-persisting actions and associating them
 * with names and predicates.
 */
public class ActionRegistry {

    /**
     * ActionArgument predicate
     */
    public interface Predicate {

        /**
         * Applies the predicate to the action arguments.
         *
         * @param arguments The action arguments.
         * @return `true` to accept the arguments, otherwise `false`.
         */
        public fun apply(arguments: ActionArguments): Boolean
    }

    private val actionsMap = MutableStateFlow(mapOf<String, Entry>())

    /**
     * Registers an action.
     *
     *
     * If another entry is registered under specified name, it will be removed from that
     * entry and used for the new action.
     *
     * @param action The action to register
     * @param names The names the action will be registered under
     * @return The entry.
     * @throws IllegalArgumentException If no names were provided, or if th one of the names is an empty string.
     */
    @Throws(IllegalArgumentException::class)
    public fun registerAction(action: Action, vararg names: String): Entry {
        require(names.isNotEmpty()) { "Unable to register an action without a name." }

        return registerEntry(Entry(
            defaultAction =  action,
            names = listOf(*names)
        ))
    }

    /**
     * Registers an action by class.
     *
     * @param clazz The class to register
     * @param names The names the action will be registered under
     * @return The entry.
     * @throws IllegalArgumentException If no names were provided, or if th one of the names is an empty string.
     */
    @Throws(IllegalArgumentException::class)
    public fun registerAction(clazz: Class<out Action?>, vararg names: String): Entry {
        require(names.isNotEmpty()) { "Unable to register an action without a name." }

        return registerEntry(Entry(
            defaultActionClass = clazz,
            names = listOf(*names)))
    }

    private fun registerEntry(entry: Entry): Entry {
        val names = entry.getNames()

        // Validate all the names
        for (name in names) {
            require(!UAStringUtil.isEmpty(name)) { "Unable to register action because one or more of the names was null or empty." }
        }

        actionsMap.update { actions ->
            val result = actions.toMutableMap()

            for (name in names) {
                if (UAStringUtil.isEmpty(name)) {
                    continue
                }

                result.remove(name)?.removeName(name)

                result[name] = entry
            }

            result.toMap()
        }

        return entry
    }

    /**
     * Gets an action entry for a given name.
     *
     * @param name The name of the action
     * @return An Entry for the name, or null if no entry exists for
     * the given name
     */
    public fun getEntry(name: String): Entry? {
        if (UAStringUtil.isEmpty(name)) {
            return null
        }

        return actionsMap.value[name]
    }

    /**
     * Gets the set of registry entries that are currently registered.
     */
    public val entries: Set<Entry>
        get() {
            return actionsMap.value.values.toSet()
        }

    /**
     * Unregister an action from the registry.
     *
     * @param name The name of the action.
     */
    public fun unregisterAction(name: String) {
        if (UAStringUtil.isEmpty(name)) {
            return
        }

        val entry = getEntry(name) ?: return
        actionsMap.update { current ->
            val result = current.toMutableMap()

            for (registeredName in entry.getNames()) {
                result.remove(registeredName)
            }

            result.toMap()
        }
    }

    /**
     * Registers default actions.
     *
     * @param context The application context.
     */
    public fun registerDefaultActions(context: Context) {
        registerActions(context, R.xml.ua_default_actions)
    }

    /**
     * Registers actions from a resource.
     * @param context The context.
     * @param actionsXml The actions XML resource ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun registerActions(context: Context, @XmlRes actionsXml: Int) {
        val entries = ActionEntryParser.fromXml(context, actionsXml)

        for (entry in entries) {
            registerEntry(entry)
        }
    }

    /**
     * An entry in the action registry.
     */
    public class Entry @JvmOverloads internal constructor(
        private var defaultAction: Action? = null,
        names: List<String>,
        private var defaultActionClass: Class<*>? = null
    ) {

        private val names = MutableStateFlow(names)

        @JvmField
        public var predicate: Predicate? = null

        private val situationOverrides = mutableMapOf<Situation, Action>()

        /**
         * Returns an action for a given situation.
         *
         * @param situation Situation for the entry
         * @return The action defined for the situation override, or the
         * default action
         */
        public fun getActionForSituation(situation: Situation?): Action {
            return situationOverrides[situation] ?: getDefaultAction()
        }

        /**
         * Gets the default action
         *
         * @return The default action
         */
        @Throws(IllegalArgumentException::class)
        public fun getDefaultAction(): Action {
            return defaultAction ?: run {
                val actionClass = defaultActionClass ?: throw IllegalArgumentException("Unable to instantiate action class.")
                try {
                    val result = actionClass.newInstance() as Action
                    defaultAction = result
                    result
                } catch (e: Exception) {
                    throw IllegalArgumentException("Unable to instantiate action class.")
                }
            }
        }

        /**
         * Sets the default action.
         *
         * @param action The default action for the entry
         */
        public fun setDefaultAction(action: Action) {
            this.defaultAction = action
        }

        /**
         * Adds an action to be used instead of the default action for a
         * given situation.
         *
         * @param situation The situation to override
         * @param action Action for the situation
         */
        public fun setSituationOverride(situation: Situation, action: Action?) {
            if (action == null) {
                situationOverrides.remove(situation)
            } else {
                situationOverrides[situation] = action
            }
        }

        /**
         * Gets the list of registered names for the entry
         *
         * @return A list of names
         */
        public fun getNames(): List<String> {
            return names.value
        }

        /**
         * Removes a name from the entry
         *
         * @param name Name to remove
         */
        public fun removeName(name: String) {
            names.update { it - name }
        }

        /**
         * Adds a name to the entry
         *
         * @param name Name to add
         */
        private fun addName(name: String) {
            names.update { it + name }
        }

        override fun toString(): String {
            return "Action Entry: ${names.value}"
        }
    }
}
