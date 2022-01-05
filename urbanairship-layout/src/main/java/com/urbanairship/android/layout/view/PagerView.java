/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerView extends RecyclerView implements BaseView<PagerModel> {
    private PagerModel model;
    private Environment environment;
    private PagerAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;

    private boolean isInternalScroll = false;

    public PagerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PagerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PagerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(@NonNull Context context) {
        setId(generateViewId());

        layoutManager = new LinearLayoutManager(context, HORIZONTAL, false);
        // Disable prefetch so we won't get display events from items that aren't yet visible.
        layoutManager.setItemPrefetchEnabled(false);
        setLayoutManager(layoutManager);

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(this);

        setHorizontalScrollBarEnabled(false);
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
        configure();
    }

    private void configure() {
        adapter = new PagerAdapter(environment);
        setAdapter(adapter);

        LayoutUtils.applyBorderAndBackground(this, model);

        adapter.setItems(model.getChildren());
        addOnScrollListener(onScrollListener);

        model.setListener(modelListener);

        // Emit an init event so that we can connect to the indicator view, if one exists.
        model.onConfigured(getDisplayedItemPosition(), environment.displayTimer().getTime());
    }

    private int getDisplayedItemPosition() {
        View snapView = snapHelper.findSnapView(layoutManager);
        return snapView != null ? getChildAdapterPosition(snapView) : 0;
    }

    @Override
    public void smoothScrollToPosition(int position) {
        isInternalScroll = true;
        super.smoothScrollToPosition(position);
    }

    private final PagerModel.Listener modelListener = new PagerModel.Listener() {
        @Override
        public void onScrollToNext() {
            int position = getDisplayedItemPosition();
            int nextPosition = position + 1;

            if (position != NO_POSITION && nextPosition < adapter.getItemCount()) {
                smoothScrollToPosition(nextPosition);
            }
        }

        @Override
        public void onScrollToPrevious() {
            int position = getDisplayedItemPosition();
            int previousPosition = position - 1;

            if (position != NO_POSITION && previousPosition > -1) {
                smoothScrollToPosition(previousPosition);
            }
        }
    };

    private final OnScrollListener onScrollListener = new OnScrollListener() {
        private int previousPosition = NO_POSITION;

        @Override
        public void onScrollStateChanged(@NonNull RecyclerView v, int state) {
            if (state != SCROLL_STATE_IDLE) { return; }

            int position = getDisplayedItemPosition();
            if (position != NO_POSITION && position != previousPosition) {
                model.onScrollTo(position, isInternalScroll, environment.displayTimer().getTime());
            }
            previousPosition = position;
            isInternalScroll = false;
        }
    };
}
