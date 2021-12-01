/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

public class PagerView extends RecyclerView implements BaseView<PagerModel> {
    private PagerModel model;
    private PagerAdapter adapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;

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
        adapter = new PagerAdapter();
        snapHelper = new PagerSnapHelper();

        setHorizontalScrollBarEnabled(false);

        setLayoutManager(layoutManager);
        setAdapter(adapter);
        snapHelper.attachToRecyclerView(this);
    }

    @NonNull
    public static PagerView create(@NonNull Context context, @NonNull PagerModel model) {
        PagerView view = new PagerView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull PagerModel model) {
        this.model = model;
        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(this, model);

        adapter.setItems(model.getItems());
        addOnScrollListener(onScrollListener);

        model.setListener(modelListener);

        // Emit an init event so that we can connect to the indicator view, if one exists.
        model.onConfigured(getDisplayedItemPosition());
    }

    private int getDisplayedItemPosition() {
        View snapView = snapHelper.findSnapView(layoutManager);
        return snapView != null ? getChildAdapterPosition(snapView) : 0;
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
                model.onScrollTo(position);
            }
            previousPosition = position;
        }
    };
}
