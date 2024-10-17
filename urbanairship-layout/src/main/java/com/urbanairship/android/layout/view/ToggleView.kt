/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.urbanairship.android.layout.model.CheckableModel
import com.urbanairship.android.layout.model.ToggleModel
import com.urbanairship.android.layout.property.CheckboxStyle
import com.urbanairship.android.layout.property.SwitchStyle
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.ShapeButton

/**
 * Checkbox or Switch view for use within a `FormController` or `NpsController`.
 */
internal class ToggleView(
    context: Context,
    model: ToggleModel
) : CheckableView<ToggleModel>(context, model) {

    init {
        model.listener = object : CheckableModel.Listener {
            override fun setChecked(checked: Boolean) = Unit
            override fun setEnabled(enabled: Boolean) {
                this@ToggleView.isEnabled = enabled
            }
            override fun setVisibility(visible: Boolean) {
                this@ToggleView.isVisible = visible
            }
        }
    }

    override fun createSwitchView(style: SwitchStyle): SwitchCompat {
        return object : SwitchCompat(context) {
            override fun toggle() {
                // Super updates the toggle view
                super.toggle()
                checkedChangeListener?.onCheckedChange(this, isChecked)
            }
        }
    }

    override fun createCheckboxView(style: CheckboxStyle): ShapeButton {
        val checked = style.bindings.selected
        val unchecked = style.bindings.unselected
        return object : ShapeButton(
            context, checked.shapes, unchecked.shapes, checked.icon, unchecked.icon
        ) {
            override fun toggle() {
                // Super updates the toggle view
                super.toggle()
                checkedChangeListener?.onCheckedChange(this, isChecked)
            }
        }
    }
}
