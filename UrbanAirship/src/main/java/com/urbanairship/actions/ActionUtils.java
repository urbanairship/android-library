/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.JSONUtils;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods related to actions.
 */
public class ActionUtils {

    /**
     * Check if a push bundle contains any registered actions.
     * <p/>
     * For each action name provided, the action's entry will be looked up in
     * the {@link com.urbanairship.actions.ActionRegistry} to determine if the
     * action is registered and to check if any of the aliases of the actions
     * are in the payload.
     *
     * @param bundle The push notification bundle
     * @param actionNames Action names to check
     * @return <code>true</code> if the bundle contains any of the action names,
     * <code>false</code> otherwise.
     */
    public static boolean containsRegisteredActions(Bundle bundle, String... actionNames) {
        if (actionNames == null || actionNames.length == 0) {
            return false;
        }

        // Get all the action names from the bundle to compare against
        Set<String> bundleActionNames = parseActionNames(bundle);

        if (bundleActionNames.isEmpty()) {
            return false;
        }

        // Build a set of all the action names and any alternative names
        // that the action is registered under
        Set<String> allActionNames = new HashSet<>();
        for (String actionName : actionNames) {
            ActionRegistry.Entry entry = ActionRegistry.shared().getEntry(actionName);
            if (entry != null) {
                allActionNames.addAll(entry.getNames());
            }
        }

        // Check if we have any of the actions in the bundle by checking
        // if the remove modifies the set or not
        return allActionNames.removeAll(bundleActionNames);
    }


    /**
     * Parses any action names from the push notification bundle
     *
     * @param bundle The push notification bundle
     * @return A set of all the action names found in the bundle.
     */
    public static Set<String> parseActionNames(Bundle bundle) {
        String actionsPayload = bundle.getString(PushMessage.EXTRA_ACTIONS);

        if (UAStringUtil.isEmpty(actionsPayload)) {
            return Collections.emptySet();
        }

        Map<String, Object> actionsMap;

        try {
            JSONObject actionsJSON = new JSONObject(actionsPayload);
            actionsMap = JSONUtils.convertToMap(actionsJSON);
            return actionsMap.keySet();
        } catch (JSONException e) {
            Logger.info("Invalid actions payload: " + actionsPayload);
            return Collections.emptySet();
        }
    }
}
