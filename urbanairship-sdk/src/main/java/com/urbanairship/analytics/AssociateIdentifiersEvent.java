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

package com.urbanairship.analytics;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;

import org.json.JSONObject;

import java.util.Map;

/**
 * Event to set the associated identifiers.
 */
class AssociateIdentifiersEvent extends Event {

    private static final String TYPE = "associate_identifiers";

    private final Map<String, String> ids;

    AssociateIdentifiersEvent(@NonNull AssociatedIdentifiers ids) {
        this.ids = ids.getIds();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isValid() {
        boolean isValid = true;

        if (ids.size() > AssociatedIdentifiers.MAX_IDS) {
            Logger.error("Associated identifiers exceeds " + AssociatedIdentifiers.MAX_IDS);
            isValid = false;
        }

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            if (entry.getKey().length() > AssociatedIdentifiers.MAX_CHARACTER_COUNT) {
                Logger.error("Associated identifiers key " + entry.getKey() + " exceeds " + AssociatedIdentifiers.MAX_CHARACTER_COUNT + "  characters.");
                isValid = false;
            }

            if (entry.getValue().length() > AssociatedIdentifiers.MAX_CHARACTER_COUNT) {
                Logger.error("Associated identifiers for key " + entry.getKey() + " exceeds " + AssociatedIdentifiers.MAX_CHARACTER_COUNT + " characters.");
                isValid = false;
            }
        }

        return isValid;
    }

    @Override
    protected JSONObject getEventData() {
        return new JSONObject(ids);
    }
}
