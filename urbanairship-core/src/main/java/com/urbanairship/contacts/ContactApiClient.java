/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.PlatformUtils;
import com.urbanairship.util.UAHttpStatusUtil;

import java.util.List;
import java.util.Map;

/**
 * A high level abstraction for performing Contact API requests.
 */
class ContactApiClient {

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestFactory requestFactory;

    private static final String RESOLVE_PATH = "api/contacts/resolve/";
    private static final String IDENTIFY_PATH = "api/contacts/identify/";
    private static final String RESET_PATH = "api/contacts/reset/";
    private static final String UPDATE_PATH = "api/contacts/";

    private static final String NAMED_USER_ID = "named_user_id";
    private static final String CHANNEL_ID = "channel_id";
    private static final String DEVICE_TYPE = "device_type";
    private static final String CONTACT_ID = "contact_id";
    private static final String IS_ANONYMOUS = "is_anonymous";
    private static final String TAGS = "tags";
    private static final String ATTRIBUTES = "attributes";
    private static final String TAG_WARNINGS= "tag_warnings";
    private static final String ATTRIBUTE_WARNINGS= "attribute_warnings";

    ContactApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    ContactApiClient(@NonNull AirshipRuntimeConfig runtimeConfig, @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
    }

    @NonNull
    Response<ContactIdentity> resolve(@NonNull String channelId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                .deviceUrl()
                .appendEncodedPath(RESOLVE_PATH)
                .build();

        String deviceType = PlatformUtils.getDeviceType(runtimeConfig.getPlatform());

        JsonMap payload = JsonMap.newBuilder()
                .put(CHANNEL_ID, channelId)
                .put(DEVICE_TYPE, deviceType)
                .build();

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(payload)
                .setAirshipJsonAcceptsHeader()
                .setAirshipUserAgent(runtimeConfig)
                .execute(new ResponseParser<ContactIdentity>() {
                    @Override
                    public ContactIdentity parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (UAHttpStatusUtil.inSuccessRange(status)) {
                            String contactId = JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).getString();
                            boolean isAnonymous = JsonValue.parseString(responseBody).optMap().opt(IS_ANONYMOUS).getBoolean();
                            return new ContactIdentity(contactId, isAnonymous, null);
                        }
                        return null;
                    }
                });
    }

    @NonNull
    Response<ContactIdentity> identify(@NonNull final String namedUserId, @NonNull String channelId, @Nullable String contactId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                .deviceUrl()
                .appendEncodedPath(IDENTIFY_PATH)
                .build();

        String deviceType = PlatformUtils.getDeviceType(runtimeConfig.getPlatform());

        JsonMap.Builder builder = JsonMap.newBuilder()
                .put(NAMED_USER_ID, namedUserId)
                .put(CHANNEL_ID, channelId)
                .put(DEVICE_TYPE, deviceType);

        if (contactId != null) {
            builder.put(CONTACT_ID, contactId);
        }

        JsonMap payload = builder.build();

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(payload)
                .setAirshipJsonAcceptsHeader()
                .setAirshipUserAgent(runtimeConfig)
                .execute(new ResponseParser<ContactIdentity>() {
                    @Override
                    public ContactIdentity parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (UAHttpStatusUtil.inSuccessRange(status)) {
                            String contactId = JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).getString();
                            return new ContactIdentity(contactId, false, namedUserId);
                        }
                        return null;
                    }
                });
    }

    @NonNull
    Response<ContactIdentity> reset(@NonNull String channelId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                .deviceUrl()
                .appendEncodedPath(RESET_PATH)
                .build();

        String deviceType = PlatformUtils.getDeviceType(runtimeConfig.getPlatform());

        JsonMap payload = JsonMap.newBuilder()
                .put(CHANNEL_ID, channelId)
                .put(DEVICE_TYPE, deviceType)
                .build();

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(payload)
                .setAirshipJsonAcceptsHeader()
                .setAirshipUserAgent(runtimeConfig)
                .execute(new ResponseParser<ContactIdentity>() {
                    @Override
                    public ContactIdentity parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (UAHttpStatusUtil.inSuccessRange(status)) {
                            String contactId = JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).getString();
                            return new ContactIdentity(contactId, true, null);
                        }
                        return null;
                    }
                });
    }

    @NonNull
    Response<Void> update(@NonNull String identifier, @Nullable List<TagGroupsMutation> tagGroupMutations, @Nullable final List<AttributeMutation> attributeMutations) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                .deviceUrl()
                .appendEncodedPath(UPDATE_PATH + identifier)
                .build();

        JsonMap.Builder builder = JsonMap.newBuilder();

        if (tagGroupMutations != null) {
            JsonMap.Builder tagBuilder = JsonMap.newBuilder();

            List<TagGroupsMutation> tags = TagGroupsMutation.collapseMutations(tagGroupMutations);
            for (TagGroupsMutation tag : tags) {
                if (tag.toJsonValue().isJsonMap()) {
                    tagBuilder.putAll(tag.toJsonValue().optMap());
                }
            }

            builder.put(TAGS, tagBuilder.build());
        }

        if (attributeMutations != null) {
            List<AttributeMutation> attributeList = AttributeMutation.collapseMutations(attributeMutations);

            try {
                builder.put(ATTRIBUTES, JsonValue.wrap(attributeList));
            } catch (JsonException e) {
                Logger.debug(e, "Unable to parse attribute list.");
            }
        }

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(builder.build())
                .setAirshipJsonAcceptsHeader()
                .setAirshipUserAgent(runtimeConfig)
                .execute(new ResponseParser<Void>() {
                    @Override
                    public Void parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (UAHttpStatusUtil.inSuccessRange(status)) {
                            String tagWarnings = JsonValue.parseString(responseBody).optMap().opt(TAG_WARNINGS).getString();
                            String attributeWarnings = JsonValue.parseString(responseBody).optMap().opt(ATTRIBUTE_WARNINGS).getString();

                            if (tagWarnings != null) {
                                Logger.debug("ContactApiClient - " + tagWarnings);
                            }

                            if (attributeWarnings != null) {
                                Logger.debug("ContactApiClient - " + attributeWarnings);
                            }
                        }
                        return null;
                    }
                });
    }

}
