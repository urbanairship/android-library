/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.model.CarouselIndicatorModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CarouselIndicatorView extends View implements BaseView<CarouselIndicatorModel> {
    private CarouselIndicatorModel model;

    public CarouselIndicatorView(@NonNull Context context) {
        super(context);
        init();
    }

    public CarouselIndicatorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CarouselIndicatorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static CarouselIndicatorView create(@NonNull Context context, @NonNull CarouselIndicatorModel model) {
        CarouselIndicatorView view = new CarouselIndicatorView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull CarouselIndicatorModel model) {
        this.model = model;
        configure();
    }

    private void configure() {
        // TODO: draw some dots
    }
}
