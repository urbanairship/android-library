/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push.iam.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.ViewUtils;

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
    private final TextView messageTextView;
    private final View actionsDividerView;
    private final ImageButton dismissButton;
    private final ViewGroup actionButtonViewGroup;
    private int primaryColor;
    private int secondaryColor;
    private int actionButtonTextAppearance;

    private Typeface buttonTypeface;


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

            Typeface bannerTypeface = null;
            String fontPath = attributes.getString(R.styleable.BannerView_bannerFontPath);
            if (!UAStringUtil.isEmpty(fontPath)) {
                try {
                    bannerTypeface = Typeface.createFromAsset(context.getAssets(), fontPath);
                } catch (RuntimeException e) {
                    Logger.error("Failed to load font path: " + fontPath);
                }
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
            buttonTypeface = ViewUtils.createTypeface(context, actionButtonTextAppearance);
            if (buttonTypeface == null) {
                buttonTypeface = bannerTypeface;
            }

            int textAppearance = attributes.getResourceId(R.styleable.BannerView_bannerTextAppearance, -1);
            Typeface messageTypeface = ViewUtils.createTypeface(context, textAppearance);
            if (messageTypeface == null) {
                messageTypeface = bannerTypeface;
            }
            applyTextStyle(context, messageTextView, textAppearance, messageTypeface);

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

            if (actionButton.getIcon() > 0) {
                Drawable drawable = ContextCompat.getDrawable(context, actionButton.getIcon());
                drawable.setBounds(0, 0, size, size);
                drawable.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
                button.setCompoundDrawables(drawable, null, null, null);
            }

            applyTextStyle(context, button, actionButtonTextAppearance, buttonTypeface);

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
     * secondaryColor and typeface need to be set before calling this method.
     *
     * @param context The view's context.
     * @param textView The text view.
     * @param textAppearance Optional text appearance.
     * @param typeface Optional typeface.
     */
    private void applyTextStyle(Context context, TextView textView, int textAppearance, Typeface typeface) {
        ViewUtils.applyTextStyle(context, textView, textAppearance, typeface);

        // Called after setting the text appearance to override the color
        textView.setTextColor(secondaryColor);
    }
}
