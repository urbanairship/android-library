/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.CheckboxModel;
import com.urbanairship.android.layout.property.CheckboxStyle;
import com.urbanairship.android.layout.property.SwitchStyle;
import com.urbanairship.android.layout.widget.CheckableView;
import com.urbanairship.android.layout.widget.ShapeButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

public class CheckboxView extends CheckableView<CheckboxModel> {

    public CheckboxView(@NonNull Context context) {
        super(context);
    }

    public CheckboxView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckboxView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    public static CheckboxView create(
        @NonNull Context context,
        @NonNull CheckboxModel model,
        @NonNull Environment environment
    ) {
        CheckboxView view = new CheckboxView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    protected void configure() {
        super.configure();

        getModel().setListener(this::setCheckedInternal);
    }

    @NonNull
    @Override
    protected SwitchCompat createSwitchView(SwitchStyle style) {
        return new SwitchCompat(getContext()) {
            @Override
            public void toggle() {
                getModel().onCheckedChange(!isChecked());
            }
        };
    }

    @NonNull
    @Override
    protected ShapeButton createCheckboxView(CheckboxStyle style) {
        CheckboxStyle.Binding checked = style.getBindings().getSelected();
        CheckboxStyle.Binding unchecked = style.getBindings().getUnselected();
        return new ShapeButton(getContext(), checked.getShapes(), unchecked.getShapes(), checked.getIcon(), unchecked.getIcon()) {
            @Override
            public void toggle() {
                getModel().onCheckedChange(!isChecked());
            }
        };
    }
}
