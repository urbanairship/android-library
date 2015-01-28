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

import android.content.Intent;
import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UriUtils;

/**
 * Action for opening a URL for viewing.
 * <p/>
 * Accepted situations: Situation.PUSH_OPENED, Situation.WEB_VIEW_INVOCATION,
 * Situation.MANUAL_INVOCATION, and Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value types: String, {@link java.net.URL}, {@link android.net.Uri}
 * <p/>
 * Result value: The URI that was opened.
 * <p/>
 * Default Registration Names: ^u, open_external_url_action
 * <p/>
 * Default Registration Predicate: none
 */
public class OpenExternalUrlAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "open_external_url_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^u";

    /**
     * Performs the open external URL action.
     *
     * @param actionName The name of the action from the registry.
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    @Override
    public ActionResult perform(String actionName, ActionArguments arguments) {
        Uri uri = UriUtils.parse(arguments.getValue());

        Logger.info("Opening URI: " + uri);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        UAirship.getApplicationContext().startActivity(intent);
        return ActionResult.newResult(uri);
    }

    /**
     * The open external URL action accepts String, URL and URI argument value types.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the action can perform with the arguments,
     * otherwise <code>false</code>.
     */
    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
        if (!super.acceptsArguments(arguments)) {
            return false;
        }

        switch (arguments.getSituation()) {
            case PUSH_OPENED:
            case WEB_VIEW_INVOCATION:
            case MANUAL_INVOCATION:
            case FOREGROUND_NOTIFICATION_ACTION_BUTTON:
                return UriUtils.parse(arguments.getValue()) != null;
            default:
                return false;
        }
    }
}
