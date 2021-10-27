/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.property.ButtonBehavior;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.json.JsonMap;

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
        String id = model.getId();
        String url = model.getUrl();
        ButtonBehavior behavior = model.getBehavior();
        JsonMap actions = model.getActions();

        ImageRequestOptions options = ImageRequestOptions.newBuilder(url).build();
        UAirship.shared().getImageLoader().load(getContext(), this, options);

        // TODO: Wire up click listener to do stuff based on behavior/actions
    }
}
