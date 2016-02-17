/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.util;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.CoreActivity;
import com.urbanairship.CoreReceiver;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.UrbanAirshipProvider;
import com.urbanairship.actions.ActionActivity;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.LandingPageAction;
import com.urbanairship.analytics.EventService;
import com.urbanairship.location.LocationService;
import com.urbanairship.push.BaseIntentReceiver;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushService;
import com.urbanairship.richpush.RichPushUpdateService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for validating the AndroidManifest.xml file.
 */
public class ManifestUtils {

    /**
     * Intent actions to validate for any app push receivers.
     */
    private static final String[] BASE_INTENT_RECEIVER_ACTIONS = new String[] { PushManager.ACTION_PUSH_RECEIVED,
                                                                                PushManager.ACTION_NOTIFICATION_OPENED,
                                                                                PushManager.ACTION_CHANNEL_UPDATED,
                                                                                PushManager.ACTION_NOTIFICATION_DISMISSED };

    /**
     * Logs an error if the specified permission is not granted
     * for the application.
     *
     * @param permission Permission to check
     */
    public static void checkRequiredPermission(@NonNull String permission) {
        if (PackageManager.PERMISSION_DENIED == UAirship.getPackageManager()
                                                        .checkPermission(permission, UAirship.getPackageName())) {

            Logger.error("AndroidManifest.xml missing required permission: "
                    + permission);
        }
    }

    /**
     * Returns whether the specified permission is granted for the application or not.
     *
     * @param permission Permission to check.
     * @return <code>true</code> if the permission is granted, otherwise <code>false</code>.
     */
    public static boolean isPermissionGranted(@NonNull String permission) {
        return PackageManager.PERMISSION_GRANTED == UAirship.getPackageManager()
                                                         .checkPermission(permission, UAirship.getPackageName());
    }

    /**
     * Gets the ComponentInfo for a service
     *
     * @param service The service to look up
     * @return The service's ComponentInfo, or null if the service
     * is not listed in the manifest
     */
    public static ComponentInfo getServiceInfo(@NonNull Class service) {
        ComponentName componentName = new ComponentName(UAirship.getPackageName(),
                service.getCanonicalName());
        try {
            return UAirship.getPackageManager().getServiceInfo(componentName,
                    PackageManager.GET_META_DATA);

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Gets the ComponentInfo for an activity
     *
     * @param activity The activity to look up
     * @return The activity's ComponentInfo, or null if the activity
     * is not listed in the manifest
     */
    public static ActivityInfo getActivityInfo(@NonNull Class activity) {
        ComponentName componentName = new ComponentName(UAirship.getPackageName(),
                activity.getCanonicalName());
        try {
            return UAirship.getPackageManager().getActivityInfo(componentName,
                    PackageManager.GET_META_DATA);

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Gets the ComponentInfo for a receiver
     *
     * @param receiver The receiver to look up
     * @return The receiver's ComponentInfo, or null if the receiver
     * is not listed in the manifest
     */
    public static ComponentInfo getReceiverInfo(@NonNull Class receiver) {
        ComponentName componentName = new ComponentName(UAirship.getPackageName(),
                receiver.getCanonicalName());
        try {
            return UAirship.getPackageManager().getReceiverInfo(componentName,
                    PackageManager.GET_META_DATA);

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Gets the ComponentInfo for a provider
     *
     * @param authorityString The authorityString for the provider
     * @return The provider's ComponentInfo, or null if the provider
     * is not listed in the manifest
     */
    public static ComponentInfo getProviderInfo(@NonNull String authorityString) {
        return UAirship.getPackageManager().resolveContentProvider(authorityString, 0);
    }


    /**
     * Determine whether the specified permission is known to the system
     *
     * @param permission the permission name to check (e.g. com.google.android.c2dm.permission.RECEIVE)
     * @return <code>true</code>if known, <code>false</code> otherwise
     */
    public static boolean isPermissionKnown(@NonNull String permission) {
        try {
            UAirship.getPackageManager().getPermissionInfo(permission, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }


    /**
     * Validates the manifest for Urban Airship components.
     */
    public static void validateManifest(@NonNull AirshipConfigOptions airshipConfigOptions) {
        ManifestUtils.checkRequiredPermission(Manifest.permission.INTERNET);
        ManifestUtils.checkRequiredPermission(Manifest.permission.ACCESS_NETWORK_STATE);

        if (ManifestUtils.isPermissionKnown(UAirship.getUrbanAirshipPermission())) {
            ManifestUtils.checkRequiredPermission(UAirship.getUrbanAirshipPermission());
        } else {
            Logger.error("AndroidManifest.xml does not define and require permission: " + UAirship.getUrbanAirshipPermission());
        }

        Map<Class, ComponentInfo> componentInfoMap = getUrbanAirshipComponentInfoMap();

        // Core Receiver
        if (componentInfoMap.get(CoreReceiver.class) == null) {
            Logger.error("AndroidManifest.xml missing required receiver: " + CoreReceiver.class.getCanonicalName());
        } else {

            ComponentInfo coreInfo = componentInfoMap.get(CoreReceiver.class);

            Intent openIntent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                    .addCategory(UAirship.getPackageName());

            ResolveInfo coreResolveInfo = null;
            for (ResolveInfo info : UAirship.getPackageManager().queryBroadcastReceivers(openIntent, 0)) {
                if (info.activityInfo != null && info.activityInfo.name != null && info.activityInfo.name.equals(coreInfo.name)) {
                    coreResolveInfo = info;
                }
            }

            if (coreResolveInfo == null) {
                Logger.error("AndroidManifest.xml's " + CoreReceiver.class.getCanonicalName() +
                        " declaration missing required intent-filter: <intent-filter android:priority=\"-999\">" +
                        "<action android:name=\"" + PushManager.ACTION_NOTIFICATION_OPENED + "\"/>" +
                        "<category android:name=\"" + UAirship.getPackageName() + "\"/></intent-filter>");
            } else if (coreResolveInfo.priority != -999) {
                Logger.error("CoreReceiver's intent filter priority should be set to -999 in order to " +
                        "let the application launch any activities before Urban Airship performs any actions " +
                        "or falls back to launching the application launch intent.");
            }
        }

        // Validate any app push receivers
        ActivityInfo[] receivers = null;
        try {
            receivers = UAirship.getPackageManager().getPackageInfo(UAirship.getPackageName(), PackageManager.GET_RECEIVERS).receivers;
        } catch (Exception e) {
            Logger.error("Unable to query the application's receivers.", e);
        }

        if (receivers != null) {
            for (ActivityInfo info : receivers) {
                try {
                    Class receiverClass = Class.forName(info.name);
                    if (BaseIntentReceiver.class.isAssignableFrom(receiverClass)) {
                        validateBaseIntentReceiver(info);
                    }
                } catch (ClassNotFoundException e) {
                    Logger.debug("ManifestUtils - Unable to find class: " + info.name, e);
                }
            }
        }

        // Core Activity
        if (componentInfoMap.get(CoreActivity.class) == null) {
            Logger.error("AndroidManifest.xml missing required activity: " + CoreActivity.class.getCanonicalName());
        }

        // Event Service check
        if (airshipConfigOptions.analyticsEnabled && componentInfoMap.get(EventService.class) == null) {
            Logger.error("AndroidManifest.xml missing required service: " + EventService.class.getCanonicalName());
        }

        // Check Push
        if (componentInfoMap.get(PushService.class) == null) {
            Logger.error("AndroidManifest.xml missing required service: " + PushService.class.getCanonicalName());
        }

        // Check Rich Push
        if (componentInfoMap.get(RichPushUpdateService.class) == null) {
            Logger.error("AndroidManifest.xml missing required service: " + RichPushUpdateService.class.getCanonicalName());
        }

        // Check Actions Service
        if (componentInfoMap.get(ActionService.class) == null) {
            Logger.error("AndroidManifest.xml missing required service: " + ActionService.class.getCanonicalName());
        }

        // Start activity for result action
        if (componentInfoMap.get(ActionActivity.class) == null) {
            Logger.warn("AndroidManifest.xml missing ActionActivity.  " +
                    "Action.startActivityForResult will not work.");
        }

        // Landing page http activity
        Intent i = new Intent(LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION, Uri.parse("http://"))
                .setPackage(UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addCategory(Intent.CATEGORY_DEFAULT);

        if (UAirship.getPackageManager().resolveActivity(i, 0) == null) {
            Logger.warn("AndroidManifest.xml missing activity with an intent " +
                    "filter for action " + LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION +
                    ", category " + Intent.CATEGORY_DEFAULT + ", and data with scheme http. " +
                    " Landing page action may not function properly.");
        }

        // Landing page https activity
        i = new Intent(LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION, Uri.parse("https://"))
                .setPackage(UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addCategory(Intent.CATEGORY_DEFAULT);

        if (UAirship.getPackageManager().resolveActivity(i, 0) == null) {
            Logger.error("AndroidManifest.xml missing activity with an intent " +
                    "filter for action " + LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION +
                    ", category " + Intent.CATEGORY_DEFAULT + ", and data with scheme https." +
                    " Landing page action may not function properly.");
        }

        // Landing page message activity
        i = new Intent(LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION, Uri.parse("message://"))
                .setPackage(UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addCategory(Intent.CATEGORY_DEFAULT);

        if (UAirship.getPackageManager().resolveActivity(i, 0) == null) {
            Logger.error("AndroidManifest.xml missing activity with an intent " +
                    "filter for action " + LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION +
                    ", category " + Intent.CATEGORY_DEFAULT + ", and data with scheme message." +
                    " Landing page action may not function properly.");
        }

        String processName = UAirship.getAppInfo() == null ? UAirship.getPackageName()
                                                           : UAirship.getAppInfo().processName;

        // Check for different process names
        for (Class component : componentInfoMap.keySet()) {
            ComponentInfo info = componentInfoMap.get(component);
            if (info != null && !processName.equals(info.processName)) {
                Logger.warn("A separate process is detected for: "
                        + component.getCanonicalName() + ". In the " +
                        "AndroidManifest.xml, remove the android:process attribute.");
            }
        }

        // Provider check, absolutely required so throw an exception
        if (componentInfoMap.get(UrbanAirshipProvider.class) == null) {
            throw new IllegalStateException("Unable to resolve UrbanAirshipProvider. " +
                    "Please check that the provider is defined in your AndroidManifest.xml, " +
                    "and that the authority string is set to  \"" + UrbanAirshipProvider.getAuthorityString() + "\"");
        }

    }

    /**
     * Gets all the ComponentInfo for the various Urban Airship
     * services, providers, receivers, and activities
     *
     * @return A map of class to component info
     */
    private static Map<Class, ComponentInfo> getUrbanAirshipComponentInfoMap() {
        return new HashMap<Class, ComponentInfo>() {{
            // Services
            put(EventService.class, ManifestUtils.getServiceInfo(EventService.class));
            put(PushService.class, ManifestUtils.getServiceInfo(PushService.class));
            put(RichPushUpdateService.class, ManifestUtils.getServiceInfo(RichPushUpdateService.class));
            put(ActionService.class, ManifestUtils.getServiceInfo(ActionService.class));
            put(LocationService.class, ManifestUtils.getServiceInfo(LocationService.class));

            // Receivers
            put(CoreReceiver.class, ManifestUtils.getReceiverInfo(CoreReceiver.class));

            // Providers
            put(UrbanAirshipProvider.class, ManifestUtils.getProviderInfo(UrbanAirshipProvider.getAuthorityString()));

            // Activities
            put(ActionActivity.class, ManifestUtils.getActivityInfo(ActionActivity.class));
            put(CoreActivity.class, ManifestUtils.getActivityInfo(CoreActivity.class));
        }};
    }


    /**
     * Helper method to validate a BaseIntentReceiver's manifest entry.
     */
    private static void validateBaseIntentReceiver(@NonNull ActivityInfo info) {
        if (info.exported) {
            Logger.error("Receiver " + info.name + " is exported. This might " +
                    "allow outside applications to message the receiver. Make sure the intent is protected by a " +
                    "permission or prevent the receiver from being exported.");
        }

        List<String> missingActions = new ArrayList<>();

        for (String action : BASE_INTENT_RECEIVER_ACTIONS) {
            Intent intent = new Intent(action)
                    .addCategory(UAirship.getPackageName());

            boolean resolved = false;
            for (ResolveInfo resolveInfo : UAirship.getPackageManager().queryBroadcastReceivers(intent, 0)) {
                if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.name != null && resolveInfo.activityInfo.name.equals(info.name)) {
                    resolved = true;
                    break;
                }
            }

            if (!resolved) {
                missingActions.add(action);
            }
        }

        if (missingActions.isEmpty()) {
            return;
        }

        Logger.error("Receiver " + info.name + " unable to receive intents for actions: " + missingActions);

        StringBuilder sb = new StringBuilder();

        sb.append("Update the manifest entry for ").append(info.name).append(" to:")
               .append("\n<receiver android:name=\"").append(info.name).append("\" exported=\"false\">")
               .append("\n\t<intent-filter> ");

        for (String action : BASE_INTENT_RECEIVER_ACTIONS) {
            sb.append("\n\t\t<action android:name=\"").append(action).append("\" />");
        }

        sb.append("\n\t\t<!-- Replace ${applicationId} with ").append(UAirship.getPackageName()).append(" if not using Android Gradle plugin -->")
          .append("\n\t\t<category android:name=\"${applicationId}\" />")
          .append("\n\t</intent-filter>")
          .append("\n</receiver>");

        Logger.error(sb.toString());
    }
}
