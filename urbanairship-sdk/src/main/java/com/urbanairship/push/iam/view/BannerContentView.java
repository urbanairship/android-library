/* Copyright 2017 Urban Airship and Contributors */

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
import android.widget.LinearLayout;
import android.widget.TextView;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.ViewUtils;


/**
 * Banner Content View.
 */
public class BannerContentView extends LinearLayout implements Banner {

    /**
     * The size of action button icons.
     */
    private static final int ACTION_BUTTON_ICON_SIZE_DP = 32;

    private TextView messageTextView;
    private View actionsDividerView;
    private ImageButton dismissButton;
    private ViewGroup actionButtonViewGroup;

    private int primaryColor;
    private int secondaryColor;

    private final Typeface messageTypeFace;
    private final int messageTextAppearance;

    private final Typeface actionButtonTypeFace;
    private final int actionButtonTextAppearance;

    private final boolean hideDismissButton;
    private final Drawable dismissDrawable;

    private NotificationActionButtonGroup actionButtonGroup;

    private Banner.OnDismissClickListener dismissClickListener;
    private Banner.OnActionClickListener actionClickListener;


    /**
     * Construct a banner content view.
     *
     * @param context A Context object used to access application assets.
     */
    public BannerContentView(Context context) {
        this(context, null, R.attr.inAppMessageBannerStyle);
    }

    /**
     * Construct a banner content view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs The view's attributes.
     */
    public BannerContentView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.inAppMessageBannerStyle);
    }

    /**
     * Construct a banner content view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs The view's attributes.
     * @param defStyleAttr The default style attribute resource ID.
     */
    public BannerContentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        int defaultPrimary = ContextCompat.getColor(context, R.color.ua_iam_primary);
        int defaultSecondary = ContextCompat.getColor(context, R.color.ua_iam_secondary);

        if (attrs != null) {
            TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.BannerView, defStyleAttr, R.style.InAppMessage_Banner);

            this.primaryColor = attributes.getColor(R.styleable.BannerView_bannerPrimaryColor, defaultPrimary);
            this.secondaryColor = attributes.getColor(R.styleable.BannerView_bannerSecondaryColor, defaultSecondary);
            this.hideDismissButton = attributes.getBoolean(R.styleable.BannerView_bannerNoDismissButton, false);
            this.dismissDrawable = attributes.getDrawable(R.styleable.BannerView_bannerDismissButtonDrawable);

            this.actionButtonTextAppearance = attributes.getResourceId(R.styleable.BannerView_bannerActionButtonTextAppearance, 0);
            this.messageTextAppearance = attributes.getResourceId(R.styleable.BannerView_bannerTextAppearance, 0);


            Typeface actionButtonTextFace = ViewUtils.createTypeface(context, this.actionButtonTextAppearance);
            Typeface messageTypeFace = ViewUtils.createTypeface(context, this.messageTextAppearance);

            Typeface bannerTypeface = null;
            if (actionButtonTextFace == null && messageTypeFace == null) {
                String fontPath = attributes.getString(R.styleable.BannerView_bannerFontPath);
                if (!UAStringUtil.isEmpty(fontPath)) {
                    try {
                        bannerTypeface = Typeface.createFromAsset(context.getAssets(), fontPath);
                    } catch (RuntimeException e) {
                        Logger.error("Failed to load font path: " + fontPath);
                    }
                }
            }

            this.actionButtonTypeFace = actionButtonTextFace == null ? bannerTypeface : actionButtonTextFace;
            this.messageTypeFace = messageTypeFace == null ? bannerTypeface : messageTypeFace;

            attributes.recycle();
        } else {
            this.primaryColor = defaultPrimary;
            this.secondaryColor = defaultSecondary;
            this.messageTypeFace = null;
            this.messageTextAppearance = 0;
            this.actionButtonTypeFace = null;
            this.actionButtonTextAppearance = 0;
            this.hideDismissButton = false;
            this.dismissDrawable = null;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        this.messageTextView = (TextView) findViewById(R.id.alert);
        this.actionsDividerView = findViewById(R.id.action_divider);
        this.actionButtonViewGroup = (ViewGroup) findViewById(R.id.action_buttons);
        this.dismissButton = (ImageButton) findViewById(R.id.close);

        if (messageTextView != null) {
            applyTextStyle(messageTextView, messageTextAppearance, messageTypeFace);
        }

        if (dismissButton != null) {
            if (hideDismissButton) {
                dismissButton.setVisibility(View.GONE);
            } else {
                dismissButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (dismissClickListener != null) {
                            dismissClickListener.onDismissClick();
                        }
                    }
                });

                if (dismissDrawable != null) {
                    dismissButton.setImageDrawable(dismissDrawable);
                }
            }
        }

        setNotificationActionButtonGroup(actionButtonGroup);
        updateColors();
    }

    @Override
    public void setText(CharSequence text) {
        messageTextView.setText(text);
    }

    @Override
    public void setNotificationActionButtonGroup(NotificationActionButtonGroup group) {
        this.actionButtonGroup = group;

        if (actionButtonViewGroup == null) {
            return;
        }

        actionButtonViewGroup.removeAllViewsInLayout();

        actionButtonViewGroup.setVisibility(group == null ? View.GONE : View.VISIBLE);

        if (actionsDividerView != null) {
            actionsDividerView.setVisibility(group == null ? View.GONE : View.VISIBLE);
        }

        if (group == null)  {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        Resources resources = getContext().getResources();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ACTION_BUTTON_ICON_SIZE_DP, resources.getDisplayMetrics());

        for (final NotificationActionButton actionButton : group.getNotificationActionButtons()) {
            Button button = (Button) inflater.inflate(R.layout.ua_iam_button, actionButtonViewGroup, false);

            if (actionButton.getLabel() > 0) {
                button.setText(actionButton.getLabel());
            }

            if (actionButton.getIcon() > 0) {
                Drawable drawable = ContextCompat.getDrawable(getContext(), actionButton.getIcon());
                drawable.setBounds(0, 0, size, size);
                drawable.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
                button.setCompoundDrawables(drawable, null, null, null);
            }

            applyTextStyle(button, actionButtonTextAppearance, actionButtonTypeFace);

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
        updateColors();
    }

    @Override
    public void setPrimaryColor(int color) {
        primaryColor = color;
    }

    /**
     * Returns the primary color.
     *
     * @return The primary color.
     */
    int getPrimaryColor() {
        return primaryColor;
    }

    private void updateColors() {
        if (actionsDividerView != null) {
            actionsDividerView.setBackgroundColor(secondaryColor);
        }

        if (dismissButton != null && !hideDismissButton) {
            dismissButton.setColorFilter(secondaryColor, PorterDuff.Mode.MULTIPLY);
        }

        if (messageTextView != null) {
            messageTextView.setTextColor(secondaryColor);
        }


        if (actionButtonViewGroup != null) {
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
    }

    /**
     * Helper method to apply custom text view styles.
     * <p>
     * secondaryColor and typeface need to be set before calling this method.
     *
     * @param textView The text view.
     * @param textAppearance Optional text appearance.
     * @param typeface Optional typeface.
     */
    private void applyTextStyle(TextView textView, int textAppearance, Typeface typeface) {
        ViewUtils.applyTextStyle(getContext(), textView, textAppearance, typeface);

        // Called after setting the text appearance to override the color
        textView.setTextColor(secondaryColor);
    }
}
