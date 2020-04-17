/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;
import android.text.TextUtils;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Accengage push button.
 */
public class AccengagePushButton {

    private static final String KEY_BUTTON_ID = "id";
    private static final String KEY_BUTTON_URL_OLD = "url";
    private static final String KEY_BUTTON_URL = "acc_url";
    private static final String KEY_BUTTON_ACTION_OLD = "action";
    private static final String KEY_BUTTON_ACTION = "acc_action";
    private static final String KEY_BUTTON_TITLE = "title";
    private static final String KEY_BUTTON_ICON = "icon";
    private static final String KEY_BUTTON_CUSTOM_PARAMS = "ccp";
    private static final String KEY_BUTTON_OPEN_APP = "oa";

    private String id;
    private String url;
    private String title;
    private String iconName;
    private boolean openApp = true;
    private Map<String, String> customParams;
    @AccengageMessage.Action
    private String action;

    private AccengagePushButton() {
    }

    @NonNull
    static AccengagePushButton fromJson(@NonNull JsonValue jsonValue) throws JsonException {
        JsonMap jsonMap = jsonValue.optMap();
        AccengagePushButton button = new AccengagePushButton();

        button.id = parseId(jsonMap);
        button.title = jsonMap.opt(KEY_BUTTON_TITLE).getString();
        button.iconName = jsonMap.opt(KEY_BUTTON_ICON).getString();
        button.openApp = jsonMap.opt(KEY_BUTTON_OPEN_APP).getBoolean(true);
        button.action = parseAction(jsonMap);
        button.url = parseUrl(jsonMap);
        button.customParams = parseCustomParams(jsonMap);
        return button;
    }

    @NonNull
    private static Map<String, String> parseCustomParams(@NonNull JsonMap jsonMap) {
        Map<String, String> params = new HashMap<>();

        for (Map.Entry<String, JsonValue> entry : jsonMap.opt(KEY_BUTTON_CUSTOM_PARAMS).optMap()) {
            params.put(entry.getKey(), entry.getValue().getString(""));
        }

        return params;
    }

    @Nullable
    private static String parseUrl(@NonNull JsonMap jsonMap) {
        String url = null;

        if (jsonMap.containsKey(KEY_BUTTON_URL)) {
            url = jsonMap.opt(KEY_BUTTON_URL).getString();
        } else if (jsonMap.containsKey(KEY_BUTTON_URL_OLD)) {
            url = jsonMap.opt(KEY_BUTTON_URL_OLD).getString();
        }

        return url;
    }

    @NonNull
    private static String parseId(@NonNull JsonMap jsonMap) {
        String id = jsonMap.opt(KEY_BUTTON_ID).getString();
        if (id == null) {
            id = String.valueOf(jsonMap.opt(KEY_BUTTON_ID).getInt(0));
        }

        return id;
    }

    @AccengageMessage.Action
    @NonNull
    private static String parseAction(@NonNull JsonMap jsonMap) {
        String action = null;
        if (jsonMap.containsKey(KEY_BUTTON_ACTION)) {
            action = jsonMap.opt(KEY_BUTTON_ACTION).getString();
        } else if (jsonMap.containsKey(KEY_BUTTON_ACTION_OLD)) {
            action = jsonMap.opt(KEY_BUTTON_ACTION_OLD).getString();
        }

        switch (action == null ? "" : action) {
            case AccengageMessage.ACTION_OPEN_URL:
                return AccengageMessage.ACTION_OPEN_URL;
            case AccengageMessage.ACTION_TRACK_URL:
                return AccengageMessage.ACTION_TRACK_URL;
            case AccengageMessage.ACTION_SHOW_WEBVIEW:
            default:
                return AccengageMessage.ACTION_SHOW_WEBVIEW;
        }
    }

    /**
     * Returns the button's Id.
     *
     * @return The button's Id.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Returns the button's title.
     *
     * @return The button's title, or null if not set.
     */
    @Nullable
    public String getTitle() {
        return title;
    }

    /**
     * Returns the button's icon name.
     *
     * @return The button's icon name, or null if not set.
     */
    @Nullable
    public String getIconName() {
        return iconName;
    }

    /**
     * Returns the button's icon resource Id.
     *
     * @return The button's resource Id, or 0 if not set.
     */
    @DrawableRes
    public int getIcon(@NonNull Context context) {
        if (TextUtils.isEmpty(iconName)) {
            return 0;
        }
        return context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
    }

    /**
     * Returns the button's custom params.
     *
     * @return The button's custom params.
     */
    @NonNull
    public Map<String, String> getCustomParams() {
        return customParams;
    }

    /**
     * Checks if the button should launch the app or not.
     *
     * @return {@code true} if the button should launch the app, otherwise {@code false}.
     */
    public boolean getOpenApp() {
        return openApp;
    }

    /**
     * Gets the Accengage URL.
     *
     * @return The Accengage URL, or null if not set.
     */
    @Nullable
    public String getAccengageUrl() {
        return url;
    }

    /**
     * Gets the Accengage action.
     *
     * @return The Accengage action, or null if not set.
     */
    @NonNull
    @AccengageMessage.Action
    public String getAccengageAction() {
        return action;
    }
}
