package com.urbanairship.modules.accengage;

import com.urbanairship.modules.ModuleLoader;

import androidx.annotation.RestrictTo;

/**
 * Accengage push integration.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AccengageModuleLoader extends ModuleLoader {
    AccengageNotificationHandler getAccengageNotificationHandler();
}
