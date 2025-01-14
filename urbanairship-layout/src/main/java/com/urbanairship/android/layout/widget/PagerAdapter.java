/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import java.util.ArrayList;
import java.util.List;

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerAdapter extends RecyclerView.Adapter<PagerAdapter.ViewHolder> {
    @NonNull
    private final List<BaseModel<?, ?, ?>> items = new ArrayList<>();

    @NonNull
    private final PagerModel pagerModel;
    @NonNull
    private final ViewEnvironment viewEnvironment;

    public PagerAdapter(@NonNull PagerModel model, @NonNull ViewEnvironment viewEnvironment) {
        this.pagerModel = model;
        this.viewEnvironment = viewEnvironment;
    }

    @NonNull
    @Override
    public PagerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull PagerAdapter.ViewHolder holder, int position) {
        BaseModel<?, ?, ?> model = getItemAtPosition(position);
        holder.container.setId(pagerModel.getPageViewId(position));
        holder.bind(model, viewEnvironment);
    }

    @Override
    public void onViewRecycled(@NonNull PagerAdapter.ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.onRecycled();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getViewInfo().getType().ordinal();
    }

    public BaseModel<?, ?, ?> getItemAtPosition(int position) {
        return items.get(position);
    }

    public void setItems(@NonNull List<BaseModel<?, ?, ?>> items) {
        if (!this.items.equals(items)) {
            this.items.clear();
            this.items.addAll(items);
            notifyDataSetChanged();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewGroup container;

        public ViewHolder(@NonNull Context context) {
            this(new FrameLayout(context));
        }

        private ViewHolder(@NonNull ViewGroup container) {
            super(container);
            this.container = container;
        }

        public void bind(@NonNull BaseModel<?, ?, ?> item, @NonNull ViewEnvironment viewEnvironment) {
            View view = item.createView(itemView.getContext(), viewEnvironment, null);
            container.addView(view, MATCH_PARENT, MATCH_PARENT);

            // Register a listener, so we can request insets when the view is attached.
            LayoutUtils.doOnAttachToWindow(itemView, () ->
                ViewCompat.requestApplyInsets(itemView)
            );
        }

        public void onRecycled() {
            container.removeAllViews();
        }
    }
}
