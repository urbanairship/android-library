/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.PagerRecyclerView;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerView extends FrameLayout implements BaseView {
    private final PagerModel model;
    private final ViewEnvironment viewEnvironment;
    private final PagerRecyclerView view;

    public PagerView(
            @NonNull Context context,
            @NonNull PagerModel model,
            @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context);
        this.model = model;
        this.viewEnvironment = viewEnvironment;

        setId(model.getViewId());

        this.view = new PagerRecyclerView(context, model, viewEnvironment);
        addView(view, MATCH_PARENT, MATCH_PARENT);

        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(this, model);

        model.setListener(modelListener);

        // Emit an init event so that we can connect to the indicator view, if one exists.
        model.onConfigured(view.getDisplayedItemPosition(), viewEnvironment.displayTimer().getTime());

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) ->
                ViewCompat.dispatchApplyWindowInsets(view, insets)
        );
    }

    private final PagerModel.Listener modelListener = new PagerModel.Listener() {
        @Override
        public void onScrollToNext() {
            int position = view.getDisplayedItemPosition();
            int nextPosition = position + 1;

            if (position != NO_POSITION && nextPosition < view.getAdapterItemCount()) {
                view.scrollTo(nextPosition);
            }
        }

        @Override
        public void onScrollToPrevious() {
            int position = view.getDisplayedItemPosition();
            int previousPosition = position - 1;

            if (position != NO_POSITION && previousPosition > -1) {
                view.scrollTo(previousPosition);
            }
        }
    };
}
