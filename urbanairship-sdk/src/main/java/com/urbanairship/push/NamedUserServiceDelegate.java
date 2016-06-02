/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;

/**
 * Service delegate for the {@link PushService} to handle associating and dissociating a named user
 * from the Urban Airship channel.
 */
class NamedUserServiceDelegate extends BaseIntentService.Delegate {

    /**
     * Key for storing the {@link NamedUser#getChangeToken()} in the {@link PreferenceDataStore} from the
     * last time the named user was updated.
     */
    static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    private final NamedUserApiClient client;
    private final NamedUser namedUser;
    private final PushManager pushManager;

    public NamedUserServiceDelegate(Context context, PreferenceDataStore dataStore) {
        this(context, dataStore, new NamedUserApiClient(), UAirship.shared().getPushManager(),
                UAirship.shared().getPushManager().getNamedUser());
    }

    public NamedUserServiceDelegate(Context context, PreferenceDataStore dataStore, NamedUserApiClient client, PushManager pushManager, NamedUser namedUser) {
        super(context, dataStore);
        this.client = client;
        this.namedUser = namedUser;
        this.pushManager = pushManager;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!intent.getAction().equals(PushService.ACTION_UPDATE_NAMED_USER)) {
            return;
        }

        String currentId = namedUser.getId();
        String changeToken = namedUser.getChangeToken();
        String lastUpdatedToken = getDataStore().getString(LAST_UPDATED_TOKEN_KEY, null);
        String channelId = pushManager.getChannelId();

        if (changeToken == null && lastUpdatedToken == null) {
            // Skip since no one has set the named user ID. Usually from a new or re-install.
            return;
        }

        if (changeToken != null && changeToken.equals(lastUpdatedToken)) {
            // Skip since no change has occurred (token remain the same).
            Logger.debug("NamedUserServiceDelegate - Named user already updated. Skipping.");
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
            retryIntent(intent);
            return;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Update named user succeeded with status: " + response.getStatus());
            getDataStore().put(LAST_UPDATED_TOKEN_KEY, changeToken);
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
