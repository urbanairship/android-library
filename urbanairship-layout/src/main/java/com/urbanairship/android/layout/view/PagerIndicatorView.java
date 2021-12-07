/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.view.Gravity;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.PagerIndicatorModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.android.layout.widget.ShapeView;

import androidx.annotation.NonNull;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class PagerIndicatorView extends LinearLayout implements BaseView<PagerIndicatorModel> {
    private PagerIndicatorModel model;

    public PagerIndicatorView(@NonNull Context context) {
        super(context);

        setId(generateViewId());
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
    }

    @NonNull
    public static PagerIndicatorView create(
        @NonNull Context context,
        @NonNull PagerIndicatorModel model,
        @NonNull Environment environment
    ) {
        PagerIndicatorView view = new PagerIndicatorView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull PagerIndicatorModel model, @NonNull Environment environment) {
        this.model = model;
        configure();
    }

    private void configure() {
        model.setListener(listener);

        LayoutUtils.applyBorderAndBackground(this, model);

        model.onConfigured();
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
        PagerIndicatorModel.Binding checked = bindings.getSelected();
        PagerIndicatorModel.Binding unchecked = bindings.getUnselected();

        int spacing = (int) ResourceUtils.dpToPx(context, model.getIndicatorSpacing());
        int halfSpacing = (int) (spacing / 2f);

        for (int i = 0; i < count; i++) {
            // TODO: pass icon through and render it too

            ImageView view = new ShapeView(getContext(), checked.getShapes(), unchecked.getShapes());

            LayoutParams lp = new LayoutParams(WRAP_CONTENT, MATCH_PARENT);
            lp.setMarginStart(i == 0 ? spacing : halfSpacing);
            lp.setMarginEnd(i == count - 1 ? spacing : halfSpacing);

            view.setAdjustViewBounds(true);

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
