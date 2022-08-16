/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.view.Gravity;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.info.PagerIndicatorInfo;
import com.urbanairship.android.layout.model.PagerIndicatorModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.android.layout.widget.ShapeView;

import androidx.annotation.NonNull;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class PagerIndicatorView extends LinearLayout implements BaseView {
    private final PagerIndicatorModel model;

    public PagerIndicatorView(
            @NonNull Context context,
            @NonNull PagerIndicatorModel model,
            @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context);
        this.model = model;

        setId(generateViewId());

        configure();
    }

    private void configure() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        model.setListener(listener);

        LayoutUtils.applyBorderAndBackground(this, model);

        model.onConfigured();
    }

    //
    // Model Listener
    //

    private final PagerIndicatorModel.Listener listener = new PagerIndicatorModel.Listener() {
        private boolean isInitialized = false;

        @Override
        public void onInit(int size, int position) {
            if (!isInitialized) {
                isInitialized = true;
                setCount(size);
            }
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

        PagerIndicatorInfo.Bindings bindings = model.getBindings();
        PagerIndicatorInfo.Binding checked = bindings.getSelected();
        PagerIndicatorInfo.Binding unchecked = bindings.getUnselected();

        int spacing = (int) ResourceUtils.dpToPx(context, model.getIndicatorSpacing());
        int halfSpacing = (int) (spacing / 2f);

        for (int i = 0; i < count; i++) {
            ImageView view = new ShapeView(
                getContext(),
                checked.getShapes(),
                unchecked.getShapes(),
                checked.getIcon(),
                unchecked.getIcon());

            view.setId(model.getIndicatorViewId(i));
            view.setAdjustViewBounds(true);

            LayoutParams lp = new LayoutParams(WRAP_CONTENT, MATCH_PARENT);
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
