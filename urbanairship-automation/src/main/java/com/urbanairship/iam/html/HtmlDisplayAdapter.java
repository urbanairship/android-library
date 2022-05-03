/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.ForegroundDisplayAdapter;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.Network;

import androidx.annotation.NonNull;

/**
 * Html display adapter.
 */
public class HtmlDisplayAdapter extends ForegroundDisplayAdapter {

    private final InAppMessage message;
    private final HtmlDisplayContent displayContent;

    /**
     * Default constructor.
     *
     * @param message The HTML in-app message.
     */
    protected HtmlDisplayAdapter(@NonNull InAppMessage message, @NonNull HtmlDisplayContent displayContent) {
        this.message = message;
        this.displayContent = displayContent;
    }

    /**
     * Creates a new modal adapter.
     *
     * @param message The in-app message.
     * @return The modal adapter.
     */
    @NonNull
    public static HtmlDisplayAdapter newAdapter(@NonNull InAppMessage message) {
        HtmlDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new HtmlDisplayAdapter(message, displayContent);
    }

    @PrepareResult
    @Override
    public int onPrepare(@NonNull Context context, @NonNull Assets assets) {
        if (!UAirship.shared().getUrlAllowList().isAllowed(displayContent.getUrl(), UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("HTML in-app message URL is not allowed. Unable to display message.");
            return InAppMessageAdapter.CANCEL;
        }

        return InAppMessageAdapter.OK;
    }

    @Override
    public boolean isReady(@NonNull Context context) {
        if (!super.isReady(context)) {
            return false;
        }

        return !displayContent.getRequireConnectivity() || Network.shared().isConnected(context);
    }

    @Override
    public void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler) {
        Intent intent = new Intent(context, HtmlActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HtmlActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(HtmlActivity.IN_APP_MESSAGE_KEY, message);

        context.startActivity(intent);
    }

    @Override
    public void onFinish(@NonNull Context context) {}

}
