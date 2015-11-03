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

package com.urbanairship.actions.tags;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;

import java.util.Set;

/**
 * An action that removes tags.
 * <p/>
 * Accepted situations: all
 * <p/>
 * Accepted argument value types: String for a single tag or Collection of Strings for multiple tags.
 * <p/>
 * Result value: null
 * <p/>
 * Default Registration Names: ^-t, remove_tags_action
 * <p/>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class RemoveTagsAction extends BaseTagsAction {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "remove_tags_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^-t";

    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Set<String> tags = getTags(arguments);
        Logger.info("RemoveTagsAction - Removing tags: " + tags);

        Set<String> currentTags = getPushManager().getTags();
        currentTags.removeAll(tags);

        getPushManager().setTags(currentTags);

        return ActionResult.newEmptyResult();
    }
}
