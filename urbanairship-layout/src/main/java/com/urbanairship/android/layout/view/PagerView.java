/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.PagerRecyclerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerView extends FrameLayout implements BaseView<PagerModel> {
    private PagerModel model;
    private Environment environment;

    private PagerRecyclerView view = null;

    public PagerView(@NonNull Context context) {
        super(context);
        init();
    }

    public PagerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PagerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
    }

    @NonNull
    public static PagerView create(@NonNull Context context, @NonNull PagerModel model, @NonNull Environment environment) {
        PagerView view = new PagerView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull PagerModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;
        setId(model.getViewId());

        configure();
    }

    private void configure() {
        view = new PagerRecyclerView(getContext());
        view.configure(model, environment);
        addView(view, MATCH_PARENT, MATCH_PARENT);

        LayoutUtils.applyBorderAndBackground(this, model);

        model.setListener(modelListener);

        // Emit an init event so that we can connect to the indicator view, if one exists.
        model.onConfigured(view.getDisplayedItemPosition(), environment.displayTimer().getTime());

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
