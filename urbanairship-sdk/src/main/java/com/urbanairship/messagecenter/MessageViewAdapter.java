/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.


Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.messagecenter;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
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
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
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
    public MessageViewAdapter(Context context, int layout) {
        this.context = context;
        this.layout = layout;
        this.items = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getMessageId().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(layout, parent, false);
        }

        bindView(view, items.get(position), position);

        return view;
    }

    /**
     * Called when a {@link RichPushMessage} needs to be bound to the view.
     *
     * @param view The view.
     * @param message The message.
     * @param position The message's position in the list.
     */
    protected abstract void bindView(View view, RichPushMessage message, int position);

    /**
     * Sets the current items in the adapter to the collection.
     *
     * @param collection Collection of items
     */
    public void set(Collection<RichPushMessage> collection) {
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
    protected Context getContext() {
        return context;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
