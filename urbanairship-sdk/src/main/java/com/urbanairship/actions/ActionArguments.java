/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * Container for the argument data passed to an {@link com.urbanairship.actions.Action}.
 */
public final class ActionArguments {

    /**
     * Metadata when running an action from the JavaScript interface with an associated RichPushMessage.
     * The value is stored as a String.
     */
    public static final String RICH_PUSH_ID_METADATA = "com.urbanairship.RICH_PUSH_ID_METADATA";

    /**
     * Metadata attached to action arguments when running actions from a push message.
     * The value is stored as a {@link com.urbanairship.push.PushMessage}.
     */
    public static final String PUSH_MESSAGE_METADATA = "com.urbanairship.PUSH_MESSAGE";

    /**
     * Metadata attached to action argument when running actions from a {@link com.urbanairship.push.notifications.NotificationActionButton}
     * with {@link com.urbanairship.push.notifications.LocalizableRemoteInput}.
     */
    public static final String REMOTE_INPUT_METADATA = "com.urbanairship.REMOTE_INPUT";

    /**
     * Metadata attached to action arguments when running scheduled actions from {@link com.urbanairship.automation.Automation}.
     * The value is stored as a {@link com.urbanairship.automation.ActionSchedule}.
     */
    public static final String ACTION_SCHEDULE_METADATA = "com.urbanairship.ACTION_SCHEDULE";

    /**
     * Metadata attached to action arguments when triggering an action from by name.
     * The value is stored as a String.
     */
    public static final String REGISTRY_ACTION_NAME_METADATA = "com.urbanairship.REGISTRY_ACTION_NAME";

    private final @Action.Situation int situation;
    private final ActionValue value;
    private final Bundle metadata;

    /**
     * Constructs ActionArguments.
     *
     * @param situation The situation.
     * @param value The argument's value.
     * @param metadata The argument's metadata.
     */
    public ActionArguments(@Action.Situation int situation, ActionValue value, Bundle metadata) {
        this.situation = situation;
        this.value = value == null ? new ActionValue() : value;
        this.metadata = metadata == null ? new Bundle() : new Bundle(metadata);
    }

    /**
     * Retrieves the argument value.
     *
     * @return The value as an Object.
     */
    @NonNull
    public ActionValue getValue() {
        return value;
    }

    /**
     * Retrieves the situation.
     *
     * @return The situation.
     */
    @Action.Situation
    public int getSituation() {
        return situation;
    }

    /**
     * Gets the metadata for the action arguments. Metadata provides additional information about the
     * calling environment.
     *
     * @return The arguments metadata.
     */
    @NonNull
    public Bundle getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "ActionArguments { situation: " + situation + ", value: " + value + ", metadata: " + metadata + " }";
    }
}
