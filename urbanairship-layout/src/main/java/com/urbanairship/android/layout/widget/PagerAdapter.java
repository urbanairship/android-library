/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.PagerModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PagerAdapter extends RecyclerView.Adapter<PagerAdapter.ViewHolder> {
    @NonNull
    private final List<BaseModel> items = new ArrayList<>();

    @NonNull
    private final PagerModel pagerModel;
    @NonNull
    private final Environment environment;

    public PagerAdapter(@NonNull PagerModel model, @NonNull Environment environment) {
        this.pagerModel = model;
        this.environment = environment;
    }

    @NonNull
    @Override
    public PagerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull PagerAdapter.ViewHolder holder, int position) {
        BaseModel model = getItemAtPosition(position);
        holder.container.setId(pagerModel.getPageViewId(position));
        holder.bind(model, environment);
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
        return items.get(position).getType().ordinal();
    }

    public BaseModel getItemAtPosition(int position) {
        return items.get(position);
    }

    public void setItems(@NonNull List<BaseModel> items) {
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
            container.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            this.container = container;
        }

        public void bind(@NonNull BaseModel item, @NonNull Environment environment) {
            View view = Thomas.view(itemView.getContext(), item, environment);
            container.addView(view, new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));

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
