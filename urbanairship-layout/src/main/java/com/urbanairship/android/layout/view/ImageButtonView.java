/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.property.ButtonImage;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.images.ImageRequestOptions;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

public class ImageButtonView extends AppCompatImageButton implements BaseView<ImageButtonModel> {
    private ImageButtonModel model;

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
        setId(generateViewId());

        Drawable ripple = ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple);
        setBackgroundDrawable(ripple);
        setClickable(true);
        setFocusable(true);
    }

    @NonNull
    public static ImageButtonView create(@NonNull Context context, @NonNull ImageButtonModel model) {
        ImageButtonView view = new ImageButtonView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull ImageButtonModel model) {
        this.model = model;
        configureButton();
    }

    public void configureButton() {
        LayoutUtils.applyBorderAndBackground(this, model);

        ButtonImage image = model.getImage();
        switch (image.getType()) {
            case URL:
                String url = ((ButtonImage.Url) image).getUrl();
                UAirship.shared().getImageLoader()
                        .load(getContext(), this, ImageRequestOptions.newBuilder(url).build());
                break;
            case ICON:
                ButtonImage.Icon icon = ((ButtonImage.Icon) image);
                @DrawableRes int resId = icon.getDrawableRes();
                @ColorInt int tint = icon.getTint();

                setImageResource(resId);
                setImageTintList(ColorStateList.valueOf(tint));
                break;
        }

        setOnClickListener(v -> model.onClick());
    }
}
