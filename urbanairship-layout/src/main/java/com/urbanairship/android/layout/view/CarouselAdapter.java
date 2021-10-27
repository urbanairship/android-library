/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.view.ViewGroup;

import com.urbanairship.android.layout.Layout;
import com.urbanairship.android.layout.Layout.LayoutViewHolder;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.property.ViewType;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class CarouselAdapter extends RecyclerView.Adapter<LayoutViewHolder<?, ?>> {
    private final List<BaseModel> items = new ArrayList<>();

    @NonNull
    @Override
    public LayoutViewHolder<?, ?> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return Layout.viewHolder(parent.getContext(), ViewType.from(viewType));
    }

    @Override
    public void onBindViewHolder(@NonNull LayoutViewHolder holder, int position) {
        holder.bind(getItemAtPosition(position));
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
}
