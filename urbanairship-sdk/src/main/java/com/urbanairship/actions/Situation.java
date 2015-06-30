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

/**
 * An enum representing the possible situations for an {@link com.urbanairship.actions.Action}.
 */
public enum Situation {
    /**
     * Situation where an action is manually invoked.
     */
    MANUAL_INVOCATION,

    /**
     * Situation where an action is triggered when a push is received.
     */
    PUSH_RECEIVED,

    /**
     * Situation where an action is triggered when a push is opened.
     */
    PUSH_OPENED,

    /**
     * Situation where an action is triggered from a web view.
     */
    WEB_VIEW_INVOCATION,

    /**
     * Situation where an action is triggered from a foreground notification action button.
     */
    FOREGROUND_NOTIFICATION_ACTION_BUTTON,

    /**
     * Situation where an action is triggered from a background notification action button.
     */
    BACKGROUND_NOTIFICATION_ACTION_BUTTON
}
