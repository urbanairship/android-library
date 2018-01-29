/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.urbanairship.UAirship;

/**
 * An activity that creates and launches a task stack from a deep link uri.
 * <p/>
 * The activity will operate on any URI that it is started with, only parsing
 * the URI's path and query parameters. URI filtering should be defined in the
 * AndroidManifest.xml for the ParseDeepLinkActivity entry by defining an intent
 * filter.
 * <p/>
 * Handles URLs of the following syntax:
 * <url> := vnd.urbanairship.sample://deeplink/<deep-link>
 * <deep-link> := home | preferences | inbox | inbox?<message_id>
 * <message-id> := A rich push message ID
 * <p/>
 * Examples:
 * <p/>
 * // Deep link to inbox
 * vnd.urbanairship.sample://deeplink/inbox
 * <p/>
 * // Deep link to home
 * vnd.urbanairship.sample://deeplink/home
 * <p/>
 * // Deep link to preferences
 * vnd.urbanairship.sample://deeplink/preferences
 * <p/>
 * // Deep link to the message 'VJO7DpCEQ2i7LdqOxLbFxw'
 * vnd.urbanairship.sample://deeplink/inbox?message_id=VJO7DpCEQ2i7LdqOxLbFxw
 */
public class ParseDeepLinkActivity extends Activity {

    private static final String TAG = "ParseDeepLinkActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Parse the deep link
        String deepLink = getDeepLink();

        // If deep link is null start the MainActivity
        if (deepLink == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        switch (deepLink) {
            case "/preferences":
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case "/home":
                startActivity(new Intent(this, MainActivity.class));
                break;

            case "/inbox":
                // Check for an optional Message ID
                String messageId = getDeepLinkQueryParameter("message_id");
                if (messageId != null && messageId.length() > 0) {
                    UAirship.shared().getInbox().startMessageActivity(messageId);
                } else {
                    UAirship.shared().getInbox().startInboxActivity();
                }
                break;

            default:
                Log.e(TAG, "Unknown deep link: " + deepLink + ". Falling back to main activity.");
                startActivity(new Intent(this, MainActivity.class));
                break;
        }

        finish();
    }

    @Nullable
    private String getDeepLink() {
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            return intent.getData().getPath();
        }

        return null;
    }

    private String getDeepLinkQueryParameter(String key) {
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            return intent.getData().getQueryParameter(key);
        }

        return null;
    }
}
