/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

/**
 * The banner view for in-app messages.
 */
public class BannerView extends FrameLayout implements Banner {

    private final BannerContent content;

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
        content = new BannerContent(context, this, attrs, defStyleAttr);

        if (getBackground() != null) {
            getBackground().setColorFilter(content.getPrimaryColor(), PorterDuff.Mode.MULTIPLY);
        } else {
            setBackgroundColor(content.getPrimaryColor());
        }
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
        if (getBackground() != null) {
            getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        } else {
            setBackgroundColor(color);
        }

        content.setPrimaryColor(color);
    }

    @Override
    public void setSecondaryColor(int color) {
        content.setSecondaryColor(color);
    }
}
