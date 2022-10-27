/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ifNotEmpty

internal class TextInputView(
    context: Context,
    private val model: TextInputModel,
    viewEnvironment: ViewEnvironment
) : AppCompatEditText(context), BaseView {

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            model.onInputChange(s.toString())
        }

        override fun afterTextChanged(s: Editable) { }
    }

    private val touchListener = View.OnTouchListener { v: View, event: MotionEvent ->
        // Enables nested scrolling of this text view so that overflow can be scrolled
        // when inside of a scroll layout.
        v.parent.requestDisallowInterceptTouchEvent(true)
        if (event.action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_UP) {
            v.parent.requestDisallowInterceptTouchEvent(false)
        }
        false
    }

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        background = null
        movementMethod = ScrollingMovementMethod()

        LayoutUtils.applyTextInputModel(this, model)

        model.contentDescription.ifNotEmpty { contentDescription = it }
        model.value?.let { setText(it) }
        addTextChangedListener(textWatcher)
        setOnTouchListener(touchListener)

        model.onConfigured()
        LayoutUtils.doOnAttachToWindow(this) { model.onAttachedToWindow() }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.imeOptions = outAttrs.imeOptions or
                (EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        return super.onCreateInputConnection(outAttrs)
    }
}
