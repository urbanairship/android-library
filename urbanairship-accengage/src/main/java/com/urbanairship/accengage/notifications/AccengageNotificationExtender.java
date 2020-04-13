/* Copyright Airship and Contributors */

package com.urbanairship.accengage.notifications;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import com.urbanairship.Logger;
import com.urbanairship.accengage.AccengageMessage;
import com.urbanairship.accengage.AccengagePushButton;
import com.urbanairship.accengage.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Accengage notification extender.
 */
class AccengageNotificationExtender implements NotificationCompat.Extender {

    protected final Context context;
    protected final AccengageMessage message;
    protected final NotificationArguments arguments;

    AccengageNotificationExtender(@NonNull Context context, @NonNull AccengageMessage message, @NonNull NotificationArguments arguments) {
        this.context = context;
        this.message = message;
        this.arguments = arguments;
    }

    @NonNull
    @Override
    public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {
        setCommonFields(builder);

        boolean isCollapsedFieldsSet = false;
        if (message.getAccengageTemplateId(context) != 0) {
            setCustomCollapsedFields(builder);
        } else {
            setCollapsedFields(builder);
            isCollapsedFieldsSet = true;
        }

        if (message.getAccengageBigTemplateId(context) != 0) {
            setCustomExpandedFields(builder);
        } else {
            if (!isCollapsedFieldsSet) {
                setCollapsedFields(builder);
            }
            setExpandedFields(builder);
        }

        return builder;
    }


    private void setCommonFields(@NonNull NotificationCompat.Builder builder) {
        Logger.debug("AccengageNotificationExtender - Setting Accengage push common fields");

        builder.setCategory(message.getAccengageCategory())
                .setGroup(message.getAccengageGroup())
                .setGroupSummary(message.getAccengageGroupSummary())
                .setPriority(message.getAccengagePriority())
                .setLights(0xFFFFFFFF, 1000, 3000)
                .setAutoCancel(true);

        int defaults = 0;
        if (context.getPackageManager().checkPermission(Manifest.permission.VIBRATE, context.getPackageName()) == PackageManager.PERMISSION_GRANTED) {
            defaults |= NotificationCompat.DEFAULT_VIBRATE;
        }

        // Set sound
        String accengageNotifSound = message.getAccengageNotificationSound();
        if (!TextUtils.isEmpty(accengageNotifSound)) {
            if (accengageNotifSound.equalsIgnoreCase("none")) {
                Logger.verbose("AccengageNotificationExtender - No sound for this notification");
            } else if (accengageNotifSound.equalsIgnoreCase("default")) {
                defaults |= NotificationCompat.DEFAULT_SOUND;
                Logger.verbose("AccengageNotificationExtender - Use default sound for this notification");
            } else {
                int soundResId = context.getResources().getIdentifier(accengageNotifSound, "raw", context.getPackageName());
                if (soundResId > 0) {
                    Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + soundResId);
                    builder.setSound(soundUri, AudioManager.STREAM_NOTIFICATION);
                    Logger.verbose("AccengageNotificationExtender - Using " + accengageNotifSound + " as notification sound");
                } else {
                    defaults |= NotificationCompat.DEFAULT_SOUND;
                    Logger.warn("AccengageNotificationExtender - Could not find " + accengageNotifSound + " in raw folder, will use default sound instead");
                }
            }
        }

        builder.setDefaults(defaults);
    }

    private void setCollapsedFields(@NonNull NotificationCompat.Builder builder) {
        Logger.debug("AccengageNotificationExtender - Setting Accengage push collapsed fields");

        builder.setContentTitle(Html.fromHtml(!message.getAccengageTitle().isEmpty() ? message.getAccengageTitle() : getAppName(context)))
                .setContentText(Html.fromHtml(message.getAccengageContent()))
                .setTicker(Html.fromHtml(message.getAccengageContent()))
                .setColor(message.getAccengageAccentColor())
                .setSmallIcon(message.getAccengageSmallIcon(context));

        String contentInfo = message.getAccengageContentInfo();
        if (!TextUtils.isEmpty(contentInfo)) {
            try {
                builder.setNumber(Integer.parseInt(contentInfo));
            } catch (NumberFormatException e) {
                builder.setContentInfo(Html.fromHtml(contentInfo));
            }
        }

        if (message.getAccengageSubtext() != null) {
            builder.setSubText(Html.fromHtml(message.getAccengageSubtext()));
        }

        String largeIconUrl = message.getAccengageLargeIcon();
        if (largeIconUrl != null) {
            try {
                URL url = new URL(largeIconUrl);
                Bitmap largeIcon = NotificationUtils.fetchBigImage(context, url);
                builder.setLargeIcon(largeIcon);
            } catch (MalformedURLException e) {
                Logger.error(e, "AccengageNotificationExtender - Malformed large icon URL.");
            }
        }
    }

    private void setCustomCollapsedFields(@NonNull NotificationCompat.Builder builder) {
        int resId = message.getAccengageTemplateId(context);

        Logger.debug("AccengageNotificationExtender - Using collapsed custom template: " + message.getAccengageTemplate());

        RemoteViews collapsedTemplateViews = new RemoteViews(context.getPackageName(), resId);
        collapsedTemplateViews.setTextViewText(R.id.text, Html.fromHtml(message.getAccengageContent()));

        builder.setCustomContentView(fillCustomTemplate(builder, collapsedTemplateViews));

        if (shouldApplyDecoratedCustomViewStyle()) {
            Logger.verbose( "AccengageNotificationExtender - apply decoration for collapsed template: " + message.getAccengageTemplate());
            builder.setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        }
    }

    private RemoteViews fillCustomTemplate(@NonNull NotificationCompat.Builder builder, @NonNull RemoteViews views) {
        builder.setSmallIcon(message.getAccengageSmallIcon(context));
        Spanned title = Html.fromHtml(!message.getAccengageTitle().isEmpty() ? message.getAccengageTitle() : getAppName(context));
        views.setTextViewText(R.id.title, title);


        views.setViewVisibility(R.id.time, View.VISIBLE);
        views.setLong(R.id.time, "setTime", System.currentTimeMillis());

        String contentInfo = message.getAccengageContentInfo();
        if (!TextUtils.isEmpty(contentInfo)) {
            views.setTextViewText(R.id.info, Html.fromHtml(contentInfo));
            views.setViewVisibility(R.id.info, View.VISIBLE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fillCustomTemplateAndroidN(views);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fillCustomTemplateAndroidL(views);
        } else  {
            fillCustomTemplateAndroidJB(views);
        }

        return views;
    }

    private void fillCustomTemplateAndroidL(@NonNull RemoteViews views) {
        String largeIconUrl = message.getAccengageLargeIcon();
        if (largeIconUrl != null) {
            try {
                URL url = new URL(largeIconUrl);
                Bitmap largeIcon = NotificationUtils.fetchBigImage(context, url);
                // Set large icon
                views.setImageViewBitmap(R.id.icon, largeIcon);
                views.setViewPadding(R.id.icon, 0, 0, 0, 0);
                views.setInt(R.id.icon, "setBackgroundResource", 0);
            } catch (MalformedURLException e) {
                Logger.error(e, "AccengageNotificationExtender - Malformed large icon URL.");
            }
            // Set small icon
            views.setViewVisibility(R.id.right_icon, View.VISIBLE);
            views.setImageViewResource(R.id.right_icon, message.getAccengageSmallIcon(context));
            setDrawableParameters(views, R.id.right_icon, false, -1, 0xFFFFFFFF, PorterDuff.Mode.SRC_ATOP, -1);
            views.setInt(R.id.right_icon, "setBackgroundResource", R.drawable.accengage_notification_icon_legacy_bg);
            setDrawableParameters(views, R.id.right_icon, true, -1, message.getAccengageAccentColor(), PorterDuff.Mode.SRC_ATOP, -1);
        } else {
            // Use small icon as a large icon
            Logger.verbose("AccengageNotificationExtender - Large icon is not set, use default one");
            views.setImageViewResource(R.id.icon, message.getAccengageSmallIcon(context));
            views.setInt(R.id.icon, "setBackgroundResource", R.drawable.accengage_notification_icon_legacy_bg);
            setDrawableParameters(views, R.id.icon, true, -1, message.getAccengageAccentColor(), PorterDuff.Mode.SRC_ATOP, -1);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.accengage_notification_large_icon_circle_padding);
            views.setViewPadding(R.id.icon, padding, padding, padding, padding);
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private void fillCustomTemplateAndroidN(@NonNull RemoteViews views) {
        // Header
        views.setImageViewResource(R.id.icon, message.getAccengageSmallIcon(context));
        setDrawableParameters(views, R.id.icon, true, -1, 0xFFFFFFFF, PorterDuff.Mode.SRC_ATOP, -1);
        setDrawableParameters(views, R.id.icon, false, -1, message.getAccengageAccentColor(), PorterDuff.Mode.SRC_ATOP, -1);

        String appName = message.getAccengageAppName();
        if (!TextUtils.isEmpty(appName)) {
            appName = getAppName(context);
            views.setTextViewText(R.id.app_name_text, appName);
        }

        String headerText = message.getAccengageHeaderText();
        if (!TextUtils.isEmpty(headerText)) {
            views.setViewVisibility(R.id.header_text, View.VISIBLE);
            views.setTextViewText(R.id.header_text, Html.fromHtml(headerText));
        }

        String contentInfo = message.getAccengageContentInfo();
        if (!TextUtils.isEmpty(contentInfo)) {
            views.setTextViewText(R.id.text_line_1, Html.fromHtml(contentInfo));
        }

        String largeIconUrl = message.getAccengageLargeIcon();
        if (largeIconUrl != null) {
            try {
                URL url = new URL(largeIconUrl);
                Bitmap largeIcon = NotificationUtils.fetchBigImage(context, url);
                views.setImageViewBitmap(R.id.right_icon, largeIcon);
                views.setViewVisibility(R.id.right_icon, View.VISIBLE);
            } catch (MalformedURLException e) {
                Logger.error(e, "AccengageNotificationExtender - Malformed large icon URL.");
            }
        } else {
            Logger.verbose("AccengageNotificationExtender - Large icon is not set");
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void fillCustomTemplateAndroidJB(@NonNull RemoteViews views) {
        String largeIconUrl = message.getAccengageLargeIcon();
        if (largeIconUrl != null) {
            try {
                URL url = new URL(largeIconUrl);
                Bitmap largeIcon = NotificationUtils.fetchBigImage(context, url);
                views.setImageViewBitmap(R.id.icon, largeIcon);
            } catch (MalformedURLException e) {
                Logger.error(e, "AccengageNotificationExtender - Malformed large icon URL.");
            }
            views.setViewVisibility(R.id.right_icon, View.VISIBLE);
            views.setImageViewResource(R.id.right_icon, message.getAccengageSmallIcon(context));
        } else {
            Logger.verbose("AccengageNotificationExtender - Large icon is not set, use default one");
            views.setImageViewResource(R.id.icon, message.getAccengageSmallIcon(context));
        }
    }

    private void setExpandedFields(@NonNull NotificationCompat.Builder builder) {
        Logger.debug("AccengageNotificationExtender - Setting Accengage push expanded fields");

        // Set button actions
        List<AccengagePushButton> buttons = message.getButtons();
        for (final AccengagePushButton button : buttons) {

            NotificationActionButton actionButton = NotificationActionButton.newBuilder(button.getId())
                    .setDescription("Accengage: " + button.getId())
                    .setIcon(button.getIcon(context))
                    .setLabel(button.getTitle())
                    .setPerformsInForeground(button.getOpenApp())
                    .build();

            builder.addAction(actionButton.createAndroidNotificationAction(context, null, arguments));
        }

        if (message.isAccengageMultipleLines()) {
            String extendedTemplate = message.getAccengageBigTemplate();
            if (extendedTemplate != null) {
                switch (extendedTemplate) {
                    case AccengageMessage.ACCENGAGE_BIG_TEXT_STYLE:
                        applyBigTextStyle(builder);
                        break;
                    case AccengageMessage.ACCENGAGE_BIG_PICTURE_STYLE:
                        if (!applyBigPictureStyle(builder)) {
                            applyBigTextStyle(builder);
                        }
                        break;
                    default:
                        Logger.warn("AccengageNotificationExtender - Unknown expanded default template: " + extendedTemplate);
                        applyBigTextStyle(builder);
                        break;
                }
            }
        }
    }

    private void setCustomExpandedFields(@NonNull NotificationCompat.Builder builder) {
        int resId = message.getAccengageBigTemplateId(context);
        Logger.debug("AccengageNotificationExtender - Using expanded custom template: " + message.getAccengageBigTemplate());

        RemoteViews expandedTemplateViews = new RemoteViews(context.getPackageName(), resId);
        String bigContent = message.getAccengageBigContent();
        if (bigContent != null) {
            expandedTemplateViews.setTextViewText(R.id.text, Html.fromHtml(bigContent));
        }

        builder.setCustomBigContentView(fillCustomTemplate(builder, expandedTemplateViews));


        RemoteViews views = builder.getBigContentView();

        String bigPictureUrl = message.getAccengageBigPictureUrl();
        if (bigPictureUrl != null) {
            URL url;
            try {
                url = new URL(bigPictureUrl);
                Bitmap bitmap = NotificationUtils.fetchBigImage(context, url);

                Logger.verbose("AccengageNotificationExtender - set big picture");
                views.setImageViewBitmap(R.id.big_picture, bitmap);
                views.setViewVisibility(R.id.big_picture, View.VISIBLE);
            } catch (MalformedURLException e) {
                Logger.error(e, "AccengageNotificationExtender - Malformed big picture URL.");
            }
        } else {
            Logger.warn("AccengageNotificationExtender - No picture found");
        }

        builder.setCustomBigContentView(views);

        if (shouldApplyDecoratedCustomViewStyle()) {
            Logger.verbose("AccengageNotificationExtenderN - apply decoration for expanded template: " + message.getAccengageBigTemplate());
            builder.setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        }
    }

    /**
     * Retrieves current application name
     *
     * @param context Android context
     * @return Application name
     */
    private static String getAppName(final Context context) {
        final ApplicationInfo appInfo = context.getApplicationInfo();
        final PackageManager packageManager = context.getPackageManager();
        return (String) packageManager.getApplicationLabel(appInfo);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setDrawableParameters(RemoteViews remoteViews, int viewId, boolean targetBackground, int alpha, int colorFilter, PorterDuff.Mode mode, int level) {
        try {
            Class c = Class.forName("android.widget.RemoteViews");
            Method m = c.getMethod("setDrawableParameters", int.class, boolean.class, int.class, int.class, PorterDuff.Mode.class, int.class);
            m.invoke(remoteViews, viewId, targetBackground, alpha, colorFilter, mode, level);
        } catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            Logger.debug("AccengageNotificationExtender - Impossible to define custom push template icon", e);
        }
    }

    private void applyBigTextStyle(@NonNull NotificationCompat.Builder builder) {
        Logger.debug("AccengageNotificationExtender - Applying Accengage BigTextStyle");
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        String bigContent = message.getAccengageBigContent();
        if (bigContent != null) {
            bigTextStyle.bigText(Html.fromHtml(bigContent));
        }
        String summaryText = message.getAccengageSummaryText();
        if (!TextUtils.isEmpty(summaryText)) {
            bigTextStyle.setSummaryText(Html.fromHtml(summaryText));
        }
        builder.setStyle(bigTextStyle);
    }

    private boolean applyBigPictureStyle(@NonNull NotificationCompat.Builder builder) {
        Logger.debug("AccengageNotificationExtender - Applying Accengage BigPictureStyle");
        NotificationCompat.BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle();

        String bigPictureUrl = message.getAccengageBigPictureUrl();
        if (bigPictureUrl == null) {
            Logger.warn("AccengageNotificationExtender - No picture found");
            return false;
        }

        URL url;
        try {
            url = new URL(bigPictureUrl);
        } catch (MalformedURLException e) {
            Logger.error(e, "AccengageNotificationExtender - Malformed big picture URL.");
            return false;
        }

        Bitmap bitmap = NotificationUtils.fetchBigImage(context, url);

        if (bitmap == null) {
            return false;
        }

        // Set big picture image
        bigPictureStyle.bigPicture(bitmap);

        String bigContent = message.getAccengageBigContent();
        if (!TextUtils.isEmpty(bigContent)) {
            bigPictureStyle.setSummaryText(Html.fromHtml(bigContent));
        } else if (!TextUtils.isEmpty(message.getAccengageContent())) {
            bigPictureStyle.setSummaryText(Html.fromHtml(message.getAccengageContent()));
        }

        builder.setStyle(bigPictureStyle);
        return true;
    }

    private boolean shouldApplyDecoratedCustomViewStyle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        if (AccengageMessage.ACCENGAGE_BIG_TEXT_BIG_PICTURE_EXPANDED_TEMPLATE.equals(message.getAccengageBigTemplate())
                || AccengageMessage.ACCENGAGE_BIG_TEXT_BIG_PICTURE_COLLAPSED_TEMPLATE.equals(message.getAccengageTemplate())) {
            return true;
        }

        return message.getAccengageIsDecorated();
    }
}
