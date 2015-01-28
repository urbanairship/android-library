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

import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.push.PushManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract tag action class.
 */
public abstract class BaseTagsAction extends Action {

    /**
     * Gets the push manager
     *
     * @return A push manager instance
     */
    protected PushManager getPushManager() {
        return UAirship.shared().getPushManager();
    }

    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
        return getTags(arguments) != null;
    }

    /**
     * Parses the arguments for a set of tags
     *
     * @param arguments The action arguments
     * @return A set of tags from the arguments, or null if no tags
     * could be parsed from the arguments
     */
    protected Set<String> getTags(ActionArguments arguments) {
        if (arguments == null || arguments.getValue().isNull()) {
            return null;
        }

        if (arguments.getValue().getString() != null) {
            Set<String> tags = new HashSet<>();
            tags.add(String.valueOf(arguments.getValue().getString()));
            return tags;
        }

        if (arguments.getValue().getList() != null) {
            Set<String> tags = new HashSet<>();

            for (ActionValue tag : arguments.getValue().getList()) {
                if (tag.getString() != null) {
                    tags.add(tag.getString());
                }
            }

            return tags;
        }

        return null;
    }
}
