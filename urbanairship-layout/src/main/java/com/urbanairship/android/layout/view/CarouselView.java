/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.model.CarouselModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

public class CarouselView extends RecyclerView implements BaseView<CarouselModel> {
    private CarouselModel model;
    private CarouselAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;

    public CarouselView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CarouselView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CarouselView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        setId(generateViewId());

        layoutManager = new LinearLayoutManager(context, HORIZONTAL, false);
        adapter = new CarouselAdapter();
        snapHelper = new PagerSnapHelper();

        setHorizontalScrollBarEnabled(false);

        setLayoutManager(layoutManager);
        setAdapter(adapter);
        snapHelper.attachToRecyclerView(this);
    }

    @NonNull
    public static CarouselView create(@NonNull Context context, @NonNull CarouselModel model) {
        CarouselView view = new CarouselView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull CarouselModel model) {
        this.model = model;
        configure();
    }

    private void configure() {
        adapter.setItems(model.getItems());
    }
}
