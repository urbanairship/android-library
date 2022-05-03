/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Checkable;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.ScoreModel;
import com.urbanairship.android.layout.property.ScoreStyle;
import com.urbanairship.android.layout.util.ConstraintSetBuilder;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ShapeButton;
import com.urbanairship.util.UAStringUtil;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

/**
 * Form input that presents a set of numeric options representing a score.
 */
public class ScoreView extends ConstraintLayout implements BaseView<ScoreModel> {
    private ScoreModel model;

    @Nullable
    private Integer selectedScore = null;

    private final SparseIntArray scoreToViewIds = new SparseIntArray();

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
    }

    @NonNull
    public static ScoreView create(@NonNull Context context, @NonNull ScoreModel model, Environment environment) {
        ScoreView view = new ScoreView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull ScoreModel model, @NonNull Environment environment) {
        this.model = model;

        setId(model.getViewId());
        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(this, model);

        ConstraintSetBuilder constraints = ConstraintSetBuilder.newBuilder(getContext());

        ScoreStyle style = model.getStyle();
        switch (style.getType()) {
            case NUMBER_RANGE:
                configureNumberRange((ScoreStyle.NumberRange) style, constraints);
                break;
        }

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }

        constraints.build().applyTo(this);

        // Restore state from the model, if we have any.
        if (model.getSelectedScore() != null) {
            setSelectedScore(model.getSelectedScore());
        }

        model.onConfigured();
        LayoutUtils.doOnAttachToWindow(this, model::onAttachedToWindow);
    }

    private void configureNumberRange(@NonNull ScoreStyle.NumberRange style, @NonNull ConstraintSetBuilder constraints) {
        ScoreStyle.Bindings bindings = style.getBindings();
        int start = style.getStart();
        int end = style.getEnd();
        int[] viewIds = new int[(end - start) + 1];
        for (int i = start; i <= end; i++) {
            int score = i;
            ShapeButton button = new ShapeButton(
                getContext(),
                bindings.getSelected().getShapes(),
                bindings.getUnselected().getShapes(),
                String.valueOf(score),
                bindings.getSelected().getTextAppearance(),
                bindings.getUnselected().getTextAppearance()
            ) {
                @Override
                public void toggle() {
                    // No-op. Checked state is updated by the click listener.
                }
            };

            int viewId = generateViewId();
            button.setId(viewId);
            viewIds[i - start] = viewId;

            scoreToViewIds.append(score, viewId);

            button.setOnClickListener(v -> onScoreClick(v, score));

            constraints.squareAspectRatio(viewId);
            constraints.minHeight(viewId, 16);
            addView(button, new LayoutParams(LayoutParams.MATCH_CONSTRAINT, LayoutParams.MATCH_CONSTRAINT));
        }

        constraints.setHorizontalChainStyle(viewIds, ConstraintSet.CHAIN_PACKED)
                .createHorizontalChainInParent(viewIds, 0, style.getSpacing());
    }

    private void setSelectedScore(int score) {
        selectedScore = score;
        int viewId = scoreToViewIds.get(score, -1);
        if (viewId > -1) {
            View view = findViewById(viewId);
            if (view instanceof Checkable) {
                ((Checkable) view).setChecked(true);
            }
        }
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
