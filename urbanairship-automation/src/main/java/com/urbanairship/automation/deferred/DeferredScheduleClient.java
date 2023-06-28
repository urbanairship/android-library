/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import android.net.Uri;

import com.urbanairship.UAirship;
import com.urbanairship.automation.TriggerContext;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Client to handle deferred schedules.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DeferredScheduleClient {

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestSession session;

    private static final String PLATFORM_KEY = "platform";
    private static final String CHANNEL_ID_KEY = "channel_id";
    private static final String PLATFORM_ANDROID = "android";
    private static final String PLATFORM_AMAZON = "amazon";
    private static final String TRIGGER_KEY = "trigger";
    private static final String TRIGGER_TYPE_KEY = "type";
    private static final String TRIGGER_GOAL_KEY = "goal";
    private static final String TRIGGER_EVENT_KEY = "event";
    private static final String TAG_OVERRIDES_KEY = "tag_overrides";
    private static final String ATTRIBUTE_OVERRIDES_KEY = "attribute_overrides";
    private static final String STATE_OVERRIDES_KEY = "state_overrides";

    private static final String AUDIENCE_MATCH_KEY = "audience_match";
    private static final String RESPONSE_TYPE_KEY = "type";
    private static final String MESSAGE_KEY = "message";
    private static final String IN_APP_MESSAGE_TYPE = "in_app_message";

    private final Supplier<StateOverrides> stateOverridesSupplier;

    /**
     * Default constructor.
     *
     * @param runtimeConfig The runtime config.
     */
    public DeferredScheduleClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, runtimeConfig.getRequestSession(), StateOverrides::defaultOverrides);
    }

    @VisibleForTesting
    DeferredScheduleClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                           @NonNull RequestSession session,
                           @NonNull Supplier<StateOverrides> stateOverridesSupplier) {
        this.runtimeConfig = runtimeConfig;
        this.session = session;
        this.stateOverridesSupplier = stateOverridesSupplier;
    }

    /**
     * Performs a request to resolve a deferred schedule.
     *
     * @param url The deferred schedule URL.
     * @param channelId The channel ID.
     * @param triggerContext The optional triggering context.
     * @param tagOverrides Tag overrides.
     * @param attributeOverrides Attribute overrides.
     * @return The deferred response.
     */
    public Response<Result> performRequest(@Nullable Uri url,
                                           @NonNull String channelId,
                                           @Nullable TriggerContext triggerContext,
                                           @Nullable List<TagGroupsMutation> tagOverrides,
                                           @Nullable List<AttributeMutation> attributeOverrides) throws RequestException {
        JsonMap.Builder requestBodyBuilder = JsonMap.newBuilder()
                                                    .put(PLATFORM_KEY, runtimeConfig.getPlatform() == UAirship.AMAZON_PLATFORM ? PLATFORM_AMAZON : PLATFORM_ANDROID)
                                                    .put(CHANNEL_ID_KEY, channelId);

        if (triggerContext != null) {
            requestBodyBuilder.put(TRIGGER_KEY, JsonMap.newBuilder()
                                                       .put(TRIGGER_TYPE_KEY, triggerContext.getTrigger().getTriggerName())
                                                       .put(TRIGGER_GOAL_KEY, triggerContext.getTrigger().getGoal())
                                                       .put(TRIGGER_EVENT_KEY, triggerContext.getEvent())
                                                       .build());
        }

        if (tagOverrides != null && !tagOverrides.isEmpty()) {
            requestBodyBuilder.put(TAG_OVERRIDES_KEY, JsonValue.wrapOpt(tagOverrides));
        }

        if (attributeOverrides != null && !attributeOverrides.isEmpty()) {
            requestBodyBuilder.put(ATTRIBUTE_OVERRIDES_KEY, JsonValue.wrapOpt(attributeOverrides));
        }

        requestBodyBuilder.put(STATE_OVERRIDES_KEY, stateOverridesSupplier.get());

        JsonMap requestBody = requestBodyBuilder.build();

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");

        Request request = new Request(
                url,
                "POST",
                new RequestAuth.ChannelTokenAuth(channelId),
                new RequestBody.Json(requestBody),
                headers
        );

        return session.execute(request, (status, responseHeaders, responseBody) -> {
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                return parseResponseBody(responseBody);
            } else {
                return null;
            }
        });
    }

    private Result parseResponseBody(String responseBody) throws JsonException {
        JsonMap response = JsonValue.parseString(responseBody).optMap();

        boolean audienceMatch = response.opt(AUDIENCE_MATCH_KEY).getBoolean(false);

        InAppMessage message = null;
        if (audienceMatch && response.opt(RESPONSE_TYPE_KEY).optString().equals(IN_APP_MESSAGE_TYPE)) {
            message = InAppMessage.fromJson(response.opt(MESSAGE_KEY), InAppMessage.SOURCE_REMOTE_DATA);
        }

        return new Result(audienceMatch, message);
    }

    /**
     * Deferred client result.
     */
    public static class Result {

        private final boolean isAudienceMatch;
        private final InAppMessage message;

        @VisibleForTesting
        public Result(boolean isAudienceMatch, @Nullable InAppMessage message) {
            this.isAudienceMatch = isAudienceMatch;
            this.message = message;
        }

        /**
         * Optional in-app message to be displayed.
         *
         * @return The optional in-app message.
         */
        @Nullable
        public InAppMessage getMessage() {
            return message;
        }

        /**
         * If the audience is a match for the schedule.
         *
         * @return {@code true} if the audience is a match, otherwise {@code false}.
         */
        public boolean isAudienceMatch() {
            return isAudienceMatch;
        }

    }

}
