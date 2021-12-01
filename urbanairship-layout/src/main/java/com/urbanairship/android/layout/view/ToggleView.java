/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.urbanairship.android.layout.model.ToggleModel;
import com.urbanairship.android.layout.property.CheckboxStyle;
import com.urbanairship.android.layout.property.SwitchStyle;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Checkbox or Switch view for use within a {@code FormController} or {@code NpsController}.
 */
public class ToggleView extends FrameLayout implements BaseView<ToggleModel> {
    private ToggleModel model;

    public ToggleView(@NonNull Context context) {
        super(context);
        init();
    }

    public ToggleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ToggleView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static ToggleView create(@NonNull Context context, @NonNull ToggleModel model) {
        ToggleView view = new ToggleView(context);
        view.setModel(model);
        return view;
    }

    @Override
    public void setModel(@NonNull ToggleModel model) {
        this.model = model;
        configure();
    }

    private void configure() {
        LayoutUtils.applyBorderAndBackground(this, model);

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }

        switch(model.getToggleType()) {
            case SWITCH:
                configureSwitch((SwitchStyle) model.getToggleStyle());
                break;
            case CHECKBOX:
                configureCheckbox((CheckboxStyle) model.getToggleStyle());
                break;
        }

        model.onInit();
    }

    private void configureSwitch(SwitchStyle style) {
        SwitchMaterial view = new SwitchMaterial(getContext());

        // TODO: build color state lists from style and apply to the thumb and track (in LayoutUtils so we can share with CheckboxView).

        // 'model.border' and 'model.background' are both ignored when rendering switch views.
        // 'style.onColor' defines the color of the track when checked. It also defines the color of the thumb,
        //      but we'll set an alpha or adjust the color to make it lighter.
        // 'style.offColor' defines the color of the track when unchecked. It also defines the color of the thumb,
        //      similar to above.

        view.setOnCheckedChangeListener((v, isChecked) -> model.onCheckedChange(isChecked));

        addView(view, MATCH_PARENT, MATCH_PARENT);
    }

    private void configureCheckbox(CheckboxStyle style) {
        MaterialCheckBox view = new MaterialCheckBox(getContext());

        // TODO: build color state lists from style and apply to the view (in LayoutUtils so we can share with CheckboxView).

        // 'model.border' defines the shape and color around the check.
        // 'model.background' is the fill behind the check.
        // 'style.checkMarkColor' is the color of the check mark.
        // 'style.checkedBorderColor' is the border color override when checked.
        // 'style.checkedBackgroundColor' is the background color override when checked.

        view.setOnCheckedChangeListener((v, isChecked) -> model.onCheckedChange(isChecked));

        addView(view, MATCH_PARENT, MATCH_PARENT);
    }
}
