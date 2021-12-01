/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.urbanairship.android.layout.model.ScoreModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Form input that presents a set of numeric options representing a score.
 */
public class ScoreView extends LinearLayout implements BaseView<ScoreModel> {
    private ScoreModel model;

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

        // TODO: actually draw the score view

        // TODO: wire up score clicks to call: model.onScoreChange(score);

        model.onInit();
    }
}
