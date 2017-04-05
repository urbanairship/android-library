/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

/**
 * The banner view for in-app messages that uses the v7 card view to implement backwards compatible
 * elevation and configurable corner radius.
 */
public class BannerCardView extends CardView implements Banner {

    private final BannerContentView contentView;

    /**
     * Construct a banner card view.
     *
     * @param context A Context object used to access application assets.
     */
    public BannerCardView(Context context) {
        this(context, null, R.attr.inAppMessageBannerStyle);
    }

    /**
     * Construct a banner card view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs The view's attributes.
     */
    public BannerCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.inAppMessageBannerStyle);
    }

    /**
     * Construct a banner card view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs The view's attributes.
     * @param defStyleAttr The default style attribute resource ID.
     */
    public BannerCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View.inflate(context, R.layout.ua_iam_content, this);
        this.contentView = (BannerContentView) findViewById(R.id.iam_banner_content);

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CardView, defStyleAttr, R.style.InAppMessage_Banner);

            if (!a.hasValue(R.styleable.CardView_cardBackgroundColor) && a.hasValue(R.styleable.CardView_optCardBackgroundColor)) {
                int color = a.getInteger(R.styleable.CardView_optCardBackgroundColor, 0);
                setCardBackgroundColor(color);
            }

            if (!a.hasValue(R.styleable.CardView_cardElevation) && a.hasValue(R.styleable.CardView_optCardElevation)) {
                float elevation = a.getDimension(R.styleable.CardView_optCardElevation, 0);
                if (elevation > getMaxCardElevation()) {
                    setMaxCardElevation(elevation);
                }
                setCardElevation(elevation);
            }

            if (!a.hasValue(R.styleable.CardView_cardCornerRadius) && a.hasValue(R.styleable.CardView_optCardCornerRadius)) {
                float radius = a.getDimension(R.styleable.CardView_optCardCornerRadius, 0);
                setRadius(radius);
            }

            a.recycle();
        }

        setCardBackgroundColor(contentView.getPrimaryColor());
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
        setCardBackgroundColor(color);
        contentView.setPrimaryColor(color);
    }

    @Override
    public void setSecondaryColor(int color) {
        contentView.setSecondaryColor(color);
    }
}
