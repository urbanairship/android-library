/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class responsible for runtime-persisting actions and associating them
 * with names and predicates.
 */
public final class ActionRegistry {

    private final Map<String, Entry> actionMap = new HashMap<>();


    private static final String ACTION_ENTRY_TAG = "ActionEntry";

    private static final String CLASS_ATTRIBUTE = "class";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String ALT_NAME_ATTRIBUTE = "altName";
    private static final String PREDICATE_ATTRIBUTE = "predicate";

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
        boolean apply(ActionArguments arguments);
    }

    /**
     * Registers an action.
     * <p/>
     * If another entry is registered under specified name, it will be removed from that
     * entry and used for the new action.
     *
     * @param action The action to register
     * @param names The names the action will be registered under
     * @return The entry, or null if the action was unable to be registered
     * @throws IllegalArgumentException If the action is null, names is null, or if any of the provided
     * names is empty.
     */
    public Entry registerAction(@NonNull Action action, @NonNull String... names) {
        //noinspection ConstantConditions
        if (action == null) {
            throw new IllegalArgumentException("Unable to register a null action");
        }

        //noinspection ConstantConditions
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("Unable to register an action without a name.");
        }

        return registerEntry(new Entry(action, names));
    }

    /**
     * Lazily registers an action.
     *
     * @param c The class to register
     * @param names The names the action will be registered under
     * @return The entry, or null if the action was unable to be registered
     * @throws IllegalArgumentException If the class is null, if names is null or if any of the provided names is empty.
     */
    public Entry registerAction(@NonNull Class<? extends Action> c, @NonNull String... names) {
        //noinspection ConstantConditions
        if (c == null) {
            throw new IllegalArgumentException("Unable to an register a null action class.");
        }


        //noinspection ConstantConditions
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("Unable to register an action without a name.");
        }

        return registerEntry(new Entry(c, names));
    }

    /**
     * Lazily registers an action.
     *
     * @param c The class to register
     * @param names The names the action will be registered under
     * @return The entry, or null if the action was unable to be registered
     * @throws IllegalArgumentException If the class is null, if names is null or if any of the provided names is empty.
     */
    public Entry registerAction(@NonNull Class<? extends Action> c, Predicate predicate, @NonNull String... names) {
        //noinspection ConstantConditions
        if (c == null) {
            throw new IllegalArgumentException("Unable to register a null action class.");
        }


        //noinspection ConstantConditions
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("Unable to register an action without a name.");
        }

        return registerEntry(new Entry(c, names));
    }

    private Entry registerEntry(Entry entry) {
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
     * Registers default actions using a resource file.
     *
     * @param context The application context.
     */
    public void registerDefaultActions(Context context) {
        try {
            XmlResourceParser parser = context.getResources().getXml(R.xml.ua_default_actions);

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                int tagType = parser.getEventType();
                String tagName = parser.getName();

                if (!(tagType == XmlPullParser.START_TAG && ACTION_ENTRY_TAG.equals(tagName))) {
                    continue;
                }

                String className = parser.getAttributeValue(null, CLASS_ATTRIBUTE);
                if (UAStringUtil.isEmpty(className)) {
                    Logger.error(ACTION_ENTRY_TAG + " must specify class attribute.");
                    continue;
                }
                
                Class<? extends Action> c;
                try {
                    c = Class.forName(className).asSubclass(Action.class);
                } catch (ClassNotFoundException e) {
                    Logger.error("Action class " + className + " not found. Skipping action registration.");
                    continue;
                }

                // Handle primary and secondary names.
                String actionName = parser.getAttributeValue(null, NAME_ATTRIBUTE);
                if (actionName == null) {
                    Logger.error(ACTION_ENTRY_TAG + " must specify name attribute.");
                    continue;
                }
                String altActionName = parser.getAttributeValue(null, ALT_NAME_ATTRIBUTE);
                String[] names = UAStringUtil.isEmpty(altActionName) ? new String[] { actionName } : new String[] { actionName, altActionName };
                Entry entry = registerAction(c, names);

                // Handle optional predicate class.
                String predicateClassName = parser.getAttributeValue(null, PREDICATE_ATTRIBUTE);
                if (predicateClassName == null) {
                    continue;
                }

                Predicate predicate;
                try {
                    predicate = Class.forName(predicateClassName).asSubclass(Predicate.class).newInstance();
                    entry.setPredicate(predicate);
                } catch (Exception e) {
                    Logger.error("Predicate class " + predicateClassName + " not found. Skipping predicate.");
                }
            }
        } catch (XmlPullParserException | IOException | Resources.NotFoundException | NullPointerException e) {
            // Note: NullPointerException can occur in rare circumstances further down the call stack
            Logger.error("Failed to parse ActionEntry:" + e.getMessage());
        }
    }

    /**
     * Registers default actions.
     *
     * @deprecated Will be removed in 9.0.0.
     */
    @Deprecated
    public void registerDefaultActions() {
        registerDefaultActions(UAirship.getApplicationContext());
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
        private Entry(Action action, String[] names) {
            this.defaultAction = action;
            this.names = new ArrayList<>(Arrays.asList(names));
        }

        /**
         * Entry constructor
         *
         * @param c The entry's action
         * @param names The names of the entry
         */
        private Entry(Class c, String[] names) {
            this.defaultActionClass = c;
            this.names = new ArrayList<>(Arrays.asList(names));
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
            } else if (defaultAction != null) {
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
         * Gets the predicate for the entry.
         *
         * @return The entry's predicate, or null if it is not defined
         */
        public Predicate getPredicate() {
            return predicate;
        }

        /**
         * Sets the predicate for the entry.
         *
         * @param predicate A predicate for the entry
         */
        public void setPredicate(Predicate predicate) {
            this.predicate = predicate;
        }

        /**
         * Gets the default action
         *
         * @return The default action
         */
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
         * <p/>
         * The action must not be null.
         *
         * @param action The default action for the entry
         */
        public void setDefaultAction(Action action) {
            if (action == null) {
                return;
            }

            this.defaultAction = action;
        }

        /**
         * Adds an action to be used instead of the default action for a
         * given situation.
         *
         * @param action Action for the situation
         * @param situation The situation to override
         */
        public void addSituationOverride(@NonNull Action action, @Action.Situation int situation) {
            //noinspection ConstantConditions
            if (action == null) {
                return;
            }

            situationOverrides.put(situation, action);
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


        @Override
        public String toString() {
            return "Action Entry: " + names;
        }
    }
}
