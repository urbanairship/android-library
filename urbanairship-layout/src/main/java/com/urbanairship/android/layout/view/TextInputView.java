/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.util.AttributeSet;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.util.LayoutUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

// TODO: This should probably be a TextInputLayout with a TextInputEditText child for consistency with other
// material views
public class TextInputView extends AppCompatEditText implements BaseView<TextInputModel> {
    private TextInputModel model;

    public TextInputView(@NonNull Context context) {
        super(context);
        init();
    }

    public TextInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextInputView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static TextInputView create(@NonNull Context context, @NonNull TextInputModel model, Environment environment) {
        TextInputView view = new TextInputView(context);
        view.setModel(model, environment);
        return view;
    }

    @Override
    public void setModel(@NonNull TextInputModel model, @NonNull Environment environment) {
        this.model = model;
        LayoutUtils.applyTextInputModel(this, model);
    }
}
