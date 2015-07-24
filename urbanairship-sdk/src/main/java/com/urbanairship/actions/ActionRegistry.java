/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

import android.support.annotation.NonNull;

import com.android.internal.util.Predicate;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.tags.AddTagsAction;
import com.urbanairship.actions.tags.RemoveTagsAction;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class responsible for runtime-persisting actions and associating them
 * with names and predicates.
 */
public final class ActionRegistry {

    private final static long LANDING_PAGE_CACHE_OPEN_TIME_LIMIT_MS = 7 * 86400000; // 1 week

    private final Map<String, Entry> actionMap = new HashMap<>();

    /**
     * Registers an action.
     * <p/>
     * If another entry is registered under specified name, it will be removed from that
     * entry and used for the new action.
     *
     * @param action The action to register
     * @param names The names the action will be registered under
     * @return The entry, or null if the action was unable to be registered
     */
    public Entry registerAction(@NonNull Action action, @NonNull String... names) {
        //noinspection ConstantConditions
        if (action == null) {
            Logger.error("Unable to register null action");
            return null;
        }

        //noinspection ConstantConditions
        if (names == null || names.length == 0) {
            Logger.error("A name is required to register an action");
            return null;
        }

        // Validate all the names
        for (String name : names) {
            if (UAStringUtil.isEmpty(name)) {
                Logger.error("Unable to register action because one or more of" +
                        " the names was null or empty.");
                return null;
            }
        }

        synchronized (actionMap) {

            Entry entry = new Entry(action, names);

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
     * Registers default actions
     */
    public void registerDefaultActions() {
        registerAction(new ShareAction(),
                ShareAction.DEFAULT_REGISTRY_NAME,
                ShareAction.DEFAULT_REGISTRY_SHORT_NAME);

        registerAction(new OpenExternalUrlAction(),
                OpenExternalUrlAction.DEFAULT_REGISTRY_NAME,
                OpenExternalUrlAction.DEFAULT_REGISTRY_SHORT_NAME);

        registerAction(new DeepLinkAction(),
                DeepLinkAction.DEFAULT_REGISTRY_NAME,
                DeepLinkAction.DEFAULT_REGISTRY_SHORT_NAME);

        Entry landingPageEntry = registerAction(new LandingPageAction(),
                LandingPageAction.DEFAULT_REGISTRY_NAME,
                LandingPageAction.DEFAULT_REGISTRY_SHORT_NAME);

        landingPageEntry.setPredicate(new Predicate<ActionArguments>() {
            @Override
            public boolean apply(ActionArguments arguments) {
                if (Situation.PUSH_RECEIVED.equals(arguments.getSituation())) {
                    long lastOpenTime = UAirship.shared().getApplicationMetrics().getLastOpenTimeMillis();
                    return System.currentTimeMillis() - lastOpenTime <= LANDING_PAGE_CACHE_OPEN_TIME_LIMIT_MS;
                }
                return true;
            }
        });

        Predicate<ActionArguments> rejectPushReceivedPredicate = new Predicate<ActionArguments>() {
            @Override
            public boolean apply(ActionArguments arguments) {
                return !(Situation.PUSH_RECEIVED.equals(arguments.getSituation()));
            }
        };

        Entry addTagsEntry = registerAction(new AddTagsAction(),
                AddTagsAction.DEFAULT_REGISTRY_NAME,
                AddTagsAction.DEFAULT_REGISTRY_SHORT_NAME);

        addTagsEntry.setPredicate(rejectPushReceivedPredicate);

        Entry removeTagsEntry = registerAction(new RemoveTagsAction(),
                RemoveTagsAction.DEFAULT_REGISTRY_NAME,
                RemoveTagsAction.DEFAULT_REGISTRY_SHORT_NAME);

        removeTagsEntry.setPredicate(rejectPushReceivedPredicate);

        Entry addCustomEventEntry = registerAction(new AddCustomEventAction(),
                AddCustomEventAction.DEFAULT_REGISTRY_NAME);

        addCustomEventEntry.setPredicate(new Predicate<ActionArguments>() {
            @Override
            public boolean apply(ActionArguments arguments) {
                return (Situation.MANUAL_INVOCATION == arguments.getSituation() ||
                        Situation.WEB_VIEW_INVOCATION == arguments.getSituation());
            }
        });

        registerAction(new OpenRichPushInboxAction(),
                OpenRichPushInboxAction.DEFAULT_REGISTRY_NAME,
                OpenRichPushInboxAction.DEFAULT_REGISTRY_SHORT_NAME);

        registerAction(new OverlayRichPushMessageAction(),
                OverlayRichPushMessageAction.DEFAULT_REGISTRY_NAME,
                OverlayRichPushMessageAction.DEFAULT_REGISTRY_SHORT_NAME);

        registerAction(new ClipboardAction(),
                ClipboardAction.DEFAULT_REGISTRY_NAME,
                ClipboardAction.DEFAULT_REGISTRY_SHORT_NAME);
    }

    /**
     * An entry in the action registry.
     */
    public final static class Entry {
        private final List<String> names;
        private Action defaultAction;
        private Predicate<ActionArguments> predicate;

        private final Map<Situation, Action> situationOverrides = new ConcurrentHashMap<>();

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
         * Returns an action for a given situation.
         *
         * @param situation Situation for the entry
         * @return The action defined for the situation override, or the
         * default action
         */
        @NonNull
        public Action getActionForSituation(Situation situation) {
            if (situation == null) {
                return defaultAction;
            }

            Action action = situationOverrides.get(situation);
            if (action != null) {
                return action;
            }
            return defaultAction;
        }

        /**
         * Gets the predicate for the entry.
         *
         * @return The entry's predicate, or null if it is not defined
         */
        public Predicate<ActionArguments> getPredicate() {
            return predicate;
        }

        /**
         * Sets the predicate for the entry.
         *
         * @param predicate A predicate for the entry
         */
        public void setPredicate(Predicate<ActionArguments> predicate) {
            this.predicate = predicate;
        }

        /**
         * Gets the default action
         *
         * @return The default action
         */
        @NonNull
        public Action getDefaultAction() {
            return defaultAction;
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
        public void addSituationOverride(@NonNull Action action, @NonNull Situation situation) {
            //noinspection ConstantConditions
            if (situation == null || action == null) {
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
