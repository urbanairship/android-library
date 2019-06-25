/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.urbanairship.richpush.RichPushMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A generic base adapter that binds items to views using the ViewBinder interface.
 */
public abstract class MessageViewAdapter extends BaseAdapter {

    private final List<RichPushMessage> items;
    private final Context context;
    private final int layout;

    /**
     * Creates a ViewBinder
     *
     * @param context The application context
     * @param layout The layout for each line item
     */
    public MessageViewAdapter(@NonNull Context context, int layout) {
        this.context = context;
        this.layout = layout;
        this.items = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Nullable
    @Override
    public Object getItem(int position) {
        if (position >= items.size() || position < 0) {
            return null;
        }

        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        if (position >= items.size() || position < 0) {
            return -1;
        }

        return items.get(position).getMessageId().hashCode();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @Nullable ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(layout, parent, false);
        }

        if (position < items.size() && position >= 0) {
            bindView(view, items.get(position), position);
        }

        return view;
    }

    /**
     * Called when a {@link RichPushMessage} needs to be bound to the view.
     *
     * @param view The view.
     * @param message The message.
     * @param position The message's position in the list.
     */
    protected abstract void bindView(@NonNull View view, @NonNull RichPushMessage message, int position);

    /**
     * Sets the current items in the adapter to the collection.
     *
     * @param collection Collection of items
     */
    public void set(@NonNull Collection<RichPushMessage> collection) {
        synchronized (items) {
            items.clear();
            items.addAll(collection);
        }

        notifyDataSetChanged();
    }

    /**
     * Returns the context.
     *
     * @return The context.
     */
    @NonNull
    protected Context getContext() {
        return context;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

}
