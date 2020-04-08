/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import com.urbanairship.json.JsonSerializable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * In-app message display content.
 */
public interface DisplayContent extends JsonSerializable {

    @StringDef({ BUTTON_LAYOUT_SEPARATE, BUTTON_LAYOUT_JOINED, BUTTON_LAYOUT_STACKED })
    @Retention(RetentionPolicy.SOURCE)
    @interface ButtonLayout {}

    /**
     * Buttons are displayed with a space between them.
     */
    @NonNull
    String BUTTON_LAYOUT_SEPARATE = "separate";

    /**
     * Buttons are displayed right next to each other.
     */
    @NonNull
    String BUTTON_LAYOUT_JOINED = "joined";

    /**
     * Buttons are stacked.
     */
    @NonNull
    String BUTTON_LAYOUT_STACKED = "stacked";

    // JSON KEYS
    @NonNull
    String BODY_KEY = "body";

    @NonNull
    String HEADING_KEY = "heading";

    @NonNull
    String BACKGROUND_COLOR_KEY = "background_color";

    @NonNull
    String PLACEMENT_KEY = "placement";

    @NonNull
    String BORDER_RADIUS_KEY = "border_radius";

    @NonNull
    String BUTTON_LAYOUT_KEY = "button_layout";

    @NonNull
    String BUTTONS_KEY = "buttons";

    @NonNull
    String MEDIA_KEY = "media";

    @NonNull
    String URL_KEY = "url";

    @NonNull
    String DISMISS_BUTTON_COLOR_KEY = "dismiss_button_color";

    @NonNull
    String TEMPLATE_KEY = "template";

    @NonNull
    String FOOTER_KEY = "footer";

    @NonNull
    String DURATION_KEY = "duration";

    /**
     * JSON key for flag to allow the message to display fullscreen.
     */
    @NonNull
    String ALLOW_FULLSCREEN_DISPLAY_KEY = "allow_fullscreen_display";

}
