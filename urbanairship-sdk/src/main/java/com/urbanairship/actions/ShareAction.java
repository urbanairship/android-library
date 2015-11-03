/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.urbanairship.R;
import com.urbanairship.UAirship;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a chooser activity to share text.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
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
                return arguments.getValue().getString() != null;
            default:
                return false;
        }
    }

    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        final Context context = UAirship.getApplicationContext();

        Intent sharingIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, arguments.getValue().getString());


        List<Intent> intentList = new ArrayList<>();
        List<ResolveInfo> resolveInfoList = UAirship.getPackageManager().queryIntentActivities(sharingIntent, 0);

        // Used to prevent multiple entries of the same package from showing in the list
        List<String> packages = new ArrayList<>();

        for (int j = 0; j < resolveInfoList.size(); j++) {
            ResolveInfo resolveInfo = resolveInfoList.get(j);
            String packageName = resolveInfo.activityInfo.packageName;

            if (!excludePackage(packageName) && !packages.contains(packageName)) {
                packages.add(packageName);

                Intent intent = new Intent(sharingIntent);
                intent.setPackage(packageName);
                intentList.add(intent);
            }
        }

        final Intent chooserIntent;

        if (intentList.isEmpty()) {
            // Show any empty chooser by setting the target intent's package to an empty string
            chooserIntent = Intent.createChooser(sharingIntent.setPackage(""), context.getString(R.string.ua_share_dialog_title))
                                  .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            chooserIntent = Intent.createChooser(intentList.remove(0), context.getString(R.string.ua_share_dialog_title))
                                  .putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toArray(new Intent[intentList.size()]))
                                  .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                context.startActivity(chooserIntent);
            }
        });

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
}
