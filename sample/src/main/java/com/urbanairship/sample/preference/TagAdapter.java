/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.preference;

import android.support.annotation.NonNull;
import android.support.v7.recyclerview.extensions.ListAdapter;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.urbanairship.sample.databinding.ItemTagBinding;

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

    public class ViewHolder extends RecyclerView.ViewHolder {

        private ItemTagBinding binding;

        ViewHolder(@NonNull ItemTagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(final String tag) {
            binding.setTag(tag);
            binding.executePendingBindings();
        }

        public String getTag() {
            return binding.getTag();
        }

    }

    public static class TagFilterDiff extends DiffUtil.ItemCallback<String> {

        @Override
        public boolean areItemsTheSame(@NonNull String s, @NonNull String t1) {
            return s == t1;
        }

        @Override
        public boolean areContentsTheSame(@NonNull String s, @NonNull String t1) {
            return s.equals(t1);
        }

    }

}
