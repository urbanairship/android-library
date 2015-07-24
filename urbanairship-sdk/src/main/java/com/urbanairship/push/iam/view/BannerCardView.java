/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push.iam.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;

import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

/**
 * The banner view for in-app messages that uses the v7 card view to implement backwards compatible
 * elevation and configurable corner radius.
 */
public class BannerCardView extends CardView implements Banner {

    private final BannerContent content;

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
        content = new BannerContent(context, this, attrs, defStyleAttr);

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

        setCardBackgroundColor(content.getPrimaryColor());
    }

    @Override
    public void setOnDismissClickListener(OnDismissClickListener onClickListener) {
        content.setOnDismissClickListener(onClickListener);
    }

    @Override
    public void setOnActionClickListener(OnActionClickListener onClickListener) {
        content.setOnActionClickListener(onClickListener);
    }

    @Override
    public void setText(CharSequence text) {
        content.setText(text);
    }

    @Override
    public void setNotificationActionButtonGroup(NotificationActionButtonGroup group) {
        content.setNotificationActionButtonGroup(group);
    }

    @Override
    public void setPrimaryColor(int color) {
        setCardBackgroundColor(color);
        content.setPrimaryColor(color);
    }

    @Override
    public void setSecondaryColor(int color) {
        content.setSecondaryColor(color);
    }
}
