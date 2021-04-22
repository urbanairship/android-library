/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.messagecenter.webkit.MessageWebView;
import com.urbanairship.messagecenter.webkit.MessageWebViewClient;
import com.urbanairship.webkit.AirshipWebChromeClient;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Fragment that displays a {@link Message}.
 */
public class MessageFragment extends Fragment {

    /**
     * Argument key to specify the message Reporting
     */
    public static final String MESSAGE_ID = "messageReporting";

    @IntDef({ ERROR_DISPLAYING_MESSAGE, ERROR_FETCHING_MESSAGES, ERROR_MESSAGE_UNAVAILABLE })
    @Retention(RetentionPolicy.SOURCE)
    @interface Error {}

    /**
     * Unable to fetch messages.
     */
    protected static final int ERROR_FETCHING_MESSAGES = 1;

    /**
     * Error displaying the message.
     */
    protected static final int ERROR_DISPLAYING_MESSAGE = 2;

    /**
     * Message has been deleted or expired.
     */
    protected static final int ERROR_MESSAGE_UNAVAILABLE = 3;

    private MessageWebView webView;
    private View progressBar;
    private Message message;
    private View errorPage;
    private Button retryButton;
    private TextView errorMessage;

    private Integer error = null;
    private Cancelable fetchMessageRequest;

    /**
     * Creates a new MessageFragment
     *
     * @param messageId The message's ID to display
     * @return messageFragment new MessageFragment
     */
    @NonNull
    public static MessageFragment newInstance(@Nullable String messageId) {
        MessageFragment message = new MessageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(MESSAGE_ID, messageId);
        message.setArguments(arguments);
        return message;
    }

    /**
     * Subclasses can override to replace with their own layout.  If doing so, the
     * returned view hierarchy <em>must</em> have a MessageWebView whose id
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
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ua_fragment_message, container, false);
        ensureView(view);
        return view;
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureView(view);
    }

    /**
     * Ensures that the view contains a web view, progress view, and a error page.
     *
     * @param view The content view.
     */
    private void ensureView(@NonNull View view) {
        if (webView != null) {
            return;
        }

        progressBar = view.findViewById(android.R.id.progress);
        if (progressBar == null) {
            throw new RuntimeException("Your content must have a progress View whose id attribute is 'android.R.id.progress'");
        }

        webView = view.findViewById(android.R.id.message);
        if (webView == null) {
            throw new RuntimeException("Your content must have a MessageWebView whose id attribute is 'android.R.id.message'");
        }

        errorPage = view.findViewById(R.id.error);

        webView.setAlpha(0);

        // Set a custom RichPushWebViewClient view client to listen for the page finish
        // Note: MessageWebViewClient is required to load the proper auth and to
        // inject the Airship Javascript interface.  When overriding any methods
        // make sure to call through to the super's implementation.
        webView.setWebViewClient(new MessageWebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (error != null) {
                    showErrorPage(ERROR_DISPLAYING_MESSAGE);
                } else if (message != null) {
                    message.markRead();
                    showMessage();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, @Nullable String failingUrl) {
                if (message != null && failingUrl != null && failingUrl.equals(message.getMessageBodyUrl())) {
                    error = errorCode;
                }
            }
        });

        webView.getSettings().setSupportMultipleWindows(true);
        webView.setWebChromeClient(new AirshipWebChromeClient(getActivity()));

        retryButton = view.findViewById(R.id.retry_button);
        if (retryButton != null) {
            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    retry();
                }
            });
        }

        errorMessage = view.findViewById(R.id.error_message);
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
     *
     * @param error The error.
     */
    protected void showErrorPage(@Error int error) {
        if (errorPage != null) {

            switch (error) {
                case ERROR_MESSAGE_UNAVAILABLE:
                    if (retryButton != null) {
                        retryButton.setVisibility(View.GONE);
                    }

                    if (errorMessage != null) {
                        errorMessage.setText(R.string.ua_mc_no_longer_available);
                    }

                    break;

                case ERROR_FETCHING_MESSAGES:
                case ERROR_DISPLAYING_MESSAGE:
                    if (retryButton != null) {
                        retryButton.setVisibility(View.VISIBLE);
                    }

                    if (errorMessage != null) {
                        errorMessage.setText(R.string.ua_mc_failed_to_load);
                    }

                    break;
            }

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
     * Returns the fragment's {@link Message} ID.
     *
     * @return The {@link Message} ID.
     */
    @Nullable
    public String getMessageId() {
        if (getArguments() == null) {
            return null;
        }
        return getArguments().getString(MESSAGE_ID);
    }

    private void loadMessage() {
        showProgress();
        error = null;

        message = MessageCenter.shared().getInbox().getMessage(getMessageId());

        if (message == null) {
            Logger.debug("Fetching messages.");
            fetchMessageRequest = MessageCenter.shared().getInbox().fetchMessages(new Inbox.FetchMessagesCallback() {
                @Override
                public void onFinished(boolean success) {
                    message = MessageCenter.shared().getInbox().getMessage(getMessageId());

                    if (!success) {
                        showErrorPage(ERROR_FETCHING_MESSAGES);
                        return;
                    } else if (message == null || message.isExpired()) {
                        showErrorPage(ERROR_MESSAGE_UNAVAILABLE);
                        return;
                    }

                    Logger.info("Loading message: " + message.getMessageId());
                    webView.loadMessage(message);
                }
            });
        } else {
            if (message.isExpired()) {
                showErrorPage(ERROR_MESSAGE_UNAVAILABLE);
                return;
            }

            Logger.info("Loading message: %s", message.getMessageId());
            webView.loadMessage(message);
        }
    }

}
