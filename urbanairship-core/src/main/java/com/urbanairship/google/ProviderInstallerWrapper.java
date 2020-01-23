/* Copyright Airship and Contributors */

package com.urbanairship.google;

import android.content.Context;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

/**
 * Wrapper around GMS ProviderInstaller.
 */
class ProviderInstallerWrapper {

    @NetworkProviderInstaller.Result
    static int installIfNeeded(Context context) {
        try {
            ProviderInstaller.installIfNeeded(context);
            return NetworkProviderInstaller.PROVIDER_INSTALLED;
        } catch (GooglePlayServicesRepairableException e) {
            return NetworkProviderInstaller.PROVIDER_RECOVERABLE_ERROR;
        } catch (GooglePlayServicesNotAvailableException e) {
            return NetworkProviderInstaller.PROVIDER_ERROR;
        }
    }
}
