/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

/**
 * Action for opening a deep link.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value types: URL as string
 * <p/>
 * Result value: The URI that was opened.
 * <p/>
 * Default Registration Names: ^d, deep_link_action
 * <p/>
 * Default Registration Predicate: none
 * <p/>
 * This action defaults to the {@link com.urbanairship.actions.OpenExternalUrlAction}
 * behavior, where it will try to open a deep link using an intent with the
 * data set to the arguments value.
 */
public class DeepLinkAction extends OpenExternalUrlAction {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "deep_link_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^d";

}
