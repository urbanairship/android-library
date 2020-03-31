package com.urbanairship.messagecenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.richpush.RichPushUser;
import com.urbanairship.webkit.AirshipWebView;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A web view that sets settings appropriate for Airship message center content.
 */
public class MessageWebView extends AirshipWebView {

    private RichPushMessage currentMessage;

    /**
     * MessageWebView Constructor
     *
     * @param context A Context object used to access application assets.
     */
    public MessageWebView(@NonNull Context context) {
        super(context);
    }

    /**
     * MessageWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public MessageWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * MessageWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public MessageWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * MessageWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    public MessageWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
    }

    /**
     * Loads the web view with the rich push message.
     *
     * @param message The RichPushMessage that will be displayed.
     */
    @SuppressLint("NewApi")
    public void loadRichPushMessage(@Nullable RichPushMessage message) {
        if (message == null) {
            Logger.error("Unable to load null message into MessageWebView");
            return;
        }

        RichPushUser user = UAirship.shared().getInbox().getUser();

        // Send authorization in the headers if the web view supports it
        HashMap<String, String> headers = new HashMap<>();

        // Set the auth
        if (user.getId() != null && user.getPassword() != null) {
            setClientAuthRequest(message.getMessageBodyUrl(), user.getId(), user.getPassword());
            headers.put("Authorization", createBasicAuth(user.getId(), user.getPassword()));
        }

        loadUrl(message.getMessageBodyUrl(), headers);

        // loadUrl clears currentMessage, so set it after load starts
        currentMessage = message;
    }

    /**
     * The current loaded RichPushMessage.
     *
     * @return The current RichPushMessage that was loaded.
     */
    @Nullable
    public RichPushMessage getCurrentMessage() {
        return currentMessage;
    }

    /**
     * Called right before data or a URL is passed to the web view to be loaded.
     */
    @SuppressLint("NewApi")
    protected void onPreLoad() {
        currentMessage = null;

        super.onPreLoad();
    }


}
