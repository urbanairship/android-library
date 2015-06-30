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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.util.UAStringUtil;

/**
 * Helper class to manage the banner view's content. Common code between the
 * {@link BannerCardView} and {@link BannerView}.
 */
class BannerContent implements Banner {

    /**
     * The size of action button icons.
     */
    private static final int ACTION_BUTTON_ICON_SIZE_DP = 32;

    private final Context context;
    private TextView messageTextView;
    private View actionsDividerView;
    private ImageButton dismissButton;
    private ViewGroup actionButtonViewGroup;
    private int primaryColor;
    private int secondaryColor;
    private int actionButtonTextAppearance;

    private Typeface typeface;


    private Banner.OnDismissClickListener dismissClickListener;
    private Banner.OnActionClickListener actionClickListener;

    /**
     * Creates a new BannerContent.
     * @param context The view's context.
     * @param bannerView The banner view.
     * @param attrs The banner view's attributes.
     * @param defStyleAttr The default style attribute.
     */
    BannerContent(Context context, ViewGroup bannerView, AttributeSet attrs, int defStyleAttr) {
        this.context = context;

        View view = BannerView.inflate(context, R.layout.ua_iam_content, bannerView);
        this.messageTextView = (TextView) view.findViewById(R.id.alert);
        this.actionsDividerView = view.findViewById(R.id.action_divider);
        this.actionButtonViewGroup = (ViewGroup) view.findViewById(R.id.action_buttons);
        this.dismissButton = (ImageButton) view.findViewById(R.id.close);

        actionButtonViewGroup.setVisibility(View.GONE);
        actionsDividerView.setVisibility(View.GONE);

        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dismissClickListener != null) {
                    dismissClickListener.onDismissClick();
                }
            }
        });

        if (attrs != null) {
            TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.BannerView, defStyleAttr, R.style.InAppMessage_Banner);

            String fontPath = attributes.getString(R.styleable.BannerView_bannerFontPath);
            if (!UAStringUtil.isEmpty(fontPath)) {
                typeface = Typeface.createFromAsset(context.getAssets(), fontPath);
            }

            int defaultPrimary = context.getResources().getColor(R.color.ua_iam_primary);
            int defaultSecondary = context.getResources().getColor(R.color.ua_iam_secondary);

            setPrimaryColor(attributes.getColor(R.styleable.BannerView_bannerPrimaryColor, defaultPrimary));
            setSecondaryColor(attributes.getColor(R.styleable.BannerView_bannerSecondaryColor, defaultSecondary));

            if (attributes.getBoolean(R.styleable.BannerView_bannerNoDismissButton, false)) {
                dismissButton.setVisibility(View.GONE);
            } else {
                Drawable dismissDrawable = attributes.getDrawable(R.styleable.BannerView_bannerDismissButtonDrawable);
                if (dismissDrawable != null) {
                    dismissButton.setImageDrawable(dismissDrawable);
                }
            }

            this.actionButtonTextAppearance = attributes.getResourceId(R.styleable.BannerView_bannerActionButtonTextAppearance, -1);

            int textAppearance = attributes.getResourceId(R.styleable.BannerView_bannerTextAppearance, -1);
            applyTextStyle(context, messageTextView, textAppearance);

            attributes.recycle();
        }
    }



    @Override
    public void setText(CharSequence text) {
        messageTextView.setText(text);
    }

    @Override
    public void setNotificationActionButtonGroup(NotificationActionButtonGroup group) {
        actionButtonViewGroup.removeAllViewsInLayout();

        if (group == null) {
            actionButtonViewGroup.setVisibility(View.GONE);
            actionsDividerView.setVisibility(View.GONE);
            return;
        }

        actionButtonViewGroup.setVisibility(View.VISIBLE);
        actionsDividerView.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(context);
        Resources resources = context.getResources();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ACTION_BUTTON_ICON_SIZE_DP, resources.getDisplayMetrics());

        for (final NotificationActionButton actionButton : group.getNotificationActionButtons()) {
            Button button = (Button) inflater.inflate(R.layout.ua_iam_button, actionButtonViewGroup, false);

            if (actionButton.getLabel() > 0) {
                button.setText(actionButton.getLabel());
            }

            Drawable drawable = resources.getDrawable(actionButton.getIcon());
            drawable.setBounds(0, 0, size, size);
            drawable.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
            button.setCompoundDrawables(drawable, null, null, null);

            applyTextStyle(context, button, actionButtonTextAppearance);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (actionClickListener != null) {
                        actionClickListener.onActionClick(actionButton);
                    }
                }
            });

            actionButtonViewGroup.addView(button);
        }
    }

    @Override
    public void setOnDismissClickListener(Banner.OnDismissClickListener listener) {
        dismissClickListener = listener;
    }

    @Override
    public void setOnActionClickListener(Banner.OnActionClickListener listener) {
        actionClickListener = listener;
    }

    @Override
    public void setSecondaryColor(int color) {
        secondaryColor = color;

        actionsDividerView.setBackgroundColor(secondaryColor);
        dismissButton.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
        messageTextView.setTextColor(secondaryColor);

        for (int i = 0; i < actionButtonViewGroup.getChildCount(); i++) {
            View child = actionButtonViewGroup.getChildAt(i);
            if (child instanceof Button) {
                Button button = (Button) child;
                for (Drawable drawable : button.getCompoundDrawables()) {
                    if (drawable != null) {
                        drawable.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
                    }
                }
                button.setTextColor(secondaryColor);
            }
        }
    }

    @Override
    public void setPrimaryColor(int color) {
        primaryColor = color;
    }

    /**
     * Returns the primary color.
     * @return The primary color.
     */
    int getPrimaryColor() {
        return primaryColor;
    }


    /**
     * Helper method to apply custom text view styles.
     *
     * secondaryColor and typeFace need to be set before calling this method.
     *
     * @param context The view's context.
     * @param textView The text view.
     * @param textAppearance Optional text appearance.
     */
    private void applyTextStyle(Context context, TextView textView, int textAppearance) {
        // Apply text appearance first before the color or type face.
        if (textAppearance != -1) {
            textView.setTextAppearance(context, textAppearance);
        }

        // Called after setting the text appearance so we can keep style defined in the text appearance
        if (typeface != null) {
            int style = -1;
            if (textView.getTypeface() != null) {
                style = textView.getTypeface().getStyle();
            }

            textView.setPaintFlags(textView.getPaintFlags() | Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

            if (style >= 0) {
                textView.setTypeface(typeface, style);
            } else {
                textView.setTypeface(typeface);
            }
        }

        // Called after setting the text appearance to override the color
        textView.setTextColor(secondaryColor);
    }
}