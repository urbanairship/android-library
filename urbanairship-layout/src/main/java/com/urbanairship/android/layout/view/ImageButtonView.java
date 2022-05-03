/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.util.ColorStateListBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

public class ImageButtonView extends AppCompatImageButton implements BaseView<ImageButtonModel> {
    private ImageButtonModel model;
    private Environment environment;

    public ImageButtonView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ImageButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ImageButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        Drawable ripple = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple);
        setBackgroundDrawable(ripple);
        setClickable(true);
        setFocusable(true);
        setPadding(0,0,0,0);
    }

    @NonNull
    public static ImageButtonView create(@NonNull Context context, @NonNull ImageButtonModel model, @NonNull Environment environment) {
        ImageButtonView view = new ImageButtonView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull ImageButtonModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;

        setId(model.getViewId());
        configureButton();
    }

    private void configureButton() {
        setScaleType(ScaleType.FIT_CENTER);
        LayoutUtils.applyBorderAndBackground(this, model);
        model.setViewListener(modelListener);


        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }

        Image image = model.getImage();
        switch (image.getType()) {
            case URL:
                String url = ((Image.Url) image).getUrl();
                String cachedImage = environment.imageCache().get(url);
                if (cachedImage != null) {
                    url = cachedImage;
                }
                UAirship.shared().getImageLoader()
                        .load(getContext(), this, ImageRequestOptions.newBuilder(url)
                                                                     .build());
                break;
            case ICON:
                Image.Icon icon = ((Image.Icon) image);
                setImageDrawable(icon.getDrawable(getContext()));

                @ColorInt int normalColor = icon.getTint().resolve(getContext());
                @ColorInt int pressedColor = LayoutUtils.generatePressedColor(normalColor);
                @ColorInt int disabledColor = LayoutUtils.generateDisabledColor(normalColor);

                ColorStateList tintList = new ColorStateListBuilder()
                    .add(pressedColor, android.R.attr.state_pressed)
                    .add(disabledColor, -android.R.attr.state_enabled)
                    .add(normalColor)
                    .build();

                setImageTintList(tintList);
                break;
        }

        setOnClickListener(v -> model.onClick());
    }

    private final ButtonModel.Listener modelListener = new ButtonModel.Listener() {
        @Override
        public void setEnabled(boolean isEnabled) {
            ImageButtonView.this.setEnabled(isEnabled);
        }
    };
}
