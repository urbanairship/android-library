/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import android.net.Uri;

import androidx.annotation.AnyRes;
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
import com.urbanairship.util.Checks;
import com.urbanairship.util.PlatformUtils;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

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
    private static final String EMAIL_PATH = "api/channels/email/";
    private static final String SMS_PATH = "api/channels/sms/";

    private static final String SUBSCRIPTION_LIST_PATH = "api/subscription_lists/contacts/";
    private static final String SUBSCRIPTION_LISTS_KEY = "subscription_lists";
    private static final String SCOPE_KEY = "scope";
    private static final String LIST_IDS_KEY = "list_ids";


    private static final String UNINSTALL_PATH = "uninstall";
    private static final String OPTOUT_PATH = "opt-out";


    private static final String NAMED_USER_ID = "named_user_id";
    private static final String CHANNEL_ID = "channel_id";
    private static final String CHANNEL_KEY = "channel";
    private static final String DEVICE_TYPE = "device_type";
    private static final String TYPE = "type";
    private static final String CONTACT_ID = "contact_id";
    private static final String IS_ANONYMOUS = "is_anonymous";
    private static final String TAGS = "tags";
    private static final String ATTRIBUTES = "attributes";
    private static final String SUBSCRIPTION_LISTS = "subscription_lists";
    private static final String TIMEZONE = "timezone";
    private static final String ADDRESS = "address";
    private static final String EMAIL_ADDRESS = "email_address";
    private static final String LOCALE_COUNTRY = "locale_country";
    private static final String LOCALE_LANGUAGE = "locale_language";
    private static final String MSISDN_KEY = "msisdn";
    private static final String SENDER_KEY = "sender";
    private static final String OPTED_IN_KEY = "opted_in";

    public enum EmailType {
        COMMERCIAL_OPTED_IN,
        COMMERCIAL_OPTED_OUT,
        TRANSACTIONAL_OPTED_IN,
        TRANSACTIONAL_OPTED_OUT
    }

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
                .execute((status, headers, responseBody) -> {
                    if (UAHttpStatusUtil.inSuccessRange(status)) {
                        String contactId = JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).getString();
                        Checks.checkNotNull(contactId, "Missing contact ID");
                        boolean isAnonymous = JsonValue.parseString(responseBody).optMap().opt(IS_ANONYMOUS).getBoolean(false);
                        return new ContactIdentity(contactId, isAnonymous, null);
                    }
                    return null;
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
                .execute((status, headers, responseBody) -> {
                    if (UAHttpStatusUtil.inSuccessRange(status)) {
                        String contactId1 = JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).getString();
                        return new ContactIdentity(contactId1, false, namedUserId);
                    }
                    return null;
                });
    }

    @NonNull
    Response<String> registerEmail(@NonNull String emailAddress, @Nullable List<EmailType> optInOptions) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(EMAIL_PATH)
                               .build();

        JsonMap.Builder builder = JsonMap.newBuilder()
                .put(TYPE, "email")
                .put(ADDRESS, emailAddress)
                .put(TIMEZONE, TimeZone.getDefault().getID())
                .put(LOCALE_LANGUAGE, Locale.getDefault().getLanguage())
                .put(LOCALE_COUNTRY, Locale.getDefault().getCountry());

                if (optInOptions != null) {
                    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    String date = df.format(Calendar.getInstance().getTime());

                    for (EmailType emailType : optInOptions) {
                        builder.put(emailType.toString().toLowerCase(), date);
                    }
                }

        JsonMap payloadContent = builder.build();

        JsonMap payload = JsonMap.newBuilder()
                            .put(CHANNEL_KEY, payloadContent)
                            .build();

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(payload)
                .setAirshipJsonAcceptsHeader()
                .setAirshipUserAgent(runtimeConfig)
                .execute((status, headers, responseBody) -> {
                    if (UAHttpStatusUtil.inSuccessRange(status)) {
                        return JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID).getString();
                    }
                    return null;
                });
    }

    @NonNull
    Response<String> updateEmail(@NonNull String emailAddress, @NonNull String channelId, @Nullable List<EmailType> optInOptions) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(EMAIL_PATH + channelId)
                               .build();

        JsonMap.Builder builder = JsonMap.newBuilder()
                                         .put(TYPE, "email")
                                         .put(ADDRESS, emailAddress);

        if (optInOptions != null) {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String date = df.format(Calendar.getInstance().getTime());

            for (EmailType emailType : optInOptions) {
                builder.put(emailType.toString().toLowerCase(), date);
            }
        }

        JsonMap payloadContent = builder.build();

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_KEY, payloadContent)
                                 .build();

        return requestFactory.createRequest()
                             .setOperation("PUT", url)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(payload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute((status, headers, responseBody) -> {
                                 if (UAHttpStatusUtil.inSuccessRange(status)) {
                                     return JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID).getString();
                                 }
                                 return null;
                             });
    }

    @NonNull
    Response<String> registerSms(@NonNull String msisdn, @NonNull String sender, @NonNull boolean optinStatus) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(SMS_PATH)
                               .build();

        JsonMap.Builder builder = JsonMap.newBuilder()
                                         .put(MSISDN_KEY, msisdn)
                                         .put(SENDER_KEY, sender)
                                         .put(TIMEZONE, TimeZone.getDefault().getID())
                                         .put(LOCALE_LANGUAGE, Locale.getDefault().getLanguage())
                                         .put(LOCALE_COUNTRY, Locale.getDefault().getCountry());

        if (optinStatus) {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String date = df.format(Calendar.getInstance().getTime());
            builder.put(OPTED_IN_KEY, date);
        }

        JsonMap payload = builder.build();

        return requestFactory.createRequest()
                             .setOperation("POST", url)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(payload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute((status, headers, responseBody) -> {
                                 if (UAHttpStatusUtil.inSuccessRange(status)) {
                                     return JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID).getString();
                                 }
                                 return null;
                             });
    }

    @NonNull
    Response<Void> optOutSms(@NonNull String msisdn, @NonNull String sender) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(SMS_PATH + OPTOUT_PATH)
                               .build();

        JsonMap payload = JsonMap.newBuilder()
                                 .put(MSISDN_KEY, msisdn)
                                 .put(SENDER_KEY, sender)
                                 .build();

        return requestFactory.createRequest()
                             .setOperation("POST", url)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(payload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute();
    }

    @NonNull
    Response<Void> updateSms(@NonNull String msisdn, @NonNull String sender, @NonNull boolean optinStatus, @NonNull String channelId) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(SMS_PATH + channelId)
                               .build();

        JsonMap.Builder builder = JsonMap.newBuilder()
                                         .put(MSISDN_KEY, msisdn)
                                         .put(SENDER_KEY, sender)
                                         .put(TIMEZONE, TimeZone.getDefault().getID())
                                         .put(LOCALE_LANGUAGE, Locale.getDefault().getLanguage())
                                         .put(LOCALE_COUNTRY, Locale.getDefault().getCountry());

        if (optinStatus) {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            String date = df.format(Calendar.getInstance().getTime());
            builder.put(OPTED_IN_KEY, date);
        }

        JsonMap payload = builder.build();

        return requestFactory.createRequest()
                             .setOperation("PUT", url)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(payload)
                             .setAirshipJsonAcceptsHeader()
                             .setAirshipUserAgent(runtimeConfig)
                             .execute();
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
                .execute((status, headers, responseBody) -> {
                    if (UAHttpStatusUtil.inSuccessRange(status)) {
                        String contactId = JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).getString();
                        return new ContactIdentity(contactId, true, null);
                    }
                    return null;
                });
    }

    @NonNull
    Response<Void> update(@NonNull String identifier,
                          @Nullable List<TagGroupsMutation> tagGroupMutations,
                          @Nullable List<AttributeMutation> attributeMutations,
                          @Nullable List<ScopedSubscriptionListMutation> subscriptionListMutations) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                .deviceUrl()
                .appendEncodedPath(UPDATE_PATH + identifier)
                .build();

        JsonMap.Builder builder = JsonMap.newBuilder();

        if (tagGroupMutations != null && !tagGroupMutations.isEmpty()) {
            JsonMap.Builder tagBuilder = JsonMap.newBuilder();

            List<TagGroupsMutation> tags = TagGroupsMutation.collapseMutations(tagGroupMutations);
            for (TagGroupsMutation tag : tags) {
                if (tag.toJsonValue().isJsonMap()) {
                    tagBuilder.putAll(tag.toJsonValue().optMap());
                }
            }

            builder.put(TAGS, tagBuilder.build());
        }

        if (attributeMutations != null && !attributeMutations.isEmpty()) {
            builder.putOpt(ATTRIBUTES, AttributeMutation.collapseMutations(attributeMutations));
        }

        if (subscriptionListMutations != null && !subscriptionListMutations.isEmpty()) {
            builder.putOpt(SUBSCRIPTION_LISTS, ScopedSubscriptionListMutation.collapseMutations(subscriptionListMutations));
        }

        return requestFactory.createRequest()
                .setOperation("POST", url)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(builder.build())
                .setAirshipJsonAcceptsHeader()
                .setAirshipUserAgent(runtimeConfig)
                .execute((status, headers, responseBody) -> {
                    Logger.verbose("Update contact response status: %s body: %s", status, responseBody);
                    return null;
                });
    }

    /**
     * Fetches the current set of subscriptions for the contact.
     *
     * @return The response.
     * @throws RequestException
     */
    @NonNull
    Response<Map<String, Set<Scope>>> getSubscriptionLists(@NonNull String identifier) throws RequestException {
        Uri uri = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(SUBSCRIPTION_LIST_PATH + identifier)
                               .build();

        return requestFactory.createRequest()
                             .setOperation("GET", uri)
                             .setAirshipUserAgent(runtimeConfig)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setAirshipJsonAcceptsHeader()
                             .execute((ResponseParser<Map<String, Set<Scope>>>) (status, headers, responseBody) -> {
                                 Logger.verbose("Fetch contact subscription list status: %s body: %s", status, responseBody);

                                 if (!UAHttpStatusUtil.inSuccessRange(status)) {
                                     return null;
                                 }


                                 JsonValue json = JsonValue.parseString(responseBody);
                                 Map<String, Set<Scope>> subscriptionLists = new HashMap<>();

                                 for (JsonValue entryJson : json.requireMap().opt(SUBSCRIPTION_LISTS_KEY).optList()) {
                                     Scope scope = Scope.fromJson(entryJson.optMap().opt(SCOPE_KEY));
                                     for (JsonValue listIdJson : entryJson.optMap().opt(LIST_IDS_KEY).optList()) {
                                         String listId = listIdJson.requireString();
                                         Set<Scope> scopes = subscriptionLists.get(listId);
                                         if (scopes == null) {
                                             scopes = new HashSet<>();
                                             subscriptionLists.put(listId, scopes);
                                         }
                                         scopes.add(scope);
                                     }
                                 }
                                 return subscriptionLists;
                             });
    }

}
