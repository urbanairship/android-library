package com.urbanairship.messagecenter.webkit;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.messagecenter.Message;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.messagecenter.User;
import com.urbanairship.webkit.AirshipWebView;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A web view that sets settings appropriate for Airship message center content.
 */
public class MessageWebView extends AirshipWebView {

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
     * Loads the web view with the {@link Message}.
     *
     * @param message The message that will be displayed.
     */
    public void loadMessage(@NonNull Message message) {
        User user = MessageCenter.shared().getUser();

        // Send authorization in the headers if the web view supports it
        HashMap<String, String> headers = new HashMap<>();

        // Set the auth
        if (user.getId() != null && user.getPassword() != null) {
            setClientAuthRequest(message.getMessageBodyUrl(), user.getId(), user.getPassword());
            headers.put("Authorization", createBasicAuth(user.getId(), user.getPassword()));
        }

        loadUrl(message.getMessageBodyUrl(), headers);
    }
}
