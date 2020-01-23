/* Copyright Airship and Contributors */

package com.urbanairship.google;

import android.content.Context;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * GMS network provider installer.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NetworkProviderInstaller {

    private static Boolean isProviderInstallerDependencyAvailable;

    /**
     * Provider installed.
     */
    public static final int PROVIDER_INSTALLED = 0;

    /**
     * Provider failed to install, but should be retried.
     */
    public static final int PROVIDER_RECOVERABLE_ERROR = 1;

    /**
     * Provider failed to install.
     */
    public static final int PROVIDER_ERROR = 2;

    @IntDef({ PROVIDER_INSTALLED, PROVIDER_RECOVERABLE_ERROR, PROVIDER_ERROR })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Result {}

    /**
     * Checks if Google Play services dependency is available for Provider Installer.
     *
     * @return <code>true</code> if available, otherwise <code>false</code>.
     */
    private static boolean isProviderInstallerDependencyAvailable() {
        if (isProviderInstallerDependencyAvailable == null) {
            if (!PlayServicesUtils.isGooglePlayServicesDependencyAvailable()) {
                isProviderInstallerDependencyAvailable = false;
            } else {
                try {
                    Class.forName("com.google.android.gms.security.ProviderInstaller");
                    isProviderInstallerDependencyAvailable = true;
                } catch (ClassNotFoundException e) {
                    isProviderInstallerDependencyAvailable = false;
                }
            }
        }

        return isProviderInstallerDependencyAvailable;
    }

    /**
     * Tries to install the provider.
     *
     * @param context The context.
     * @return The result.
     */
    @WorkerThread
    @Result
    public static int installSecurityProvider(@NonNull Context context) {
        if (!isProviderInstallerDependencyAvailable()) {
            return PROVIDER_ERROR;
        }

        return ProviderInstallerWrapper.installIfNeeded(context);
    }

}
