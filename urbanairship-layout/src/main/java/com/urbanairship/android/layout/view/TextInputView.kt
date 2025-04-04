/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.TextInputInfo
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.TextInputModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils.spToPx
import com.urbanairship.android.layout.util.ifNotEmpty
import com.urbanairship.android.layout.util.isActionUp
import com.urbanairship.android.layout.util.isLayoutRtl
import com.urbanairship.android.layout.util.resolveOptional
import com.urbanairship.android.layout.widget.TappableView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class TextInputView(
    context: Context,
    model: TextInputModel
) : AppCompatEditText(context), BaseView, TappableView {

    private val clicksChannel = Channel<Unit>(UNLIMITED)

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
        movementMethod = ScrollingMovementMethod()
        clipToOutline = true

        LayoutUtils.applyTextInputModel(this, model)

        model.contentDescription(context).ifNotEmpty { contentDescription = it }

        model.listener = object : TextInputModel.Listener {
            override fun restoreValue(value: String) {
                if (text.isNullOrEmpty()) setText(value)
            }

            override fun setVisibility(visible: Boolean) {
                this@TextInputView.isVisible = visible
            }

            override fun setEnabled(enabled: Boolean) {
                this@TextInputView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@TextInputView, old, new)
            }

            override fun onStateUpdated(state: State.Layout) {
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
                    setCompoundDrawables(endDrawable, null, null, null)
                } else {
                    setCompoundDrawables(null, null, endDrawable, null)
                }
            }
        }

        setOnTouchListener(touchListener)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        outAttrs.imeOptions = outAttrs.imeOptions or
                (EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        return super.onCreateInputConnection(outAttrs)
    }

    override fun taps(): Flow<Unit> = clicksChannel.receiveAsFlow()
}
