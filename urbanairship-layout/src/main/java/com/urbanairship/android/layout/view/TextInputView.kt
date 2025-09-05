/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.text.Editable
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.children
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isActionUp
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.android.layout.util.onEditing
import com.urbanairship.android.layout.util.textChanges
import com.urbanairship.android.layout.widget.TappableView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class TextInputView(
    context: Context,
    model: TextInputModel
) : LinearLayout(context), BaseView, TappableView {

    private val clicksChannel = Channel<Unit>(UNLIMITED)
    private val input: AppCompatEditText by lazy { makeTextInput(model) }

    private val touchListener = OnTouchListener { v: View, event: MotionEvent ->
        // Enables nested scrolling of this text view so that overflow can be scrolled
        // when inside a scroll layout.
        v.parent.requestDisallowInterceptTouchEvent(true)
        if (event.isActionUp) {
            v.parent.requestDisallowInterceptTouchEvent(false)

            // Also send an event to the clicks channel, for TAP event handling.
            clicksChannel.trySend(Unit)
        }
        false
    }

    init {
        background = null
        clipToOutline = true

        LayoutUtils.applyTextInputModel(input, model)

        // Set content description on the EditText instead of the parent
        model.contentDescription(context).ifNotEmpty {
            input.contentDescription = it
        }

        model.listener = object : TextInputModel.Listener {
            override fun restoreValue(value: String) {
                if (input.text.isNullOrEmpty()) input.setText(value)
            }

            override fun setVisibility(visible: Boolean) {
                this@TextInputView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@TextInputView.isEnabled = enabled
                children.forEach { it.isEnabled = enabled }
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@TextInputView, old, new)
            }

            override fun onStateUpdated(state: ThomasState) {
                val resolved = state.resolveOptional(
                    overrides = model.viewInfo.viewOverrides?.iconEnd,
                    default = model.viewInfo.iconEnd
                )

                val endDrawable = when (resolved) {
                    is TextInputInfo.IconEnd.Floating ->
                        resolved.icon.getDrawable(context, isEnabled)
                    else -> null
                }

                endDrawable?.let {
                    val size = spToPx(context, model.viewInfo.textAppearance.fontSize).toInt()
                    it.setBounds(0, 0, size, size)
                }

                if (isLayoutRtl) {
                    input.setCompoundDrawables(endDrawable, null, null, null)
                } else {
                    input.setCompoundDrawables(null, null, endDrawable, null)
                }
            }
        }

        setOnTouchListener(touchListener)

        if (model.viewInfo.inputType == FormInputType.SMS && model.viewInfo.smsLocales != null) {
            addView(
                makeLocalePicker(context, model),
                LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        }

        addView(input, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    internal fun textChanges() = input.textChanges()
    internal fun onEditing() = input.onEditing()
    internal val text: Editable?
        get() = input.text

    private fun makeTextInput(model: TextInputModel): AppCompatEditText {
        return AppCompatEditText(context).also {
            it.movementMethod = ScrollingMovementMethod()
            it.background = null
            it.clipToOutline = true
            it.id = model.editTextViewId
        }
    }

    private fun makeLocalePicker(
        context: Context,
        model: TextInputModel
    ): View {

        val adapter = SmsLocaleAdapter(
            context = context,
            locales = model.viewInfo.smsLocales ?: emptyList(),
            appearance = model.viewInfo.textAppearance)

        return Spinner(context).apply {
            background = null
            setBackgroundColor(android.R.color.transparent)

            setAdapter(adapter)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    val locale = adapter.getItem(p2)
                    input.hint = model.viewInfo.hintText ?: locale.prefix
                    model.onNewLocale(locale)
                }

                override fun onNothingSelected(p0: AdapterView<*>?) { }
            }
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.imeOptions = outAttrs.imeOptions or
                (EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        return super.onCreateInputConnection(outAttrs)
    }

    override fun taps(): Flow<Unit> = clicksChannel.receiveAsFlow()
}
