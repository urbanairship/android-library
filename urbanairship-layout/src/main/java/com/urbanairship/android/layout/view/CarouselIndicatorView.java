/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.model.CarouselIndicatorModel;
import com.urbanairship.android.layout.widget.PagingIndicatorLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CarouselIndicatorView extends PagingIndicatorLayout implements BaseView<CarouselIndicatorModel> {
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
       model.setListener(listener);
    }

    //
    // PagingIndicatorLayout overrides
    //

    @Override
    protected void onIndicatorClick(int position) {
        model.onIndicatorClick(position);
    }

    //
    // Model Listener
    //

    private final CarouselIndicatorModel.Listener listener = new CarouselIndicatorModel.Listener() {
        @Override
        public void onInit(int size, int position) {
            setCount(size);
            setPosition(position);
        }

        @Override
        public void onUpdate(int position) {
            setPosition(position);
        }
    };
}
