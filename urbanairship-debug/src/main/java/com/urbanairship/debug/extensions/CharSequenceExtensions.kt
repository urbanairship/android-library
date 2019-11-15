package com.urbanairship.debug.extensions

import android.content.Context
import android.widget.Toast
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ClipboardAction
import com.urbanairship.debug.R

fun CharSequence.copyToClipboard(context:Context, toast:Boolean = true){
    ActionRunRequest.createRequest(ClipboardAction.DEFAULT_REGISTRY_NAME)
            .setValue(this)
            .run { _, _ ->
                if (toast) {
                    Toast.makeText(context, context.getString(R.string.ua_toast_clipboard), Toast.LENGTH_SHORT)
                            .show()
                }
            }
}