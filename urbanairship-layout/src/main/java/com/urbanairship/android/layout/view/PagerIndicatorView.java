/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Checkable;
import android.widget.LinearLayout;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.model.PagerIndicatorModel;
import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.android.layout.widget.ShapeImageButton;

import androidx.annotation.NonNull;

public class PagerIndicatorView extends LinearLayout implements BaseView<PagerIndicatorModel> {
    private PagerIndicatorModel model;

    public PagerIndicatorView(@NonNull Context context) {
        super(context);

        setId(generateViewId());
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static PagerIndicatorView create(@NonNull Context context, @NonNull PagerIndicatorModel model) {
        PagerIndicatorView view = new PagerIndicatorView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull PagerIndicatorModel model) {
        this.model = model;
        Logger.debug("set model!");
        configure();
    }

    private void configure() {
        model.setListener(listener);

        LayoutUtils.applyBorderAndBackground(this, model);
    }

    //
    // Model Listener
    //

    private final PagerIndicatorModel.Listener listener = new PagerIndicatorModel.Listener() {
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

    /**
     * Sets the number of indicator dots to be displayed.
     *
     * @param count The number of dots to display.
     */
    public void setCount(int count) {
        Context context = getContext();
        Logger.debug("setCount: %s", count);
        if (getChildCount() > 0) {
            removeAllViews();
        }

        PagerIndicatorModel.Bindings bindings = model.getBindings();
        Shape checked = bindings.getSelected();
        Shape unchecked = bindings.getDeselected();

        int spacing = (int) ResourceUtils.dpToPx(context, model.getIndicatorSpacing());
        int halfSpacing = (int) (spacing / 2f);
        int maxWidth = (int) ResourceUtils.dpToPx(context, Math.max(checked.getWidth(), unchecked.getWidth()));
        int maxHeight = (int) ResourceUtils.dpToPx(context, Math.max(checked.getHeight(), unchecked.getHeight()));

        for (int i = 0; i < count; i++) {
            View view = new ShapeImageButton(getContext(), bindings.getSelected(), bindings.getDeselected());

            LayoutParams lp = new LayoutParams(maxWidth, maxHeight);
            lp.setMarginStart(i == 0 ? spacing : halfSpacing);
            lp.setMarginEnd(i == count - 1 ? spacing : halfSpacing);

            addView(view, lp);
        }
    }

    /**
     * Updates the highlighted dot view in the indicator.
     *
     * @param position The position of the dot to highlight.
     */
    public void setPosition(int position) {
        for (int i = 0; i < getChildCount(); i++) {
            ((Checkable) getChildAt(i)).setChecked(i == position);
        }
    }
}
