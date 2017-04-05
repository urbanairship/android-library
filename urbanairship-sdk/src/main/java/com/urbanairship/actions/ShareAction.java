/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.ResolveInfo;
import android.support.annotation.NonNull;

import com.urbanairship.R;
import com.urbanairship.UAirship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shows a chooser activity to share text.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument values: A String used as the share text.
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Names: ^s, share_action
 */
public class ShareAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "share_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^s";

    private static final List<String> ignoredPackages = new ArrayList<String>() {{
        add("com.android.bluetooth");
        add("com.android.nfc");
        add("com.google.android.apps.docs");
    }};

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                return arguments.getValue().getString() != null;

            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        final Context context = UAirship.getApplicationContext();

        Intent sharingIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, arguments.getValue().getString());


        List<ResolveInfo> shareResolveInfos = new ArrayList<>();

        for (ResolveInfo resolveInfo : UAirship.getPackageManager().queryIntentActivities(sharingIntent, 0)) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }

            String packageName = resolveInfo.activityInfo.packageName;

            if (!excludePackage(packageName)) {
                shareResolveInfos.add(resolveInfo);
            }
        }

        // Sort the share entries by display name
        Collections.sort(shareResolveInfos, new ResolveInfo.DisplayNameComparator(UAirship.getPackageManager()));

        List<Intent> intents = new ArrayList<>();
        for (ResolveInfo resolveInfo : shareResolveInfos) {
            String packageName = resolveInfo.resolvePackageName == null ? resolveInfo.activityInfo.packageName : resolveInfo.resolvePackageName;
            Intent intent = new LabeledIntent(sharingIntent, packageName, resolveInfo.labelRes, resolveInfo.icon)
                    .setPackage(packageName)
                    .setClassName(packageName, resolveInfo.activityInfo.name);

            intents.add(intent);
        }

        final Intent chooserIntent;
        if (shareResolveInfos.isEmpty()) {
            // Show any empty chooser by setting the target intent's package to an empty string
            chooserIntent = Intent.createChooser(sharingIntent.setPackage(""), context.getString(R.string.ua_share_dialog_title))
                                  .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            chooserIntent = Intent.createChooser(intents.remove(intents.size() - 1), context.getString(R.string.ua_share_dialog_title))
                                  .putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Intent[intents.size()]))
                                  .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(chooserIntent);

        return ActionResult.newEmptyResult();
    }

    /**
     * Used to filter out the list of packages in the chooser dialog.
     *
     * @param packageName The package name.
     * @return <code>true</code> to exclude the package from the chooser dialog, <code>false</code> to include the package.
     */
    protected boolean excludePackage(String packageName) {
        return ignoredPackages.contains(packageName);
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }
}
