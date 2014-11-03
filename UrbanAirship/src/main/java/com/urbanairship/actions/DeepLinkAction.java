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

/**
 * Action for opening a deep link.
 * <p/>
 * Accepted situations: Situation.PUSH_OPENED, Situation.WEB_VIEW_INVOCATION, and
 * Situation.MANUAL_INVOCATION.
 * <p/>
 * Accepted argument value types: String, {@link java.net.URL}, or {@link android.net.Uri}
 * <p/>
 * Result value: The URI that was opened.
 * <p/>
 * Default Registration Names: ^d, deep_link_action
 * <p/>
 * Default Registration Predicate: none
 * <p/>
 * This action defaults to the {@link com.urbanairship.actions.OpenExternalUrlAction}
 * behavior, where it will try to open a deep link using an intent with the
 * data set to the arguments value.
 */
public class DeepLinkAction extends OpenExternalUrlAction {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "deep_link_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^d";

}
