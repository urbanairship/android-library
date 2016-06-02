/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.NotificationIdGenerator;

import java.io.IOException;

/**
 * A notification factory that allows the use of layout XML and a custom notification sound.
 *
 * A layout resource is required. It can include a <code>ImageView</code>
 *
 * It must include an <code>ImageView</code> for an icon, a
 * <code>TextView</code> for the alert subject or title, and a <code>TextView</code> for
 * the alert message. Each of these is required, but if your layout does not make use of one or
 * more of the items, set the visibility to <code>gone</code> (<code>android:visibility="gone"</code>).
 *
 * A sample layout.xml file:
 *
 * <pre>
 * {@code

<RelativeLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="fill_parent"
    android:orientation="vertical"
    android:paddingTop="2dip"
    android:layout_alignParentTop="true"
    android:layout_height="fill_parent">

  <ImageView android:id="@+id/icon"
      android:src="@drawable/icon_1"
      android:layout_marginRight="4dip"
      android:layout_marginLeft="5dip"
      android:layout_width="100dip"
      android:layout_height="100dip" />

  <!-- The custom notification requires a subject field.
  To accommodate multiple lines in this layout this
  field is hidden. Visibility is set to gone. -->
  <TextView android:id="@+id/subject"
      android:text="Subject"
      android:layout_alignTop="@+id/icon"
      android:layout_toRightOf="@+id/icon"
      android:layout_height="wrap_content"
      android:layout_width="wrap_content"
      android:maxLines="1" android:visibility="gone"/>

  <!-- The message block. Standard text size is 14dip
  but is reduced here to maximize content. -->
  <TextView android:id="@+id/message"
      android:textSize="12dip"
      android:textColor="#FF000000"
      android:text="Message"
      android:maxLines="4"
      android:layout_marginTop="0dip"
      android:layout_marginRight="2dip"
      android:layout_marginLeft="0dip"
      android:layout_height="wrap_content"
      android:layout_toRightOf="@+id/icon"
      android:layout_width="wrap_content" />

</RelativeLayout>
 * }
 * </pre>
 * <p>
 * Note: If you are planning on targeting Honeycomb devices (and beyond), the above layout will not display
 * properly, as it will show black text on the default black background that Honeycomb and above uses for notifications.
 * Instead of explicitly setting a light background color, which may look out of place across multiple devices,
 * you should set the <code>android:textAppearance</code> property on your TextViews to
 * <code>@android:style/TextAppearance.StatusBar.EventContent.Title</code> or
 * <code>@android:style/TextAppearance.StatusBar.EventContent</code>, which were introduced in API level 9, to ensure
 * that your layout will adapt gracefully as the platform changes.
 * <p>
 * Because these styles are not available prior to API level 9, if you intend to make your app backwards compatible with
 * prior API levels, you can do so by building for the newer API but set an older minimum SDK setting for your app, and
 * including the level 9 compatible layout in its own resource directory, called "layout-v9".  See the Push Sample application
 * for a concrete example.
 * <p>
 * Below is a sample Honeycomb-compatible layout, similar to the one above:
 *
 * <pre>
 *
 * {@code
 *
   <RelativeLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="fill_parent"
    android:orientation="vertical"
    android:paddingTop="0dip"
    android:layout_alignParentTop="true"
    android:layout_height="fill_parent">

  <ImageView android:id="@+id/icon"
      android:src="@drawable/icon"
      android:layout_marginRight="10dip"
      android:layout_marginLeft="0dip"
      android:layout_width="65dip"
      android:layout_height="65dip" />

  <!-- The custom notification requires a subject field.
  To accommodate multiple lines in this layout this
  field is hidden. Visibility is set to gone. -->
  <TextView android:id="@+id/subject"
      android:text="Subject"
      android:textAppearance="@android:style/TextAppearance.StatusBar.EventContent.Title"
      android:layout_alignTop="@+id/icon"
      android:layout_toRightOf="@+id/icon"
      android:layout_height="wrap_content"
      android:layout_width="wrap_content"
      android:maxLines="1" android:visibility="gone"/>

  <!-- The message block. Standard text size is 14dip
  but is reduced here to maximize content. -->
  <TextView android:id="@+id/message"
      android:textSize="12dip"
      android:textAppearance="@android:style/TextAppearance.StatusBar.EventContent"
      android:text="Message"
      android:maxLines="4"
      android:layout_marginTop="0dip"
      android:layout_marginRight="2dip"
      android:layout_marginLeft="0dip"
      android:layout_height="wrap_content"
      android:layout_toRightOf="@+id/icon"
      android:layout_width="wrap_content" />

   </RelativeLayout>
 * }
 * </pre>
 *
 *
 * <p>
 * And here is a sample implementation:
 *
 * <pre>
 *
 * {@code

       CustomLayoutNotificationFactory nf = new CustomLayoutNotificationFactory(this);
       nf.layout = R.layout.notification_layout; // The layout resource to use
       nf.layoutIconDrawableId = R.drawable.notification_icon; // The icon you want to display
       nf.layoutIconId = R.id.icon; // The icon's layout 'id'
       nf.layoutSubjectId = R.id.subject; // The id for the 'subject' field
       nf.layoutMessageId = R.id.message; // The id for the 'message' field

       //set this ID to a value > 0 if you want a new notification to replace the previous one
       nf.constantNotificationId = 100;

       //set this if you want a custom sound to play
       nf.soundUri = Uri.parse("android.resource://"+this.getPackageName()+"/" +R.raw.notification_mp3);

       // Set the factory
       UAirship.shared().getPushManager().setNotificationBuilder(nf);
 * }
 * </pre>
 */
public class CustomLayoutNotificationFactory extends NotificationFactory {

    /**
     * The layout resource.
     * <p/>
     * For example, if the layout is <code>notification_layout.xml</code>, use <code>R.layout.notification_layout</code>.
     */
    public int layout;


    /**
     * The layout id for the icon.
     * <p/>
     * For example: <code>R.id.icon</code>. This field will be populated with the icon drawable.
     */
    public int layoutIconId;


    /**
     * The layout id for the subject <code>TextView</code>.
     * <p/>
     * For example: <code>R.id.subject</code>. This field will be populated with the application name.
     */
    public int layoutSubjectId;

    /**
     * The layout id for the message <code>TextView</code>.
     * <p/>
     * For example: <code>R.id.message</code>. This field will be populated with the alert message.
     */
    public int layoutMessageId;

    /**
     * The icon drawable to display in the custom layout.
     * <p/>
     * For example: <code>R.drawable.notification_icon</code>
     */
    public int layoutIconDrawableId;

    /**
     * The icon drawable to display in the status bar.
     */
    public int statusBarIconDrawableId;


    /**
     * An optional constant notification ID.
     * <p/>
     * By default, this builder uses unique, incremented notification IDs.
     * That ID scheme ensures that all notifications are displayed until
     * dismissed by a user. However, if a single notification scheme is desired
     * where each new notification replaces the previous one, set this to
     * a value greater than zero.
     * <p/>
     * If <code>constantNotificationId <= 0</code>, the standard incrementing
     * behavior will be used.
     */
    public int constantNotificationId = -1;

    /**
     * An optional sound URI.
     * <p/>
     * If not <code>null</code>, the sound at this URI will be played
     * when a notification is received.
     */
    public Uri soundUri;

    public CustomLayoutNotificationFactory(Context context) {
        super(context);
        layoutIconDrawableId = statusBarIconDrawableId = context.getApplicationInfo().icon;
    }

    @Override
    public Notification createNotification(@NonNull PushMessage pushMessage, int notificationId) {
        String alert = pushMessage.getAlert();
        // do not display a notification if there is not an alert
        if (alert == null || alert.length() == 0) {
            return null;
        }


        RemoteViews contentView = new RemoteViews(getContext().getPackageName(), layout);
        if (layoutIconId == 0 || layoutSubjectId == 0 || layoutMessageId == 0) {
            Logger.error("The CustomLayoutNotificationFactory object contains an invalid identifier (value of 0). layoutIconId: "
                    + layoutIconId + " layoutSubjectId: " + layoutSubjectId + " layoutMessageId: " + layoutMessageId);
            throw new IllegalArgumentException("Unable to build notification. CustomLayoutNotificationFactory missing required parameter.");
        }

        contentView.setTextViewText(layoutSubjectId, pushMessage.getTitle() != null ? pushMessage.getTitle() : UAirship.getAppName());
        contentView.setTextViewText(layoutMessageId, alert);
        contentView.setImageViewResource(layoutIconId, layoutIconDrawableId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setContent(contentView)
                .setAutoCancel(true)
                .setSmallIcon(statusBarIconDrawableId)
                .setLocalOnly(pushMessage.isLocalOnly())
                .setPriority(pushMessage.getPriority())
                .setCategory(pushMessage.getCategory())
                .setVisibility(pushMessage.getVisibility());

        // Public notification - Android L and above.
        Notification publicNotification = createPublicVersionNotification(pushMessage, layoutIconId);
        if (publicNotification != null) {
            builder.setPublicVersion(publicNotification);
        }


        int defaults = NOTIFICATION_DEFAULTS;

        if (soundUri != null) {
            builder.setSound(soundUri);

            // Remove the DEFAULT_SOUND flag
            defaults &= ~NotificationCompat.DEFAULT_SOUND;
        }

        builder.setDefaults(defaults);


        NotificationCompat.Style style = null;
        try {
            style = createNotificationStyle(pushMessage);
        } catch (IOException e) {
            Logger.error("Failed to create notification style.", e);
        }

        if (style != null) {
            builder.setStyle(style);
        }

        if (!pushMessage.isLocalOnly()) {
            try {
                builder.extend(createWearableExtender(pushMessage, notificationId));
            } catch (IOException e) {
                Logger.error("Failed to create wearable extender.", e);
            }
        }

        builder.extend(createNotificationActionsExtender(pushMessage, notificationId));

        Notification notification = builder.build();
        // workaround to resolve bug where support library NotificationCompat.Builder ignores custom RemoteView
        notification.contentView = contentView;

        return notification;
    }

    @Override
    public int getNextId(@NonNull PushMessage pushMessage) {
        if (constantNotificationId > 0) {
            return constantNotificationId;
        } else {
            return NotificationIdGenerator.nextID();
        }
    }

}
