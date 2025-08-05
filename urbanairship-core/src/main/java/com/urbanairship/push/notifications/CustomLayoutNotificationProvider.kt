package com.urbanairship.push.notifications

import android.R
import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.app.NotificationCompat
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.UAirship
import com.urbanairship.UAirship.Companion.applicationContext

/**
 * A notification provider that allows the use of layout XML. The default binding will
 * bind the following:
 * - small icon to `android.R.id.icon`
 * - title to `android.R.id.title`
 * - summary/subtitle to `android.R.id.summary`
 * - alert/message to `android.R.id.message`
 *
 * Custom binding can be applied by overriding [onBindContentView].
 */
public class CustomLayoutNotificationProvider public constructor(
    context: Context,
    configOptions: AirshipConfigOptions,
    @param:LayoutRes private val layoutId: Int
) : AirshipNotificationProvider(context, configOptions) {

    override fun onExtendBuilder(
        context: Context,
        builder: NotificationCompat.Builder,
        arguments: NotificationArguments
    ): NotificationCompat.Builder {

        val contentView = RemoteViews(context.packageName, layoutId)
        onBindContentView(contentView, arguments)

        return builder.setCustomContentView(contentView)
    }

    /**
     * Called to bind the content view.
     *
     * @param contentView The custom content view.
     * @param arguments The notification arguments.
     */
    protected fun onBindContentView(contentView: RemoteViews, arguments: NotificationArguments) {
        val message = arguments.message
        contentView.setTextViewText(
            R.id.title, message.title ?: applicationContext.packageManager.getApplicationLabel(
                applicationContext.applicationInfo).toString()
        )
        contentView.setTextViewText(R.id.message, message.alert)
        contentView.setTextViewText(R.id.summary, message.summary)
        contentView.setImageViewResource(R.id.icon, smallIcon)
    }
}
