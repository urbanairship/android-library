/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

public abstract class CheckableViewAdapter<V extends View> {
    protected final V view;

    private CheckableViewAdapter(@NonNull V view) {
        this.view = view;
    }

    public abstract void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener);

    public abstract void setChecked(boolean isChecked);

    public void setContentDescription(@NonNull String contentDescription) {
        view.setContentDescription(contentDescription);
    }

    public void setId(@IdRes int id) {
        view.setId(id);
    }

    public static class Checkbox extends CheckableViewAdapter<ShapeButton> {
        public Checkbox(@NonNull ShapeButton view) {
            super(view);
        }

        @Override
        public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
            view.setOnCheckedChangeListener(listener != null ? listener::onCheckedChange : null);
        }

        @Override
        public void setChecked(boolean isChecked) {
            view.setChecked(isChecked);
        }
    }

    public static class Switch extends CheckableViewAdapter<SwitchCompat> {
        public Switch(@NonNull SwitchCompat view) {
            super(view);
        }

        @Override
        public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
            view.setOnCheckedChangeListener(listener != null ? listener::onCheckedChange : null);
        }

        @Override
        public void setChecked(boolean isChecked) {
            view.setChecked(isChecked);
        }
    }

    public interface OnCheckedChangeListener {
        void onCheckedChange(View view, boolean isChecked);
    }
}
