/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.view.View;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.ScrollLayoutModel;
import com.urbanairship.android.layout.property.Direction;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

public class ScrollLayoutView extends NestedScrollView implements BaseView {
    private final ScrollLayoutModel model;
    private final ViewEnvironment viewEnvironment;

    public ScrollLayoutView(
            @NonNull Context context,
            @NonNull ScrollLayoutModel model,
            @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context);
        this.model = model;
        this.viewEnvironment = viewEnvironment;

        setId(model.getViewId());

        configure();
    }

    private void configure() {
        setFillViewport(false);
        LayoutUtils.applyBorderAndBackground(this, model);

        Direction direction = model.getDirection();
        View contentView = Thomas.view(getContext(), model.getView(), viewEnvironment);

        LayoutParams layoutParams;
        if (direction == Direction.VERTICAL) {
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else {
            layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        }

        setClipToOutline(true);
        contentView.setLayoutParams(layoutParams);
        addView(contentView);

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) ->
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        );
    }
}
