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
package com.urbanairship.android.layout.widget

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Drawable which delegates all calls to its wrapped [Drawable].
 *
 *
 * The wrapped [Drawable] *must* be fully released from any [View]
 * before wrapping, otherwise internal [Callback] may be dropped.
 *
 *
 * This class is a compat version of [android.graphics.drawable.DrawableWrapper],
 * based on [androidx.appcompat.graphics.drawable.DrawableWrapper], which is
 * restricted to library group (as of app compat v1.3.1).
 * @hide
 */
public open class DrawableWrapper : Drawable, Drawable.Callback {

    private val state: DrawableWrapperState?

    private var drawable: Drawable? = null

    internal constructor(state: DrawableWrapperState?, res: Resources?) {
        this.state = state
        state?.drawableState?.let { setDrawable(it.newDrawable(res)) }
    }

    public constructor(drawable: Drawable?) {
        state = null
        setDrawable(drawable)
    }

    override fun draw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        drawable?.bounds = bounds
    }

    override fun setChangingConfigurations(configs: Int) {
        drawable?.changingConfigurations = configs
    }

    override fun getChangingConfigurations(): Int {
        return drawable?.changingConfigurations ?: -1
    }

    override fun setDither(dither: Boolean) {
        drawable?.setDither(dither)
    }

    override fun setFilterBitmap(filter: Boolean) {
        drawable?.isFilterBitmap = filter
    }

    override fun setAlpha(alpha: Int) {
        drawable?.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        drawable?.colorFilter = cf
    }

    override fun isStateful(): Boolean {
        return drawable?.isStateful == true
    }

    override fun setState(stateSet: IntArray): Boolean {
        return drawable?.setState(stateSet) == true
    }

    override fun getState(): IntArray {
        return drawable?.state ?: IntArray(0)
    }

    override fun jumpToCurrentState() {
        drawable?.jumpToCurrentState()
    }

    override fun getCurrent(): Drawable {
        return drawable?.current ?: ShapeDrawable()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return super.setVisible(visible, restart) || drawable?.setVisible(visible, restart) == true
    }

    override fun getOpacity(): Int {
        return drawable?.opacity ?: 0
    }

    override fun getTransparentRegion(): Region? {
        return drawable?.transparentRegion
    }

    override fun getIntrinsicWidth(): Int {
        return drawable?.intrinsicWidth ?: 0
    }

    override fun getIntrinsicHeight(): Int {
        return drawable?.intrinsicHeight ?: 0
    }

    override fun getMinimumWidth(): Int {
        return drawable?.minimumWidth ?: 0
    }

    override fun getMinimumHeight(): Int {
        return drawable?.minimumHeight ?: 0
    }

    override fun getPadding(padding: Rect): Boolean {
        return drawable?.getPadding(padding) == true
    }

    /**
     * {@inheritDoc}
     */
    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    /**
     * {@inheritDoc}
     */
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    /**
     * {@inheritDoc}
     */
    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    override fun onLevelChange(level: Int): Boolean {
        return drawable?.setLevel(level) == true
    }

    override fun setAutoMirrored(mirrored: Boolean) {
        drawable?.let { DrawableCompat.setAutoMirrored(it, mirrored) }
    }

    override fun isAutoMirrored(): Boolean {
        return drawable?.let { DrawableCompat.isAutoMirrored(it) } ?: false
    }

    override fun setTint(tint: Int) {
        drawable?.let { DrawableCompat.setTint(it, tint) }
    }

    override fun setTintList(tint: ColorStateList?) {
        drawable?.let { DrawableCompat.setTintList(it, tint) }
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        drawable?.let { DrawableCompat.setTintMode(it, tintMode) }
    }

    override fun setHotspot(x: Float, y: Float) {
        drawable?.let { DrawableCompat.setHotspot(it, x, y) }
    }

    override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {
        drawable?.let { DrawableCompat.setHotspotBounds(it, left, top, right, bottom) }
    }

    public fun getDrawable(): Drawable? {
        return drawable
    }

    public fun setDrawable(drawable: Drawable?) {
        if (this.drawable != null) {
            this.drawable?.callback = null
        }

        this.drawable = drawable

        drawable?.let {
            it.callback = this

            // Only call setters for data that's stored in the base Drawable.
            it.setVisible(isVisible, true)
            it.setState(getState())
            it.level = level
            it.bounds = bounds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                it.layoutDirection = layoutDirection
            }

            if (state != null) {
                state.drawableState = it.constantState
            }
        }

        invalidateSelf()
    }

    internal abstract class DrawableWrapperState(orig: DrawableWrapperState?) : ConstantState() {

        private var changingConfigurations: Int = 0
        var drawableState: ConstantState? = null

        init {
            if (orig != null) {
                changingConfigurations = orig.changingConfigurations
                drawableState = orig.drawableState
            }
        }

        override fun newDrawable(): Drawable {
            return newDrawable(null)
        }

        abstract override fun newDrawable(res: Resources?): Drawable

        override fun getChangingConfigurations(): Int {
            val state = drawableState?.let { it.changingConfigurations } ?: 0
            return (changingConfigurations or state)
        }
    }
}
