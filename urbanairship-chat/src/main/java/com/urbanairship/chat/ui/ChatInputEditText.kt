package com.urbanairship.chat.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.urbanairship.chat.R

/** Custom `EditText` that supports image input via URI. */
internal class ChatInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    interface ChatInputListener {
        fun onImageSelected(imageUri: String)
        fun onActionDone()
        fun onTextChanged(text: String?)
    }

    private val animationDuration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
    private var bkgAnimator: Animator? = null

    private var listener: ChatInputListener? = null

    init {
        setOnEditorActionListener { _, actionId, event ->
            if (isEditorActionDone(actionId, event)) {
                listener?.onActionDone()
                true
            } else {
                false
            }
        }

        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateBackgroundDrawable(isVisible = text.isNullOrEmpty())
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                listener?.onTextChanged(s?.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        })
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val ic: InputConnection = super.onCreateInputConnection(editorInfo) ?: return null

        // Force Android to show actionSend instead of enter on the on-screen keyboard. This is
        // needed because the inputType set in the layout includes textMultiLine, which shows the
        // enter button regardless of the imeOptions for this view.
        editorInfo.imeOptions = editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()

        // Exclude GIFs if on API 27 and below, since we aren't able to animate them (yet).
        val mimeTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            arrayOf("image/png", "image/gif", "image/jpeg")
        } else {
            arrayOf("image/png", "image/jpeg")
        }

        EditorInfoCompat.setContentMimeTypes(editorInfo, mimeTypes)

        val callback =
                InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->
                    val lacksPermission = (flags and
                            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
                    // read and display inputContentInfo asynchronously
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission) {
                        try {
                            inputContentInfo.requestPermission()
                        } catch (e: Exception) {
                            return@OnCommitContentListener false // return false if failed
                        }
                    }

                    val result = imageSelected(inputContentInfo.linkUri)
                    inputContentInfo.releasePermission()
                    result
                }
        return InputConnectionCompat.createWrapper(ic, editorInfo, callback)
    }

    /**
     * Sets a `ChatInputListener` on this `View`, overwriting any previously registered listener.
     */
    internal fun setListener(listener: ChatInputListener) {
        this.listener = listener
    }

    private fun imageSelected(imageUri: Uri?): Boolean {
        if (imageUri == null) {
            return false
        }
        return listener?.let {
            it.onImageSelected(imageUri.toString())
            true
        } ?: false
    }

    /**
     * Fades the background drawable in and out for API 19+, falling back to setting visibility on
     * older versions.
     */
    private fun updateBackgroundDrawable(isVisible: Boolean) {
        val (start, end) = if (isVisible) {
            (background?.alpha ?: 0) to 255
        } else {
            (background?.alpha ?: 255) to 0
        }

        bkgAnimator?.cancel()
        bkgAnimator = ObjectAnimator.ofInt(background, "alpha", start, end).apply {
            duration = animationDuration
            start()
        }
    }

    private fun isEditorActionDone(actionId: Int, event: KeyEvent?): Boolean =
        when (actionId) {
            // Return true if the user pressed the done or send action on the on-screen keyboard.
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_SEND -> true
            // Return true if the user pressed the 'Enter' key on a physical keyboard.
            EditorInfo.IME_ACTION_UNSPECIFIED ->
                event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER
            else -> false
        }
}
