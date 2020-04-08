/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Event to set the associated identifiers.
 */
class AssociateIdentifiersEvent extends Event {

    private static final String TYPE = "associate_identifiers";

    @NonNull
    private final Map<String, String> ids;

    AssociateIdentifiersEvent(@NonNull AssociatedIdentifiers ids) {
        this.ids = ids.getIds();
    }

    @NonNull
    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isValid() {
        boolean isValid = true;

        if (ids.size() > AssociatedIdentifiers.MAX_IDS) {
            Logger.error("Associated identifiers exceeds %s", AssociatedIdentifiers.MAX_IDS);
            isValid = false;
        }

        for (Map.Entry<String, String> entry : ids.entrySet()) {
            if (entry.getKey().length() > AssociatedIdentifiers.MAX_CHARACTER_COUNT) {
                Logger.error("Associated identifiers key %s exceeds %s characters.", entry.getKey(), AssociatedIdentifiers.MAX_CHARACTER_COUNT);
                isValid = false;
            }

            if (entry.getValue().length() > AssociatedIdentifiers.MAX_CHARACTER_COUNT) {
                Logger.error("Associated identifiers for key %s exceeds %s characters.", entry.getKey(), AssociatedIdentifiers.MAX_CHARACTER_COUNT);
                isValid = false;
            }
        }

        return isValid;
    }

    /**
     * @hide
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonMap getEventData() {
        return JsonValue.wrapOpt(ids).optMap();
    }

}
