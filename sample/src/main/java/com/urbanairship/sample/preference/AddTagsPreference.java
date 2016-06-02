/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.sample.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.urbanairship.UAirship;
import com.urbanairship.sample.R;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * DialogPreference to set the tags
 *
 */
public class AddTagsPreference extends DialogPreference  {

    private final List<String> tags = new ArrayList<>();
    private final Set<String>  currentTags;
    private TagsAdapter adapter;

    public AddTagsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        currentTags = UAirship.shared().getPushManager().getTags();
    }

    @Override
    protected View onCreateDialogView() {
        tags.clear();
        tags.addAll(currentTags);

        View view = super.onCreateDialogView();
        ListView listView = (ListView) view.findViewById(R.id.tags_list);
        adapter = new TagsAdapter(getContext(), R.layout.tag_preference_item);
        listView.setAdapter(adapter);

        final EditText editText = (EditText) view.findViewById(R.id.new_tag_text);

        ImageButton button = (ImageButton) view.findViewById(R.id.new_tag_button);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View buttonView) {
                String newTag = editText.getText().toString();
                editText.setText(null);
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

                if (!UAStringUtil.isEmpty(newTag)) {
                    if (tags.contains(newTag)) {
                        showDuplicateItemToast();
                    } else {
                        tags.add(0, newTag);
                        adapter.notifyDataSetChanged();
                    }
                }
            }
        });

        return view;
    }

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription("ADD_TAGS");
        return view;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            if (callChangeListener(tags)) {
                setTags(tags);

                notifyChanged();
            }
        }
    }

    @Override
    public String getSummary() {
        StringBuilder summary = new StringBuilder();

        Iterator<String> iterator = currentTags.iterator();
        while (iterator.hasNext()) {
            summary.append(iterator.next());

            if (iterator.hasNext()) {
                summary.append(", ");
            }
        }

        return summary.toString();
    }

    @Override
    protected boolean shouldPersist() {
        return false;
    }

    private void setTags(List<String> tags) {
        currentTags.clear();
        currentTags.addAll(tags);

        UAirship.shared().getPushManager().setTags(currentTags);
    }

    private void showDuplicateItemToast() {
        Toast.makeText(getContext(), R.string.duplicate_tag_warning, Toast.LENGTH_SHORT).show();
    }

    private class TagsAdapter extends ArrayAdapter<String> {

        private final int layout;

        public TagsAdapter(Context context, int layout) {
            super(context, layout, tags);

            this.layout = layout;
        }

        private View createView(ViewGroup parent) {
            LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return layoutInflater.inflate(layout, parent, false);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            // Use either the convertView or create a new view
            View view = convertView == null ? createView(parent) : convertView;
            final String tag = this.getItem(position);

            TextView textView = (TextView) view.findViewById(R.id.tag_text);
            textView.setText(tag);


            ImageButton button = (ImageButton) view.findViewById(R.id.delete_tag_button);
            button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View buttonView) {
                    tags.remove(tag);
                    TagsAdapter.this.notifyDataSetChanged();
                }
            });

            return view;
        }
    }

}
