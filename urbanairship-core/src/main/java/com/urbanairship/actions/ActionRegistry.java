/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.util.SparseArray;

import com.urbanairship.R;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.XmlRes;

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
         * @return {@code true} to accept the arguments, otherwise {@code false}.
         */
        boolean apply(@NonNull ActionArguments arguments);

    }

    private final Map<String, Entry> actionMap = new HashMap<>();

    /**
     * Registers an action.
     * <p>
     * If another entry is registered under specified name, it will be removed from that
     * entry and used for the new action.
     *
     * @param action The action to register
     * @param names The names the action will be registered under
     * @return The entry.
     * @throws IllegalArgumentException If no names were provided, or if th one of the names is an empty string.
     */
    @NonNull
    public Entry registerAction(@NonNull Action action, @NonNull String... names) {
        if (names.length == 0) {
            throw new IllegalArgumentException("Unable to register an action without a name.");
        }

        return registerEntry(new Entry(action, new ArrayList<>(Arrays.asList(names))));
    }

    /**
     * Registers an action by class.
     *
     * @param clazz The class to register
     * @param names The names the action will be registered under
     * @return The entry.
     * @throws IllegalArgumentException If no names were provided, or if th one of the names is an empty string.
     */
    @NonNull
    public Entry registerAction(@NonNull Class<? extends Action> clazz, @NonNull String... names) {
        if (names.length == 0) {
            throw new IllegalArgumentException("Unable to register an action without a name.");
        }

        return registerEntry(new Entry(clazz, new ArrayList<>(Arrays.asList(names))));
    }

    @NonNull
    private Entry registerEntry(@NonNull Entry entry) {
        List<String> names = entry.getNames();

        // Validate all the names
        for (String name : names) {
            if (UAStringUtil.isEmpty(name)) {
                throw new IllegalArgumentException("Unable to register action because one or more of the names was null or empty.");
            }
        }

        synchronized (actionMap) {
            for (String name : names) {
                if (UAStringUtil.isEmpty(name)) {
                    continue;
                }

                Entry existingEntry = actionMap.remove(name);
                if (existingEntry != null) {
                    existingEntry.removeName(name);
                }

                actionMap.put(name, entry);
            }

            return entry;
        }
    }

    /**
     * Gets an action entry for a given name.
     *
     * @param name The name of the action
     * @return An Entry for the name, or null if no entry exists for
     * the given name
     */
    @Nullable
    public Entry getEntry(@NonNull String name) {
        if (UAStringUtil.isEmpty(name)) {
            return null;
        }

        synchronized (actionMap) {
            return actionMap.get(name);
        }
    }

    /**
     * Gets the set of registry entries that are currently registered.
     *
     * @return Set of registered entries
     */
    @NonNull
    public Set<Entry> getEntries() {
        synchronized (actionMap) {
            return new HashSet<>(actionMap.values());
        }
    }

    /**
     * Unregister an action from the registry.
     *
     * @param name The name of the action.
     */
    public void unregisterAction(@NonNull String name) {
        if (UAStringUtil.isEmpty(name)) {
            return;
        }

        synchronized (actionMap) {
            Entry entry = getEntry(name);
            if (entry == null) {
                return;
            }

            for (String entryName : entry.getNames()) {
                actionMap.remove(entryName);
            }
        }
    }

    /**
     * Registers default actions.
     *
     * @param context The application context.
     */
    public void registerDefaultActions(@NonNull Context context) {
        registerActions(context, R.xml.ua_default_actions);
    }

    /**
     * Registers actions from a resource.
     * @param context The context.
     * @param actionsXml The actions XML resource ID.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void registerActions(@NonNull Context context, @XmlRes int actionsXml) {
        List<Entry> entries = ActionEntryParser.fromXml(context, actionsXml);

        for (Entry entry : entries) {
            registerEntry(entry);
        }
    }

    /**
     * An entry in the action registry.
     */
    public final static class Entry {

        private final List<String> names;
        private Action defaultAction;
        private Class defaultActionClass;
        private Predicate predicate;

        private final SparseArray<Action> situationOverrides = new SparseArray<>();

        /**
         * Entry constructor
         *
         * @param action The entry's action
         * @param names The names of the entry
         */
        Entry(@NonNull Action action, @NonNull List<String> names) {
            this.defaultAction = action;
            this.names = names;
        }

        /**
         * Entry constructor
         *
         * @param c The entry's action
         * @param names The names of the entry
         */
        Entry(@NonNull Class c, @NonNull List<String> names) {
            this.defaultActionClass = c;
            this.names = names;
        }

        /**
         * Returns an action for a given situation.
         *
         * @param situation Situation for the entry
         * @return The action defined for the situation override, or the
         * default action
         */
        @NonNull
        public Action getActionForSituation(@Action.Situation int situation) {
            Action action = situationOverrides.get(situation);
            if (action != null) {
                return action;
            } else {
                return getDefaultAction();
            }
        }

        /**
         * Gets the predicate for the entry.
         *
         * @return The entry's predicate, or null if it is not defined
         */
        @Nullable
        public Predicate getPredicate() {
            return predicate;
        }

        /**
         * Sets the predicate for the entry.
         *
         * @param predicate A predicate for the entry
         */
        public void setPredicate(@Nullable Predicate predicate) {
            this.predicate = predicate;
        }

        /**
         * Gets the default action
         *
         * @return The default action
         */
        @NonNull
        public Action getDefaultAction() {
            if (defaultAction != null) {
                return defaultAction;
            } else {
                try {
                    defaultAction = (Action) defaultActionClass.newInstance();
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to instantiate action class.");
                }
                return defaultAction;
            }
        }

        /**
         * Sets the default action.
         *
         * @param action The default action for the entry
         */
        public void setDefaultAction(@NonNull Action action) {
            this.defaultAction = action;
        }

        /**
         * Adds an action to be used instead of the default action for a
         * given situation.
         *
         * @param situation The situation to override
         * @param action Action for the situation
         */
        public void setSituationOverride(@Action.Situation int situation, @Nullable Action action) {
            if (action == null) {
                situationOverrides.remove(situation);
            } else {
                situationOverrides.put(situation, action);
            }
        }

        /**
         * Gets the list of registered names for the entry
         *
         * @return A list of names
         */
        @NonNull
        public List<String> getNames() {
            synchronized (names) {
                return new ArrayList<>(names);
            }
        }

        /**
         * Removes a name from the entry
         *
         * @param name Name to remove
         */
        private void removeName(@NonNull String name) {
            synchronized (names) {
                names.remove(name);
            }
        }

        /**
         * Adds a name to the entry
         *
         * @param name Name to add
         */
        private void addName(@NonNull String name) {
            synchronized (names) {
                names.add(name);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return "Action Entry: " + names;
        }

    }

}
