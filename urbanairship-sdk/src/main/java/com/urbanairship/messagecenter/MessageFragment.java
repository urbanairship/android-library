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

package com.urbanairship.messagecenter;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.widget.UAWebView;
import com.urbanairship.widget.UAWebViewClient;

/**
 * Fragment that displays a {@link RichPushMessage}.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MessageFragment extends Fragment {

    private static final String MESSAGE_ID_KEY = "com.urbanairship.richpush.URL_KEY";
    private UAWebView webView;
    private ProgressBar progressBar;
    private RichPushMessage message;
    private View errorPage;

    private Integer error = null;

    /**
     * Creates a new MessageFragment
     *
     * @param messageId The message's ID to display
     * @return messageFragment new MessageFragment
     */
    public static MessageFragment newInstance(String messageId) {
        MessageFragment message = new MessageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(MESSAGE_ID_KEY, messageId);
        message.setArguments(arguments);
        return message;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String messageId = getMessageId();
        message = UAirship.shared().getInbox().getMessage(messageId);

        if (message == null) {
            Logger.info("Couldn't retrieve message for ID: " + messageId);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_message, container, false);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        webView = (UAWebView) view.findViewById(R.id.message_view);
        webView.setAlpha(0);

        // Workaround render issue with older android devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        errorPage = view.findViewById(R.id.error_page);

        Button retryButton = (Button) view.findViewById(R.id.retry_button);
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProgress();
                error = null;
                webView.loadRichPushMessage(message);
            }
        });

        // Set a custom RichPushWebViewClient view client to listen for the page finish
        // Note: UAWebViewClient is required to load the proper auth and to
        // inject the Urban Airship Javascript interface.  When overriding any methods
        // make sure to call through to the super's implementation.
        webView.setWebViewClient(new UAWebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (error != null) {
                    showErrorPage();
                } else {
                    message.markRead();
                    showMessage();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (message != null && failingUrl != null && failingUrl.equals(message.getMessageBodyUrl())) {
                    error = errorCode;
                }
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (message != null) {
            showProgress();
            Logger.info("Loading message: " + message.getMessageId());
            webView.loadRichPushMessage(message);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        webView.onPause();
    }

    /**
     * Shows the progress bar
     */
    private void showProgress() {
        if (errorPage.getVisibility() == View.VISIBLE) {
            errorPage.animate()
                     .alpha(0f)
                     .setDuration(200)
                     .setListener(null);
        }

        webView.animate()
               .alpha(0f)
               .setDuration(200)
               .setListener(null);

        progressBar.animate()
                   .alpha(1f)
                   .setDuration(200)
                   .setListener(null);
    }
    /**
     * Shows the message.
     */
    private void showMessage() {
        webView.animate()
               .alpha(1f)
               .setDuration(200)
               .setListener(null);

        progressBar.animate()
                   .alpha(0f)
                   .setDuration(200)
                   .setListener(null);
    }

    /**
     * Shows the error page.
     */
    private void showErrorPage() {
        if (errorPage.getVisibility() == View.GONE) {
            errorPage.setAlpha(0);
            errorPage.setVisibility(View.VISIBLE);
        }

        errorPage.animate()
               .alpha(1f)
               .setDuration(200)
               .setListener(null);

        progressBar.animate()
                   .alpha(0f)
                   .setDuration(200)
                   .setListener(null);
    }

    /**
     * Returns the fragment's {@link RichPushMessage} ID.
     *
     * @return The {@link RichPushMessage} ID.
     */
    public String getMessageId() {
        return getArguments().getString(MESSAGE_ID_KEY);
    }
}
