/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.RadioButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.CheckableModel
import com.urbanairship.android.layout.model.RadioInputModel
import com.urbanairship.android.layout.property.CheckboxStyle
import com.urbanairship.android.layout.property.SwitchStyle
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.ShapeButton

internal class RadioInputView(
    context: Context,
    model: RadioInputModel
) : CheckableView<RadioInputModel>(context, model) {

    override val accessibilityNodeClassName: String = RadioButton::class.java.name

    init {
        val baseBackground = this.background
        model.listener = object : CheckableModel.Listener {
            override fun setChecked(checked: Boolean) = setCheckedInternal(checked)
            override fun setEnabled(enabled: Boolean) {
                this@RadioInputView.isEnabled = enabled
            }

            override fun setVisibility(visible: Boolean) {
                this@RadioInputView.isVisible = visible
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@RadioInputView, baseBackground, old, new)
            }
        }
    }

    override fun createSwitchView(style: SwitchStyle): SwitchCompat {
        return object : SwitchCompat(context) {
            override fun toggle() {
                // Not calling super, because the controller/model handles updating the view.
                checkedChangeListener?.onCheckedChange(this, !isChecked)
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
                // Not calling super, because the controller/model handles updating the view.
                checkedChangeListener?.onCheckedChange(this, !isChecked)
            }

            override fun getAccessibilityClassName(): CharSequence? {
                return accessibilityNodeClassName
            }

            override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
                super.onInitializeAccessibilityEvent(event)
                event.setClassName(accessibilityNodeClassName)
            }

            override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.setClassName(accessibilityNodeClassName)
            }
        }
    }
}
