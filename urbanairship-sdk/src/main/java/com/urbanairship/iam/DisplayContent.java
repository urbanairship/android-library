/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.StringDef;

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
    String BUTTON_LAYOUT_SEPARATE = "separate";

    /**
     * Buttons are displayed right next to each other.
     */
    String BUTTON_LAYOUT_JOINED = "joined";

    /**
     * Buttons are stacked.
     */
    String BUTTON_LAYOUT_STACKED = "stacked";

    // JSON KEYS
    String BODY_KEY = "body";
    String HEADING_KEY = "heading";
    String BACKGROUND_COLOR_KEY = "background_color";
    String PLACEMENT_KEY = "placement";
    String BORDER_RADIUS_KEY = "border_radius";
    String BUTTON_LAYOUT_KEY = "button_layout";
    String BUTTONS_KEY = "buttons";
    String MEDIA_KEY = "media";
    String URL_KEY = "url";
    String DISMISS_BUTTON_COLOR_KEY = "dismiss_button_color";
    String TEMPLATE_KEY = "template";
    String FOOTER_KEY = "footer";
    String DURATION_KEY = "duration";
}
