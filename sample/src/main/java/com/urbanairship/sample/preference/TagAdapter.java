/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.urbanairship.sample.databinding.ItemTagBinding;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView adapter for a list of tags.
 */
public class TagAdapter extends ListAdapter<String, TagAdapter.ViewHolder> {

    /**
     * Default constructor.
     */
    public TagAdapter() {
        super(new TagFilterDiff());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        ItemTagBinding binding = ItemTagBinding.inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.bind(getItem(i));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemTagBinding binding;
        private String tag = null;

        ViewHolder(@NonNull ItemTagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(final String tag) {
            this.tag = tag;
            binding.tagText.setText(tag);
        }

        @Nullable
        public String getTag() {
            return tag;
        }
    }

    public static class TagFilterDiff extends DiffUtil.ItemCallback<String> {

        @Override
        public boolean areItemsTheSame(@NonNull String s, @NonNull String t1) {
            return s.equals(t1);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String s, @NonNull String t1) {
            return s.equals(t1);
        }

    }

}
