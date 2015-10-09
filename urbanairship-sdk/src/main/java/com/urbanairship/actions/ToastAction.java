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

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.urbanairship.UAirship;

/**
 * An action that displays text in a toast.
 * <p/>
 * Accepted situations: Situation.PUSH_OPENED, Situation.WEB_VIEW_INVOCATION,
 * Situation.MANUAL_INVOCATION, Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON,
 * and Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value - A string with the toast text or a map with:
 * <ul>
 * <li>{@link #LENGTH_KEY}: int either {@link Toast#LENGTH_LONG} or {@link Toast#LENGTH_SHORT}, Optional</li>
 * <li>{@link #TEXT_KEY}: String, Required</li>
 * </ul>
 * <p/>
 * Result value: The arguments value.
 * <p/>
 * Default Registration Names: toast_action
 */
public class ToastAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "toast_action";

    /**
     * Key to define the Toast's text when providing the action's value as a map.
     */
    public static final String TEXT_KEY = "text";

    /**
     * Key to define the Toast's length when providing the action's value as a map.
     */
    public static final String LENGTH_KEY = "length";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        if (arguments.getSituation() == Situation.PUSH_RECEIVED) {
            return false;
        }

        if (arguments.getValue().getMap() != null) {
            return arguments.getValue().getMap().get(TEXT_KEY).isString();
        }

        return arguments.getValue().getString() != null;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        final String text;
        final int length;

        if (arguments.getValue().getMap() != null) {
            length = arguments.getValue().getMap().opt(LENGTH_KEY).getInt(Toast.LENGTH_SHORT);
            text = arguments.getValue().getMap().opt(TEXT_KEY).getString();
        } else {
            text = arguments.getValue().getString();
            length = Toast.LENGTH_SHORT;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (length == Toast.LENGTH_LONG) {
                    Toast.makeText(UAirship.getApplicationContext(), text, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(UAirship.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                }
            }
        });

        return ActionResult.newResult(arguments.getValue());
    }
}
