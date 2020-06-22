/* Copyright Airship and Contributors */

package com.urbanairship.push.hms;

import android.content.Context;

import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * HMS push provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class HmsPushProvider implements PushProvider, AirshipVersionInfo  {

    private static final String HCM_SCOPE = "HCM";
    private static final String APP_ID_KEY = "client/app_id";

    @Override
    public int getPlatform() {
        return UAirship.ANDROID_PLATFORM;
    }

    @NonNull
    @Override
    public String getDeliveryType() {
        return PushProvider.HMS_DELIVERY_TYPE;
    }

    @Nullable
    @Override
    public String getRegistrationToken(@NonNull Context context) throws RegistrationException {
        String token;

        try {
            String appId = AGConnectServicesConfig.fromContext(context).getString(APP_ID_KEY);
            if (appId == null) {
                return null;
            }

            token = HmsInstanceId.getInstance(context).getToken(appId, HCM_SCOPE);
        } catch (Exception e) {
            throw new RegistrationException("HMS error " + e.getMessage(), true, e);
        }

        if (UAStringUtil.isEmpty(token)) {
            String cachedToken = HmsTokenCache.shared().get(context);
            if (UAStringUtil.isEmpty(cachedToken)) {
                throw new RegistrationException("Empty HMS registration token", true);
            } else {
                return cachedToken;
            }
        } else {
            HmsTokenCache.shared().set(context, token);
            return token;
        }
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        try {
            String appId = AGConnectServicesConfig.fromContext(context).getString(APP_ID_KEY);
            return !UAStringUtil.isEmpty(appId);
        } catch (Exception e) {
            Logger.error(e, "HmsPushProvider - HMS availability check failed.");
            return false;
        }
    }

    @Override
    public boolean isSupported(@NonNull Context context) {
        try {
            return HuaweiApiAvailability.getInstance().isHuaweiMobileNoticeAvailable(context) == 0;
        } catch (Exception e) {
            Logger.error(e, "HmsPushProvider - HMS is supported check failed.");
            return false;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "HMS Push Provider " + getAirshipVersion();
    }

    @NonNull
    @Override
    public String getAirshipVersion() {
        return BuildConfig.AIRSHIP_VERSION;
    }

    @NonNull
    @Override
    public String getPackageVersion() {
        return BuildConfig.SDK_VERSION;
    }

}
