/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.urbanairship.R;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayContent;

import java.util.List;

/**
 * In-app button layout. Supports stacked, separated, and joined button layouts.
 */
public class InAppButtonLayout extends LinearLayout {

    private int stackedSpaceHeight;
    private int separatedSpaceWidth;

    /**
     * Button click listener.
     */
    public interface ButtonClickListener {

        /**
         * Called when a button is clicked.
         *
         * @param view The button's view.
         * @param buttonInfo The button info.
         */
        void onButtonClicked(View view, ButtonInfo buttonInfo);
    }

    private ButtonClickListener buttonClickListener;

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public InAppButtonLayout(Context context) {
        this(context, null);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public InAppButtonLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public InAppButtonLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle, 0);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    public InAppButtonLayout(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
        init(context, attrs, defStyle, defResStyle);
    }

    /**
     * Initializes the view.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    private void init(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        if (attrs != null) {
            TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.UrbanAirshipInAppButtonLayout, defStyle, defResStyle);
            stackedSpaceHeight = attributes.getDimensionPixelSize(R.styleable.UrbanAirshipInAppButtonLayout_buttonStackedSpaceHeight, 0);
            separatedSpaceWidth = attributes.getDimensionPixelSize(R.styleable.UrbanAirshipInAppButtonLayout_buttonSeparatedSpaceWidth, 0);
        }
    }

    /**
     * Sets the button click listener.
     *
     * @param buttonClickListener The button click listener.
     */
    public void setButtonClickListener(ButtonClickListener buttonClickListener) {
        this.buttonClickListener = buttonClickListener;
    }

    /**
     * Sets the buttons.
     *
     * @param layout The button layout.
     * @param buttonInfos The list of button infos.
     */
    public void setButtons(@DisplayContent.ButtonLayout String layout, final List<ButtonInfo> buttonInfos) {

        boolean isStacked = DisplayContent.BUTTON_LAYOUT_STACKED.equals(layout);
        boolean isJoined = !isStacked && DisplayContent.BUTTON_LAYOUT_JOINED.equals(layout);

        removeAllViews();
        setOrientation(isStacked ? VERTICAL : HORIZONTAL);
        setMeasureWithLargestChildEnabled(true);

        for (int i = 0; i < buttonInfos.size(); i++) {
            @InAppButton.BorderRadiusFlag int radiusFlag = 0;
            final ButtonInfo buttonInfo = buttonInfos.get(i);

            if (isJoined) {
                if (i == 0) {
                    radiusFlag = InAppButton.BORDER_RADIUS_BOTTOM_LEFT | InAppButton.BORDER_RADIUS_TOP_LEFT;
                } else if (i == buttonInfos.size() - 1) {
                    radiusFlag = InAppButton.BORDER_RADIUS_TOP_RIGHT | InAppButton.BORDER_RADIUS_BOTTOM_RIGHT;
                }
            } else {
                radiusFlag = InAppButton.BORDER_RADIUS_ALL;
            }

            final InAppButton button = new InAppButton(getContext(), null, R.style.UrbanAirship_InAppBanner_Button);
            button.setButtonInfo(buttonInfo, radiusFlag);
            button.setGravity(Gravity.CENTER_VERTICAL);

            if (isStacked) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
                params.weight = 1;
                button.setLayoutParams(params);

                if (i > 0) {
                    params.setMargins(0, stackedSpaceHeight, 0, 0);
                }
            } else {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
                params.weight = 1;
                button.setLayoutParams(params);

                if (!isJoined && i > 0) {
                    params.setMargins(separatedSpaceWidth, 0, 0, 0);
                    params.setMarginStart(separatedSpaceWidth);
                }
            }

            addView(button);

            button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (buttonClickListener != null) {
                        buttonClickListener.onButtonClicked(view, buttonInfo);
                    }
                }
            });
        }

        requestLayout();
    }
}
