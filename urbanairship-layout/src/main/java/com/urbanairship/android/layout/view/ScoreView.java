/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import com.urbanairship.android.layout.model.ScoreModel;
import com.urbanairship.android.layout.property.ScoreStyle;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ShapeButton;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Form input that presents a set of numeric options representing a score.
 */
public class ScoreView extends ConstraintLayout implements BaseView<ScoreModel> {
    private static final int NPS_SCORE_ITEMS = 10;

    private ScoreModel model;

    @Nullable
    private Integer selectedScore = null;

    public ScoreView(Context context) {
        super(context);
        init();
    }

    public ScoreView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScoreView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static ScoreView create(@NonNull Context context, @NonNull ScoreModel model) {
        ScoreView view = new ScoreView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull ScoreModel model) {
        this.model = model;
        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(getRootView(),model);

        ConstraintSetBuilder constraints = ConstraintSetBuilder.newBuilder(getContext());

        ScoreStyle style = model.getStyle();
        switch (style.getType()) {
            case NPS:
                configureNpsScore(style, constraints);
                break;
        }

        constraints.build().applyTo(this);
        model.onInit();
    }

    private void configureNpsScore(@NonNull ScoreStyle style, @NonNull ConstraintSetBuilder constraints) {
        ScoreStyle.Bindings bindings = style.getBindings();

        int[] viewIds = new int[NPS_SCORE_ITEMS + 1];
        for (int i = 0; i <= NPS_SCORE_ITEMS; i++) {
            int score = i;
            ShapeButton button = new ShapeButton(
                getContext(),
                bindings.getSelected().getShapes(),
                bindings.getUnselected().getShapes(),
                String.valueOf(score),
                bindings.getSelected().getTextAppearance(),
                bindings.getUnselected().getTextAppearance()
            );

            int viewId = button.getId();
            viewIds[i] = viewId;

            button.setOnClickListener(v -> onScoreClick(v, score));

            constraints.squareAspectRatio(viewId);
            constraints.minHeight(viewId, 28);
            addView(button, new LayoutParams(LayoutParams.MATCH_CONSTRAINT, LayoutParams.MATCH_CONSTRAINT));
        }

        constraints.createHorizontalChainInParent(viewIds, 0, style.getSpacing());
    }

    private void onScoreClick(@NonNull View view, int score) {
        if (Objects.equals(score, selectedScore)) {
            return;
        }
        selectedScore = score;

        // Uncheck other items in the view
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof Checkable) {
                ((Checkable) child).setChecked(view.getId() == child.getId());
            }
        }
        // Notify our model
        model.onScoreChange(score);
    }
}
