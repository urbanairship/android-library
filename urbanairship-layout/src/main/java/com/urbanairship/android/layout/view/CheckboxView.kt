/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import androidx.appcompat.widget.SwitchCompat
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.CheckboxModel
import com.urbanairship.android.layout.property.CheckboxStyle
import com.urbanairship.android.layout.property.SwitchStyle
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.ShapeButton

internal class CheckboxView(
    context: Context,
    model: CheckboxModel,
    viewEnvironment: ViewEnvironment
) : CheckableView<CheckboxModel>(context, model, viewEnvironment) {

    override fun configure() {
        super.configure()

        model.setListener(::setCheckedInternal)
    }

    override fun createSwitchView(style: SwitchStyle): SwitchCompat {
        return object : SwitchCompat(context) {
            override fun toggle() {
                model.onCheckedChange(!isChecked)
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
                model.onCheckedChange(!isChecked)
            }
        }
    }
}
