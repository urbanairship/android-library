/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

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
    protected JsonMap getEventData() {
        return JsonValue.wrapOpt(ids).optMap();
    }
}
