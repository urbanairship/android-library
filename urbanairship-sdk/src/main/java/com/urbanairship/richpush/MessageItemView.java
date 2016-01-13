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
package com.urbanairship.richpush;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.urbanairship.R;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.ViewUtils;

/**
 * Message Center item view.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class MessageItemView extends FrameLayout {

    private static final int[] STATE_HIGHLIGHTED = { R.attr.ua_state_highlighted };

    private TextView titleView;
    private TextView dateView;
    private ImageView iconView;
    private CheckBox checkBox;

    private boolean isHighlighted;
    private OnClickListener selectionListener;
    private Typeface customTypeface;

    private Typeface titleTypeface;
    private Typeface titleReadTypeface;


    public MessageItemView(Context context) {
        this(context, null, R.attr.messageCenterStyle);
    }

    public MessageItemView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.messageCenterStyle);
    }

    public MessageItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, R.style.MessageCenter);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MessageItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Initializes the view.
     *
     * @param context The Context the view is running in, through which it can
     * access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyleAttr An attribute in the current theme that contains a
     * reference to a style resource that supplies default values for
     * the view. Can be 0 to not look for defaults.
     * @param defStyleRes A resource identifier of a style resource that
     * supplies default values for the view, used only if
     * defStyleAttr is 0 or can not be found in the theme. Can be 0
     * to not look for defaults.
     */
    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        int contentLayout = R.layout.ua_item_mc_content;
        int dateTextAppearance;
        int titleTextAppearance;

        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.MessageCenter, defStyleAttr, defStyleRes);

        String fontPath = attributes.getString(R.styleable.BannerView_bannerFontPath);
        if (!UAStringUtil.isEmpty(fontPath)) {
            customTypeface = Typeface.createFromAsset(context.getAssets(), fontPath);
        }

        if (attributes.getBoolean(R.styleable.MessageCenter_messageCenterItemIconEnabled, false)) {
            contentLayout = R.layout.ua_item_mc_icon_content;
        }

        dateTextAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemDateTextAppearance, -1);
        titleTextAppearance = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemTitleTextAppearance, -1);

        int background = attributes.getResourceId(R.styleable.MessageCenter_messageCenterItemBackground, -1);
        if (background > 0) {
            setBackgroundResource(background);
        }

        attributes.recycle();

        View contentView = View.inflate(context, contentLayout, this);

        titleView = (TextView) contentView.findViewById(R.id.title);
        ViewUtils.applyTextStyle(context, titleView, titleTextAppearance, customTypeface);
        if (titleView.getTypeface() != null) {
            titleReadTypeface = titleView.getTypeface();
            titleTypeface = Typeface.create(titleView.getTypeface(), titleView.getTypeface().getStyle() | Typeface.BOLD);
        } else {
            titleReadTypeface = Typeface.DEFAULT;
            titleTypeface = Typeface.DEFAULT_BOLD;
        }

        dateView = (TextView) contentView.findViewById(R.id.date);
        ViewUtils.applyTextStyle(context, dateView, dateTextAppearance, customTypeface);

        iconView = (ImageView) contentView.findViewById(R.id.image);
        if (iconView != null) {
            iconView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectionListener != null) {
                        selectionListener.onClick(MessageItemView.this);
                    }
                }
            });
        }

        checkBox = (CheckBox) contentView.findViewById(R.id.checkbox);
        if (checkBox != null) {
            checkBox.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectionListener != null) {
                        selectionListener.onClick(MessageItemView.this);
                    }
                }
            });
        }

    }

    /**
     * Updates the view's message.
     *
     * @param message The message.
     * @param placeholder Image place holder.
     * @param imageLoader An {@link ImageLoader} to load the icon view.
     */
    void updateMessage(RichPushMessage message, @DrawableRes int placeholder, ImageLoader imageLoader) {
        titleView.setText(message.getTitle());
        dateView.setText(DateFormat.getDateFormat(getContext()).format(message.getSentDate()));

        if (message.isRead()) {
            titleView.setTypeface(titleReadTypeface);
        } else {
            titleView.setTypeface(titleTypeface);
        }

        if (checkBox != null) {
            checkBox.setChecked(isActivated());
        }

        if (iconView != null) {
            imageLoader.load(message.getListIconUrl(), placeholder, iconView);
        }
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        if (checkBox != null) {
            checkBox.setChecked(activated);
        }
    }

    /**
     * Sets the highlight state item.
     *
     * @param isHighlighted {@code true} to highlight the view, otherwise {@code false}.
     */
    void setHighlighted(boolean isHighlighted) {
        if (this.isHighlighted != isHighlighted) {
            this.isHighlighted = isHighlighted;
            refreshDrawableState();
        }
    }

    /**
     * Sets the selection listener when the item's icon or checkbox is tapped.
     *
     * @param listener A click listener.
     */
    void setSelectionListener(View.OnClickListener listener) {
        this.selectionListener = listener;
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (isHighlighted) {
            final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
            mergeDrawableStates(drawableState, STATE_HIGHLIGHTED);
            return drawableState;
        } else {
            return super.onCreateDrawableState(extraSpace);
        }
    }


}
