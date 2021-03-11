/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.html.HtmlDisplayContent;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.AirshipComponentUtils;
import com.urbanairship.util.Checks;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.UriUtils;

import java.util.UUID;
import java.util.concurrent.Callable;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Schedules a landing page to display ASAP.
 *
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 *
 * Accepted argument value types: URL defined as either a String or a Map containing the key
 * "url" that defines the URL, an optional "width", "height" in dps as an int or "fill" string,
 * an optional "aspect_lock" option as a boolean.
 *
 * The aspect_lock option guarantees that if the message does not fit, it will be resized at the
 * same aspect ratio defined by the provided width and height parameters.
 *
 *
 * Default Registration Names: ^p, landing_page_action
 */
public class LandingPageAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "landing_page_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^p";

    /**
     * The content's url payload key
     */
    @NonNull
    public static final String URL_KEY = "url";

    /**
     * Legacy key for aspect lock.
     */
    @NonNull
    private static final String LEGACY_ASPECT_LOCK_KEY = "aspectLock";

    /**
     * Default border radius.
     */
    public final static float DEFAULT_BORDER_RADIUS = 2;

    private final Callable<InAppAutomation> inAppCallable;

    private float borderRadius = DEFAULT_BORDER_RADIUS;

    /**
     * Default constructor.
     */
    public LandingPageAction() {
        this(AirshipComponentUtils.callableForComponent(InAppAutomation.class));
    }

    @VisibleForTesting
    LandingPageAction(@NonNull Callable<InAppAutomation> inAppCallable) {
        this.inAppCallable = inAppCallable;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        InAppAutomation inAppAutomation;
        try {
            inAppAutomation = inAppCallable.call();
        } catch (Exception e) {
            return ActionResult.newErrorResult(e);
        }

        final Uri uri = parseUri(arguments);
        Checks.checkNotNull(uri, "URI should not be null");

        inAppAutomation.schedule(createSchedule(uri, arguments));
        return ActionResult.newEmptyResult();
    }

    /**
     * Sets the border radius to apply to the {@link HtmlDisplayContent}.
     *
     * @param borderRadius The border radius.
     */
    public void setBorderRadius(@FloatRange(from = 0.0) float borderRadius) {
        this.borderRadius = borderRadius;
    }

    /**
     * Gets the border radius.
     *
     * @return The border radius.
     */
    public float getBorderRadius() {
        return borderRadius;
    }

    /**
     * Called to create the schedule Info.
     *
     * @param uri The landing page Uri.
     * @param arguments The arguments.
     * @return The schedule info.
     */
    @NonNull
    protected Schedule<InAppMessage> createSchedule(@NonNull Uri uri, @NonNull ActionArguments arguments) {
        JsonMap options = arguments.getValue().toJsonValue().optMap();

        int width = options.opt(HtmlDisplayContent.WIDTH_KEY).getInt(0);
        int height = options.opt(HtmlDisplayContent.HEIGHT_KEY).getInt(0);
        boolean aspectLock;

        if (options.containsKey(HtmlDisplayContent.ASPECT_LOCK_KEY)) {
            aspectLock = options.opt(HtmlDisplayContent.ASPECT_LOCK_KEY).getBoolean(false);
        } else {
            aspectLock = options.opt(LEGACY_ASPECT_LOCK_KEY).getBoolean(false);
        }

        String messageId;
        boolean reportEvents = false;
        PushMessage pushMessage = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
        if (pushMessage != null && pushMessage.getSendId() != null) {
            messageId = pushMessage.getSendId();
            reportEvents = true;
        } else {
            messageId = UUID.randomUUID().toString();
        }

        InAppMessage.Builder messageBuilder = InAppMessage.newBuilder()
                                                          .setDisplayContent(HtmlDisplayContent.newBuilder()
                                                                                               .setUrl(uri.toString())
                                                                                               .setAllowFullscreenDisplay(false)
                                                                                               .setBorderRadius(borderRadius)
                                                                                               .setSize(width, height, aspectLock)
                                                                                               .setRequireConnectivity(false)
                                                                                               .build())
                                                          .setReportingEnabled(reportEvents)
                                                          .setDisplayBehavior(InAppMessage.DISPLAY_BEHAVIOR_IMMEDIATE);

        InAppMessage message = extendMessage(messageBuilder).build();

        Schedule.Builder<InAppMessage> scheduleInfoBuilder = Schedule.newBuilder(message)
                                                       .setId(messageId)
                                                       .addTrigger(Triggers.newActiveSessionTriggerBuilder().setGoal(1).build())
                                                       .setLimit(1)
                                                       .setPriority(Integer.MIN_VALUE);

        return extendSchedule(scheduleInfoBuilder).build();
    }

    /**
     * Can be used to customize the {@link InAppMessage}.
     *
     * @param builder The builder.
     * @return The builder.
     */
    @NonNull
    protected InAppMessage.Builder extendMessage(@NonNull InAppMessage.Builder builder) {
        return builder;
    }

    /**
     * Can be used to customize the {@link Schedule}.
     *
     * @param builder The builder.
     * @return The builder.
     */
    @NonNull
    protected Schedule.Builder<InAppMessage> extendSchedule(@NonNull Schedule.Builder<InAppMessage> builder) {
        return builder;
    }

    /**
     * Checks if the argument's value can be parsed to a URI and if the situation is not
     * Action.SITUATION_PUSH_RECEIVED.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the action can perform with the arguments,
     * otherwise <code>false</code>.
     */
    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                Uri uri = parseUri(arguments);

                return uri != null;
            case Action.SITUATION_PUSH_RECEIVED:
            case Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            default:
                return false;
        }
    }

    /**
     * Parses the ActionArguments for a landing page URI.
     *
     * @param arguments The action arguments.
     * @return A landing page Uri, or null if the arguments could not be parsed or is not allowed.
     */
    @Nullable
    protected Uri parseUri(@NonNull ActionArguments arguments) {
        String uriValue;

        if (arguments.getValue().getMap() != null) {
            uriValue = arguments.getValue().getMap().opt(URL_KEY).getString();
        } else {
            uriValue = arguments.getValue().getString();
        }

        if (uriValue == null) {
            return null;
        }

        // Assume a string
        Uri uri = UriUtils.parse(uriValue);
        if (uri == null || UAStringUtil.isEmpty(uri.toString())) {
            return null;
        }

        // Add https scheme if not set
        if (UAStringUtil.isEmpty(uri.getScheme())) {
            uri = Uri.parse("https://" + uri);
        }

        if (!UAirship.shared().getUrlAllowList().isAllowed(uri.toString(), UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("Landing page URL is not allowed: %s", uri);
            return null;
        }

        return uri;
    }

}
