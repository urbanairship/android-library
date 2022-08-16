/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.content.res.ColorStateList;

import com.urbanairship.UAirship;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.ButtonModel;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.util.ColorStateListBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

public class ImageButtonView extends AppCompatImageButton implements BaseView {
    private final ImageButtonModel model;
    private final ViewEnvironment viewEnvironment;

    public ImageButtonView(
        @NonNull Context context,
        @NonNull ImageButtonModel model,
        @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context);
        this.model = model;
        this.viewEnvironment = viewEnvironment;

        setId(model.getViewId());

        configure();
    }

    private void configure() {
        setBackgroundDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ua_layout_imagebutton_ripple));
        setClickable(true);
        setFocusable(true);
        setPadding(0,0,0,0);

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
                String cachedImage = viewEnvironment.imageCache().get(url);
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
