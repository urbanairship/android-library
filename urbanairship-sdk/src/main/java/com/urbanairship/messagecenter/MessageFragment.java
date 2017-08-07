/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;

import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.widget.UAWebView;
import com.urbanairship.widget.UAWebViewClient;

/**
 * Fragment that displays a {@link RichPushMessage}.
 */
public class MessageFragment extends Fragment {

    private static final String MESSAGE_ID_KEY = "com.urbanairship.richpush.URL_KEY";
    private UAWebView webView;
    private View progressBar;
    private RichPushMessage message;
    private View errorPage;

    private Integer error = null;
    private Cancelable fetchMessageRequest;

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


    /**
     * Subclasses can override to replace with their own layout.  If doing so, the
     * returned view hierarchy <em>must</em> have a UAWebView whose id
     * is {@code android.R.id.message}, a progress view whose id is {@code android.R.id.progress},
     * and can optionally error page with a view id {@code R.id.error}.
     *
     * @param inflater The LayoutInflater object that can be used to inflate
     * any views in the fragment,
     * @param container If non-null, this is the parent view that the fragment's
     * UI should be attached to.  The fragment should not add the view itself,
     * but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     * @return Return the View for the fragment's UI, or null.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_message, container, false);
        ensureView(view);
        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureView(view);
    }

    /**
     * Ensures that the view contains a web view, progress view, and a error page.
     *
     * @param view The content view.
     */
    private void ensureView(View view) {
        if (webView != null) {
            return;
        }

        progressBar = view.findViewById(android.R.id.progress);
        if (progressBar == null) {
            throw new RuntimeException("Your content must have a progress View whose id attribute is 'android.R.id.progress'");
        }

        webView = (UAWebView) view.findViewById(android.R.id.message);
        if (webView == null) {
            throw new RuntimeException("Your content must have a UAWebView whose id attribute is 'android.R.id.message'");
        }

        errorPage = view.findViewById(R.id.error);

        webView.setAlpha(0);

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
                } else if (message != null) {
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

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {

                // Re-enable hardware rending if we detect a video in the message
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }

                return super.getDefaultVideoPoster();
            }
        });

        // Workaround render issue with older android devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        Button retryButton = (Button) view.findViewById(R.id.retry_button);
        if (retryButton != null) {
            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    retry();
                }
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        loadMessage();
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

    @Override
    public void onStop() {
        super.onStop();
        if (fetchMessageRequest != null) {
            fetchMessageRequest.cancel();
            fetchMessageRequest = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.webView = null;
        this.progressBar = null;
    }

    /**
     * Retries loading the message.
     */
    protected void retry() {
        if (webView == null) {
            return;
        }
        loadMessage();
    }

    /**
     * Shows the progress bar
     */
    protected void showProgress() {
        if (errorPage != null && errorPage.getVisibility() == View.VISIBLE) {
            errorPage.animate()
                     .alpha(0f)
                     .setDuration(200)
                     .setListener(null);
        }

        if (webView != null) {
            webView.animate()
                   .alpha(0f)
                   .setDuration(200)
                   .setListener(null);
        }

        if (progressBar != null) {
            progressBar.animate()
                       .alpha(1f)
                       .setDuration(200)
                       .setListener(null);
        }
    }

    /**
     * Shows the message.
     */
    protected void showMessage() {
        if (webView != null) {
            webView.animate()
                   .alpha(1f)
                   .setDuration(200)
                   .setListener(null);
        }

        if (progressBar != null) {
            progressBar.animate()
                       .alpha(0f)
                       .setDuration(200)
                       .setListener(null);
        }
    }

    /**
     * Shows the error page.
     */
    protected void showErrorPage() {
        if (errorPage != null) {
            if (errorPage.getVisibility() == View.GONE) {
                errorPage.setAlpha(0);
                errorPage.setVisibility(View.VISIBLE);
            }

            errorPage.animate()
                     .alpha(1f)
                     .setDuration(200)
                     .setListener(null);
        }

        if (progressBar != null) {
            progressBar.animate()
                       .alpha(0f)
                       .setDuration(200)
                       .setListener(null);
        }
    }

    /**
     * Returns the fragment's {@link RichPushMessage} ID.
     *
     * @return The {@link RichPushMessage} ID.
     */
    public String getMessageId() {
        return getArguments().getString(MESSAGE_ID_KEY);
    }

    private void loadMessage() {
        showProgress();
        error = null;

        message = UAirship.shared().getInbox().getMessage(getMessageId());

        if (message == null) {
            Logger.info("MessageFragment - Fetching messages.");
            fetchMessageRequest = UAirship.shared().getInbox().fetchMessages(new RichPushInbox.FetchMessagesCallback() {
                @Override
                public void onFinished(boolean success) {
                    message = UAirship.shared().getInbox().getMessage(getMessageId());

                    if (message == null) {
                        showErrorPage();
                    } else {
                        Logger.info("Loading message: " + message.getMessageId());
                        webView.loadRichPushMessage(message);
                    }
                }
            });
        } else {
            Logger.info("Loading message: " + message.getMessageId());
            webView.loadRichPushMessage(message);
        }
    }
}
