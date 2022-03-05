/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urbanairship.android.layout.widget;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

/**
 * Drawable which delegates all calls to its wrapped {@link Drawable}.
 * <p>
 * The wrapped {@link Drawable} <em>must</em> be fully released from any {@link View}
 * before wrapping, otherwise internal {@link Callback} may be dropped.
 * <p>
 * This class is a compat version of {@link android.graphics.drawable.DrawableWrapper},
 * based on {@link androidx.appcompat.graphics.drawable.DrawableWrapper}, which is
 * restricted to library group (as of app compat v1.3.1).
 * @hide
 */
public class DrawableWrapper extends Drawable implements Drawable.Callback {

    private final DrawableWrapperState state;

    private Drawable drawable;

    DrawableWrapper(@Nullable DrawableWrapperState state, @Nullable Resources res) {
        this.state = state;
        if (state != null && state.drawableState != null) {
            setDrawable(state.drawableState.newDrawable(res));
        }
    }

    public DrawableWrapper(@Nullable Drawable drawable) {
        state = null;
        setDrawable(drawable);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawable.draw(canvas);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        drawable.setBounds(bounds);
    }

    @Override
    public void setChangingConfigurations(int configs) {
        drawable.setChangingConfigurations(configs);
    }

    @Override
    public int getChangingConfigurations() {
        return drawable.getChangingConfigurations();
    }

    @Override
    public void setDither(boolean dither) {
        if (drawable != null) {
            drawable.setDither(dither);
        }
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        drawable.setFilterBitmap(filter);
    }

    @Override
    public void setAlpha(int alpha) {
        drawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        drawable.setColorFilter(cf);
    }

    @Override
    public boolean isStateful() {
        return drawable.isStateful();
    }

    @Override
    public boolean setState(int[] stateSet) {
        return drawable.setState(stateSet);
    }

    @Override
    public int[] getState() {
        return drawable.getState();
    }

    @Override
    public void jumpToCurrentState() {
        drawable.jumpToCurrentState();
    }

    @Override
    public Drawable getCurrent() {
        return drawable.getCurrent();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        return super.setVisible(visible, restart) || drawable.setVisible(visible, restart);
    }

    @Override
    public int getOpacity() {
        return drawable.getOpacity();
    }

    @Override
    public Region getTransparentRegion() {
        return drawable.getTransparentRegion();
    }

    @Override
    public int getIntrinsicWidth() {
        return drawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return drawable.getIntrinsicHeight();
    }

    @Override
    public int getMinimumWidth() {
        return drawable.getMinimumWidth();
    }

    @Override
    public int getMinimumHeight() {
        return drawable.getMinimumHeight();
    }

    @Override
    public boolean getPadding(Rect padding) {
        return drawable.getPadding(padding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }

    @Override
    protected boolean onLevelChange(int level) {
        return drawable.setLevel(level);
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
       DrawableCompat.setAutoMirrored(drawable, mirrored);
    }

    @Override
    public boolean isAutoMirrored() {
        return DrawableCompat.isAutoMirrored(drawable);
    }

    @Override
    public void setTint(int tint) {
        DrawableCompat.setTint(drawable, tint);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        DrawableCompat.setTintList(drawable, tint);
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        DrawableCompat.setTintMode(drawable, tintMode);
    }

    @Override
    public void setHotspot(float x, float y) {
        DrawableCompat.setHotspot(drawable, x, y);
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        DrawableCompat.setHotspotBounds(drawable, left, top, right, bottom);
    }

    public Drawable getDrawable() {
        return drawable;
    }

    public void setDrawable(@Nullable Drawable drawable) {
        if (this.drawable != null) {
            this.drawable.setCallback(null);
        }

        this.drawable = drawable;

        if (drawable != null) {
            drawable.setCallback(this);

            // Only call setters for data that's stored in the base Drawable.
            drawable.setVisible(isVisible(), true);
            drawable.setState(getState());
            drawable.setLevel(getLevel());
            drawable.setBounds(getBounds());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                drawable.setLayoutDirection(getLayoutDirection());
            }

            if (state != null) {
                state.drawableState = drawable.getConstantState();
            }
        }

        invalidateSelf();
    }

    abstract static class DrawableWrapperState extends Drawable.ConstantState {
        int changingConfigurations;
        Drawable.ConstantState drawableState;

        DrawableWrapperState(@Nullable DrawableWrapperState orig, @Nullable Resources res) {
            if (orig != null) {
                changingConfigurations = orig.changingConfigurations;
                drawableState = orig.drawableState;
            }
        }

        @NonNull
        @Override
        public Drawable newDrawable() {
            return newDrawable(null);
        }

        @NonNull
        @Override
        public abstract Drawable newDrawable(@Nullable Resources res);

        @Override
        public int getChangingConfigurations() {
            return changingConfigurations
                | (drawableState != null ? drawableState.getChangingConfigurations() : 0);
        }
    }
}
