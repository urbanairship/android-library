/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

/**
 * The banner view for in-app messages.
 */
public class BannerView extends FrameLayout implements Banner {

    private final BannerContentView contentView;

    /**
     * Construct a banner view.
     *
     * @param context A Context object used to access application assets.
     */
    public BannerView(Context context) {
        this(context, null, R.attr.inAppMessageBannerStyle);
    }

    /**
     * Construct a banner view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs The view's attributes.
     */
    public BannerView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.inAppMessageBannerStyle);
    }

    /**
     * Construct a banner view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs The view's attributes.
     * @param defStyleAttr The default style attribute resource ID.
     */
    public BannerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        View.inflate(context, R.layout.ua_iam_content, this);

        this.contentView = (BannerContentView) findViewById(R.id.iam_banner_content);

        updateBackground(contentView.getPrimaryColor());
    }

    @Override
    public void setOnDismissClickListener(OnDismissClickListener onClickListener) {
        contentView.setOnDismissClickListener(onClickListener);
    }

    @Override
    public void setOnActionClickListener(OnActionClickListener onClickListener) {
        contentView.setOnActionClickListener(onClickListener);
    }

    @Override
    public void setText(CharSequence text) {
        contentView.setText(text);
    }

    @Override
    public void setNotificationActionButtonGroup(NotificationActionButtonGroup group) {
        contentView.setNotificationActionButtonGroup(group);
    }

    @Override
    public void setPrimaryColor(int color) {
        updateBackground(color);
        contentView.setPrimaryColor(color);
    }

    @Override
    public void setSecondaryColor(int color) {
        contentView.setSecondaryColor(color);
    }

    private void updateBackground(int color) {
        if (getBackground() == null) {
            setBackgroundColor(color);
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) getBackground()).setColor(color);
        } else {
            getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
    }
}
