/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.TextInputModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.widget.AppCompatEditText;

import static android.view.MotionEvent.ACTION_UP;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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
        setBackground(null);
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        return super.onCreateInputConnection(outAttrs);
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

        setId(model.getViewId());
        configure();
    }

    private void configure() {
        LayoutUtils.applyTextInputModel(this, model);

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            setContentDescription(model.getContentDescription());
        }

        if (model.getValue() != null) {
            setText(model.getValue());
        }

        addTextChangedListener(textWatcher);
        setOnTouchListener(touchListener);

        setMovementMethod(new ScrollingMovementMethod());

        model.onConfigured();
        LayoutUtils.doOnAttachToWindow(this, model::onAttachedToWindow);
    }

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            model.onInputChange(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) { }
    };

    private final OnTouchListener touchListener = (v, event) -> {
        // Enables nested scrolling of this text view so that overflow can be scrolled
        // when inside of a scroll layout.
        v.getParent().requestDisallowInterceptTouchEvent(true);
        if ((event.getAction() & MotionEvent.ACTION_MASK) == ACTION_UP) {
            v.getParent().requestDisallowInterceptTouchEvent(false);
        }
        return false;
    };
}
