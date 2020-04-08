package com.urbanairship.modules.accengage;

import com.urbanairship.AirshipComponent;
import com.urbanairship.modules.Module;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Accengage module.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AccengageModule extends Module {

    private final AccengageNotificationHandler notificationHandler;

    /**
     * Default constructor.
     *
     * @param component The component.
     * @param notificationHandler The accengage notification handler.
     */
    public AccengageModule(@NonNull AirshipComponent component, @NonNull AccengageNotificationHandler notificationHandler) {
        super(Collections.singleton(component));
        this.notificationHandler = notificationHandler;
    }

    /**
     * Gets the Accengage Notification Handler.
     *
     * @return The notification handler.
     */
    @NonNull
    public AccengageNotificationHandler getAccengageNotificationHandler() {
        return notificationHandler;
    }

}
