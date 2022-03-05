/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.Checkable;

import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.property.TextAppearance;
import com.urbanairship.android.layout.shape.Shape;
import com.urbanairship.android.layout.util.LayoutUtils;

import java.util.List;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

public class ShapeButton extends AppCompatButton implements Checkable, Clippable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    @Nullable
    private final TextAppearance checkedTextAppearance;
    @Nullable
    private final TextAppearance uncheckedTextAppearance;
    @Nullable
    private final String text;

    private final ClippableViewDelegate clippableViewDelegate;

    private boolean isChecked = false;

    @Nullable
    private OnCheckedChangeListener checkedChangeListener = null;

    public ShapeButton(
        @NonNull Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes,
        @Nullable String text,
        @Nullable TextAppearance checkedTextAppearance,
        @Nullable TextAppearance uncheckedTextAppearance
    ) {
        this(context, checkedShapes, uncheckedShapes, null, null, text, checkedTextAppearance, uncheckedTextAppearance);
    }

    public ShapeButton(
        @NonNull Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes,
        @Nullable Image.Icon checkedIcon,
        @Nullable Image.Icon uncheckedIcon
    ) {
        this(context, checkedShapes, uncheckedShapes, checkedIcon, uncheckedIcon, null, null, null);
    }

    public ShapeButton(
        @NonNull Context context,
        @NonNull List<Shape> checkedShapes,
        @NonNull List<Shape> uncheckedShapes,
        @Nullable Image.Icon checkedIcon,
        @Nullable Image.Icon uncheckedIcon,
        @Nullable String text,
        @Nullable TextAppearance checkedTextAppearance,
        @Nullable TextAppearance uncheckedTextAppearance
    ) {
        super(context);

        setId(generateViewId());

        this.checkedTextAppearance = checkedTextAppearance;
        this.uncheckedTextAppearance = uncheckedTextAppearance;
        this.text = text;

        clippableViewDelegate = new ClippableViewDelegate();

        Drawable background = Shape.buildStateListDrawable(context, checkedShapes, uncheckedShapes, checkedIcon, uncheckedIcon);
        setBackground(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setForeground(ContextCompat.getDrawable(context, R.drawable.ua_layout_imagebutton_ripple));
        }

        setText(text);
        updateText();

        setPadding(0, 0, 0, 0);
        setGravity(Gravity.CENTER);
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked != isChecked) {
            isChecked = checked;
            refreshDrawableState();
            updateText();
            if (checkedChangeListener != null) {
                checkedChangeListener.onCheckedChanged(this, checked);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    public boolean performClick() {
        toggle();

        boolean handled = super.performClick();
        if (!handled) {
            // View only makes a sound effect if the onClickListener was
            // called, so we'll need to make one here instead.
            playSoundEffect(SoundEffectConstants.CLICK);
        }

        return handled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @MainThread
    public void setClipPathBorderRadius(@Dimension float borderRadius) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius);
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        this.checkedChangeListener = listener;
    }

    private void updateText() {
        if (text != null && checkedTextAppearance != null && uncheckedTextAppearance != null) {
            LayoutUtils.applyTextAppearance(this, isChecked() ? checkedTextAppearance : uncheckedTextAppearance);
        }
    }

    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state has changed.
         *
         * @param view The button view whose state has changed.
         * @param isChecked  The new checked state of button.
         */
        void onCheckedChanged(View view, boolean isChecked);
    }
}
