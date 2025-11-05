/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.view.View
import android.widget.CompoundButton
import androidx.annotation.IdRes
import androidx.appcompat.widget.SwitchCompat

public abstract class CheckableViewAdapter<V : View> private constructor(
    protected val view: V
) {

    public abstract fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?)

    public abstract fun setChecked(isChecked: Boolean)

    public abstract fun setEnabled(isEnabled: Boolean)

    public abstract fun isChecked(): Boolean

    public fun setContentDescription(contentDescription: String) {
        view.contentDescription = contentDescription
    }

    public fun setId(@IdRes id: Int) {
        view.id = id
    }

    public class Checkbox public constructor(
        view: ShapeButton
    ) : CheckableViewAdapter<ShapeButton>(view) {

        override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
            if (listener != null) {
                view.setOnCheckedChangeListener { view, isChecked ->
                    listener.onCheckedChange(view, isChecked)
                }
            } else {
                view.setOnCheckedChangeListener(null)
            }
        }

        override fun setChecked(isChecked: Boolean) {
            view.setChecked(isChecked)
        }

        override fun setEnabled(isEnabled: Boolean) {
            view.isEnabled = isEnabled
        }

        override fun isChecked(): Boolean {
            return view.isChecked
        }
    }

    public class Switch public constructor(
        view: SwitchCompat
    ) : CheckableViewAdapter<SwitchCompat>(view) {

        override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
            if (listener != null) {
                view.setOnCheckedChangeListener { view, isChecked ->
                    listener.onCheckedChange(view, isChecked)
                }
            } else {
                view.setOnCheckedChangeListener(null)
            }
        }

        override fun setChecked(isChecked: Boolean) {
            view.setChecked(isChecked)
        }

        override fun setEnabled(isEnabled: Boolean) {
            view.isEnabled = isEnabled
        }

        override fun isChecked(): Boolean {
            return view.isChecked
        }
    }

    public fun interface OnCheckedChangeListener {
        public fun onCheckedChange(view: View, isChecked: Boolean)
    }
}
