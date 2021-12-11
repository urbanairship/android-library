/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Safe area on a device is usually the area of the nub/status bar and
 * sometimes the navigation on the bottom.
 *
 * In order to use safe area, the banner or modal placement has to first
 * ignore it in order for the safe area insets to be available. The insets are
 * then passed to the children until its consumed by a layout view.
 *
 * A linear layout will consume the insets (resetting them to 0) and draw itself in
 * the safe area.
 *
 * A container view will allow each child to handle the insets. If safe are is ignored,
 * the insets will be available to child view. If they are not ignored, the insets will be consumed
 * and applied, as margins for the child.
 *
 * Other view wrappers like the controllers and scroll view will just pass insets to the wrapped view.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface SafeAreaAware {

    boolean shouldIgnoreSafeArea();

    static boolean ignoreSafeAreaFromJson(@NonNull JsonMap json) {
        return json.opt("ignore_safe_area").getBoolean(false);
    }
}
