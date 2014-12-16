/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.richpush.RichPushUser;

import org.apache.http.Header;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.BasicScheme;

import java.util.HashMap;

/**
 * A web view configured to display a Rich Push Message.
 * <p/>
 * Only available in API 5 and higher (Eclair)
 */
@TargetApi(5)
public class RichPushMessageWebView extends UAWebView {

    private RichPushMessage currentMessage;

    /**
     * Construct a new RichPushMessageWebView
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource id.
     */
    public RichPushMessageWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Construct a new RichPushMessageWebView
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public RichPushMessageWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Construct a new RichPushMessageWebView
     *
     * @param context A Context object used to access application assets.
     */
    public RichPushMessageWebView(Context context) {
        super(context);
    }

    /**
     * Loads the web view with the rich push message.
     *
     * @param message The RichPushMessage that will be displayed.
     */
    @SuppressLint("NewApi")
    public void loadRichPushMessage(RichPushMessage message) {
        if (message == null) {
            Logger.warn("Unable to load null message into RichPushMessageWebView");
            return;
        }

        currentMessage = message;

        RichPushUser user = UAirship.shared().getRichPushManager().getRichPushUser();

        // Send authorization in the headers if the web view supports it
        if (Build.VERSION.SDK_INT >= 8) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user.getId(), user.getPassword());
            Header credentialHeader = BasicScheme.authenticate(credentials, "UTF-8", false);

            HashMap<String, String> headers = new HashMap<>();
            headers.put(credentialHeader.getName(), credentialHeader.getValue());

            loadUrl(message.getMessageBodyUrl(), headers);
        } else {
            loadUrl(message.getMessageBodyUrl());
        }

        // Set the auth
        setClientAuthRequest(message.getMessageBodyUrl(), user.getId(), user.getPassword());
    }

    /**
     * The current loaded RichPushMessage.
     *
     * @return The current RichPushMessage that was loaded.
     */
    public RichPushMessage getCurrentMessage() {
        return currentMessage;
    }
}
