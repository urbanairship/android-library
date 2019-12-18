package com.urbanairship.modules;

import androidx.annotation.RestrictTo;

/**
 * Accengage push integration.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AccengageModuleLoader extends ModuleLoader {
    AccengageNotificationHandler getAccengageNotificationHandler();
}
