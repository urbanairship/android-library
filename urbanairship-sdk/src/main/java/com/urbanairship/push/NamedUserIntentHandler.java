/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;


class NamedUserIntentHandler {

    /**
     * Action to update named user association or disassociation.
     */
    static final String ACTION_UPDATE_NAMED_USER = "com.urbanairship.push.ACTION_UPDATE_NAMED_USER";

    /**
     * Key for storing the {@link NamedUser#getChangeToken()} in the {@link PreferenceDataStore} from the
     * last time the named user was updated.
     */
    static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    private final NamedUserApiClient client;
    private final NamedUser namedUser;
    private final PushManager pushManager;
    private final Context context;
    private final PreferenceDataStore dataStore;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    NamedUserIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(context, airship, dataStore, new NamedUserApiClient());
    }

    @VisibleForTesting
    NamedUserIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore, NamedUserApiClient client) {
        this.context = context;
        this.dataStore = dataStore;
        this.client = client;
        this.namedUser = airship.getNamedUser();
        this.pushManager = airship.getPushManager();
    }

    /**
     * Handles {@link AirshipService} intents for {@link com.urbanairship.push.PushManager}.
     *
     * @param intent The intent.
     */
    protected void handleIntent(Intent intent) {
        if (!intent.getAction().equals(ACTION_UPDATE_NAMED_USER)) {
            return;
        }

        String currentId = namedUser.getId();
        String changeToken = namedUser.getChangeToken();
        String lastUpdatedToken = dataStore.getString(LAST_UPDATED_TOKEN_KEY, null);
        String channelId = pushManager.getChannelId();

        if (changeToken == null && lastUpdatedToken == null) {
            // Skip since no one has set the named user ID. Usually from a new or re-install.
            return;
        }

        if (changeToken != null && changeToken.equals(lastUpdatedToken)) {
            // Skip since no change has occurred (token remain the same).
            Logger.debug("NamedUserIntentHandler - Named user already updated. Skipping.");
            return;
        }

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.info("The channel ID does not exist. Will retry when channel ID is available.");
            return;
        }

        Response response;

        if (currentId == null) {
            // When currentId is null, disassociate the current named user ID.
            response = client.disassociate(channelId);
        } else {
            // When currentId is non-null, associate the currentId.
            response = client.associate(currentId, channelId);
        }

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.info("Update named user failed, will retry.");
            AirshipService.retryServiceIntent(context, intent);
            return;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Update named user succeeded with status: " + response.getStatus());
            dataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            namedUser.startUpdateTagsService();
            return;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.info("Update named user failed with status: " + response.getStatus() +
                    " This action is not allowed when the app is in server-only mode.");
            return;
        }

        // 4xx
        Logger.info("Update named user failed with status: " + response.getStatus());
    }
}
