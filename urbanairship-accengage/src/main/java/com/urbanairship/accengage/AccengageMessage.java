/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.text.TextUtils;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;
import androidx.core.app.NotificationCompat;

/**
 * Accengage push message.
 */
public class AccengageMessage {

    /**
     * Accengage actions.
     */
    @StringDef({ACTION_OPEN_URL, ACTION_SHOW_WEBVIEW, ACTION_TRACK_URL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {
    }

    /**
     * Browser action.
     */
    @NonNull
    public static final String ACTION_OPEN_URL = "browser";

    /**
     * Track URL action. Not supported in Airship.
     */
    @NonNull
    public static final String ACTION_TRACK_URL = "urlExec";

    /**
     * Web view action.
     */
    @NonNull
    public static final String ACTION_SHOW_WEBVIEW = "webView";

    /**
     * Big text notification style.
     */
    @NonNull
    public static final String ACCENGAGE_BIG_TEXT_STYLE = "BigTextStyle";

    /**
     * Big picture notification style.
     */
    @NonNull
    public static final String ACCENGAGE_BIG_PICTURE_STYLE = "BigPictureStyle";

    /**
     * Inbox notification style.
     */
    @NonNull
    public static final String ACCENGAGE_INBOX_STYLE = "InboxStyle";

    /**
     * Big text big picture expanded template.
     */
    @NonNull
    public static final String ACCENGAGE_BIG_TEXT_BIG_PICTURE_EXPANDED_TEMPLATE = "com_ad4screen_sdk_template_notification_bigpicture";


    /**
     * Big text big picture collapsed style.
     */
    @NonNull
    public static final String ACCENGAGE_BIG_TEXT_BIG_PICTURE_COLLAPSED_TEMPLATE = "com_ad4screen_sdk_template_notification_bigpicture_collapsed";


    private static final String ACCENGAGE_CONTENT_KEY = "a4scontent";
    private static final String EXTRA_A4S_CONTENT_HTML_KEY = "a4scontenthtml";
    private static final String EXTRA_A4S_TITLE_HTML = "a4stitlehtml";
    private static final String EXTRA_A4S_TITLE = "a4stitle";
    private static final String EXTRA_A4S_SYSTEM_ID = "a4ssysid";
    private static final String EXTRA_A4S_PRIORITY = "a4spriority";
    private static final String EXTRA_A4S_CATEGORY = "a4scategory";
    private static final String EXTRA_A4S_ACCENT_COLOR = "a4saccentcolor";
    private static final String EXTRA_A4S_TEMPLATE = "a4stemplate";
    private static final String EXTRA_A4S_NOTIFSOUND = "a4snotifsound";
    private static final String EXTRA_A4S_FOREGROUND = "a4sforeground";
    private static final String EXTRA_A4S_GROUP = "a4sgroup";
    private static final String EXTRA_A4S_GROUP_SUMMARY = "a4sgroupsummary";
    private static final String EXTRA_A4S_CONTENT_INFO = "a4scontentinfo";
    private static final String EXTRA_A4S_SUBTEXT = "a4ssubtext";
    private static final String EXTRA_A4S_SUMMARY_TEXT = "a4ssummarytext";
    private static final String EXTRA_A4S_SMALL_ICON_NAME = "a4ssmalliconname";
    private static final String EXTRA_A4S_ICON = "a4sicon";
    private static final String EXTRA_A4S_BUTTONS = "a4sb";
    private static final String EXTRA_A4S_MULTIPLE_LINES = "a4smultiplelines";
    private static final String EXTRA_A4S_BIG_TEMPLATE = "a4sbigtemplate";
    private static final String EXTRA_A4S_BIG_CONTENT_HTML = "a4sbigcontenthtml";
    private static final String EXTRA_A4S_BIG_CONTENT = "a4sbigcontent";
    private static final String EXTRA_A4S_BIG_PICTURE = "a4sbigpicture";
    private static final String EXTRA_ACC_URL = "acc_url";
    private static final String EXTRA_A4S_URL = "a4surl";
    private static final String EXTRA_ACC_ACTION = "acc_action";
    private static final String EXTRA_OPEN_WITH_SAFARI = "openWithSafari";
    private static final String EXTRA_A4S_OPEN_APP = "a4sopenapp";
    private static final String EXTRA_A4S_IS_DECORATED = "a4sIsDecorated";
    private static final String EXTRA_A4S_APP_NAME = "a4sappname";
    private static final String EXTRA_A4S_HEADER_TEXT = "a4sheadertext";
    private static final String EXTRA_ACC_CHANNEL = "acc_channel";

    private final PushMessage message;

    private AccengageMessage(@NonNull PushMessage message) {
        this.message = message;
    }

    /**
     * Factory method.
     *
     * @param message The push message.
     * @return The Accengage message.
     * @throws IllegalArgumentException if the push message is not an Accengage push.
     */
    @NonNull
    public static AccengageMessage fromAirshipPushMessage(@NonNull PushMessage message) {
        if (!message.isAccengagePush()) {
            throw new IllegalArgumentException("PushMessage is not an Accengage push.");
        }

        return new AccengageMessage(message);
    }

    /**
     * Gets Accengage push title.
     *
     * @return Accengage push title.
     */
    @NonNull
    public String getAccengageTitle() {
        return getExtra(EXTRA_A4S_TITLE_HTML, getExtra(EXTRA_A4S_TITLE, ""));
    }

    /**
     * Gets Accengage push content.
     *
     * @return Accengage push content if it is set, otherwise null.
     */
    @Nullable
    public String getAccengageContent() {
        String content = getExtra(EXTRA_A4S_CONTENT_HTML_KEY);
        if (content == null) {
            content = getExtra(ACCENGAGE_CONTENT_KEY);
        }

        return content;
    }

    /**
     * Gets Accengage push system ID.
     *
     * @return Accengage push system ID or default value 1001.
     */
    public int getAccengageSystemId() {
        try {
            return Integer.parseInt(getExtra(EXTRA_A4S_SYSTEM_ID, "1001"));
        } catch (NumberFormatException e) {
            Logger.warn("PushMessage - Impossible to parse Accengage system id, use default value: " + 1001);
            return 1001;
        }
    }

    /**
     * Gets Accengage push priority
     *
     * @return Accengage push priority or default value 0
     */
    public int getAccengagePriority() {
        try {
            return Integer.parseInt(getExtra(EXTRA_A4S_PRIORITY, ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets Accengage push category
     *
     * @return Accengage push category or default value NotificationCompat.CATEGORY_PROMO
     */
    @NonNull
    public String getAccengageCategory() {
        return getExtra(EXTRA_A4S_CATEGORY, NotificationCompat.CATEGORY_PROMO);
    }

    /**
     * Gets Accengage push accent color
     *
     * @return Accengage push accent color or default value 0
     */
    public int getAccengageAccentColor() {
        return getAccengageAccentColor(0);
    }

    /**
     * Gets Accengage push accent color
     *
     * @param defaultValue The default value.
     * @return Accengage push accent color or default value.
     */
    public int getAccengageAccentColor(int defaultValue) {
        if (getExtra(EXTRA_A4S_ACCENT_COLOR) == null) {
            return defaultValue;
        }

        try {
            return Color.parseColor(getExtra(EXTRA_A4S_ACCENT_COLOR));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Gets Accengage push small icon
     *
     * @param context The context.
     * @param defaultValue The default value.
     * @return Accengage push small icon or 0.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int getAccengageSmallIcon(@NonNull Context context, int defaultValue) {
        int smallIconId = defaultValue;

        String smallIconName = getExtra(EXTRA_A4S_SMALL_ICON_NAME);
        // We have a small icon from push, check if icon exists in drawable
        if (!TextUtils.isEmpty(smallIconName)) {
            int smallIconIdFromPush = context.getResources().getIdentifier(smallIconName, "drawable", context.getPackageName());
            if (smallIconIdFromPush > 0) {
                smallIconId = smallIconIdFromPush;
            }
        }
        return smallIconId;
    }

    /**
     * Gets Accengage push small icon
     *
     * @param context A context
     * @return Accengage push small icon or default application icon
     * @deprecated Use {@link #getAccengageSmallIcon(Context, int)} instead.
     */
    @Deprecated
    public int getAccengageSmallIcon(@NonNull Context context) {
        return getAccengageSmallIcon(context, context.getApplicationInfo().icon);
    }

    /**
     * Gets Accengage push notification sound.
     *
     * @return Accengage push notification sound if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageNotificationSound() {
        return getExtra(EXTRA_A4S_NOTIFSOUND);
    }

    /**
     * Gets Accengage push group.
     *
     * @return Accengage push group.
     */
    @Nullable
    public String getAccengageGroup() {
        return getExtra(EXTRA_A4S_GROUP);
    }

    /**
     * Gets Accengage push group summary.
     *
     * @return Accengage push group summary.
     */
    public boolean getAccengageGroupSummary() {
        return getAccengageBooleanExtra(EXTRA_A4S_GROUP_SUMMARY);
    }

    /**
     * Gets Accengage push content info.
     *
     * @return Accengage push content info if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageContentInfo() {
        return getExtra(EXTRA_A4S_CONTENT_INFO);
    }

    /**
     * Gets Accengage push subtext.
     *
     * @return Accengage push subtext if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageSubtext() {
        return getExtra(EXTRA_A4S_SUBTEXT);
    }

    /**
     * Gets Accengage push summary text.
     *
     * @return Accengage push summary text if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageSummaryText() {
        return getExtra(EXTRA_A4S_SUMMARY_TEXT);
    }

    /**
     * Tells if Accengage push has multiple lines.
     *
     * @return {@code true} if Accengage push has multiple lines, otherwise {@code false}.
     */
    public boolean isAccengageMultipleLines() {
        return getAccengageBooleanExtra(EXTRA_A4S_MULTIPLE_LINES);
    }

    /**
     * Gets Accengage push extended template.
     *
     * @return Accengage push extended template if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageBigTemplate() {
        return getExtra(EXTRA_A4S_BIG_TEMPLATE);
    }

    /**
     * Gets Accengage push collapsed template.
     *
     * @return Accengage push collapsed template if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageTemplate() {
        String collapseTemplate = getExtra(EXTRA_A4S_TEMPLATE);
        if (collapseTemplate != null && !collapseTemplate.isEmpty()) {
            return collapseTemplate;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (ACCENGAGE_BIG_TEXT_BIG_PICTURE_EXPANDED_TEMPLATE.equals(getAccengageBigTemplate())) {
                collapseTemplate = ACCENGAGE_BIG_TEXT_BIG_PICTURE_COLLAPSED_TEMPLATE;
            }
        }

        return collapseTemplate;
    }

    /**
     * Gets Accengage push big content.
     *
     * @return Accengage push big content if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageBigContent() {
        String bigContent = getExtra(EXTRA_A4S_BIG_CONTENT_HTML);
        if (bigContent == null) {
            bigContent = getExtra(EXTRA_A4S_BIG_CONTENT);
        }

        if (!TextUtils.isEmpty(bigContent)) {
            bigContent = bigContent.replace("\n", "<br/>");
        }
        return bigContent;
    }

    /**
     * Gets Accengage push big picture url.
     *
     * @return Accengage push big picture url if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageBigPictureUrl() {
        return getExtra(EXTRA_A4S_BIG_PICTURE);
    }

    /**
     * Gets Accengage push channel.
     *
     * @return Accengage push channel if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageChannel() {
        return getExtra(EXTRA_ACC_CHANNEL);
    }

    /**
     * Gets Accengage push large icon.
     *
     * @return Accengage push large icon if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageLargeIcon() {
        return getExtra(EXTRA_A4S_ICON);
    }

    /**
     * Tells if Accengage push can be displayed in foreground.
     *
     * @return {@code true} if the push can be displayed in foreground, otherwise {@code false}.
     */
    public boolean getAccengageForeground() {
        return getAccengageBooleanExtra(EXTRA_A4S_FOREGROUND);
    }

    /**
     * Gets Accengage push action.
     *
     * @return Accengage push action if it is set, otherwise returns null.
     */
    @Nullable
    @Action
    public String getAccengageAction() {
        return getExtra(EXTRA_ACC_ACTION);
    }

    /**
     * Tells if Accengage push action should be opened with browser or with webview.
     *
     * @return {@code true} if the push should be opened with browser, otherwise {@code false}.
     */
    public boolean getAccengageOpenWithBrowser() {
        return getAccengageBooleanExtra(EXTRA_OPEN_WITH_SAFARI);
    }

    /**
     * Tells if Accengage push should be decorated.
     *
     * @return {@code true} if the push should be decorated, otherwise {@code false}.
     */
    public boolean getAccengageIsDecorated() {
        return getAccengageBooleanExtra(EXTRA_A4S_IS_DECORATED);
    }

    /**
     * Gets the push app name from Accengage custom params.
     *
     * @return The push app name from the custom params if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageAppName() {
        return getExtra(EXTRA_A4S_APP_NAME);
    }

    /**
     * Gets the push header text from Accengage custom params
     *
     * @return the push header text from the custom params if it is set, otherwise returns null.
     */
    @Nullable
    public String getAccengageHeaderText() {
        return getExtra(EXTRA_A4S_HEADER_TEXT);
    }

    /**
     * Gets an boolean extra from Accengage keys
     *
     * @param key The Accengage key
     * @return {@code true} if the key can be read as boolean, otherwise {@code false}.
     */
    private boolean getAccengageBooleanExtra(String key) {
        String value = getExtra(key);
        if (value == null) {
            return false;
        }

        return value.matches(".*[yYtT].*");
    }

    /**
     * Gets an extra from the push bundle.
     *
     * @param key The extra key.
     * @return The extra if it is set, otherwise returns null.
     */
    @Nullable
    public String getExtra(@NonNull String key) {
        return message.getExtra(key);
    }

    /**
     * Gets an extra from the push bundle.
     *
     * @param key          The extra key.
     * @param defaultValue Default value if the value does not exist.
     * @return The extra or the default value if the extra does not exist.
     */
    @NonNull
    public String getExtra(@NonNull String key, @NonNull String defaultValue) {
        String value = getExtra(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Gets Accengage push action url.
     *
     * @return Accengage push action url.
     */
    @NonNull
    public String getAccengageUrl() {
        return getExtra(EXTRA_ACC_URL, getExtra(EXTRA_A4S_URL, ""));
    }

    /**
     * Gets the Accengage notification buttons.
     *
     * @return The Accengage notification buttons.
     */
    @NonNull
    public List<AccengagePushButton> getButtons() {
        List<AccengagePushButton> buttons = new ArrayList<>();
        String buttonsStr = getExtra(EXTRA_A4S_BUTTONS);
        if (!TextUtils.isEmpty(buttonsStr)) {
            try {
                JsonValue jsonValue = JsonValue.parseString(buttonsStr);
                for (JsonValue buttonJson : jsonValue.optList()) {
                    buttons.add(AccengagePushButton.fromJson(buttonJson));
                }
            } catch (JsonException e) {
                Logger.error(e, "Failed to parse Accengage buttons");
            }
        }

        return buttons;
    }

    /**
     * Gets the Accengage template Id.
     *
     * @param context The context.
     * @return The template Id, or 0 if not available.
     */
    @LayoutRes
    public int getAccengageTemplateId(@NonNull Context context) {
        String collapsedTemplate = getAccengageTemplate();
        if (TextUtils.isEmpty(collapsedTemplate)) {
            return 0;
        }

        int templateId = getTemplateId(context, collapsedTemplate);
        if (templateId == 0) {
            Logger.warn("AccengageMessage - Wrong short template provided : " + collapsedTemplate +
                    ". Default one will be used");
        }
        return templateId;
    }

    /**
     * Gets the Accengage big template Id.
     *
     * @param context The context.
     * @return The template Id, or 0 if not available.
     */
    @LayoutRes
    public int getAccengageBigTemplateId(@NonNull Context context) {
        String expandedTemplate = getAccengageBigTemplate();
        if (TextUtils.isEmpty(expandedTemplate)) {
            return 0;
        }

        switch (expandedTemplate) {
            case ACCENGAGE_BIG_TEXT_STYLE:
            case ACCENGAGE_BIG_PICTURE_STYLE:
            case ACCENGAGE_INBOX_STYLE:
                return 0;
        }

        int templateId = getTemplateId(context, expandedTemplate);
        if (templateId == 0) {
            Logger.warn("AccengageMessage - Wrong expanded template provided : " + expandedTemplate +
                    ". Default one will be used.");
        }
        return templateId;
    }


    @LayoutRes
    private static int getTemplateId(@NonNull Context context, @Nullable String templateName) {
        if (TextUtils.isEmpty(templateName)) {
            return 0;
        }

        String packageName = context.getPackageName();
        return context.getResources().getIdentifier(templateName, "layout", packageName);
    }

}
