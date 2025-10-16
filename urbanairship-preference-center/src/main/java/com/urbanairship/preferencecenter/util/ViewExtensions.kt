package com.urbanairship.preferencecenter.util

import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.parseAsHtml
import com.urbanairship.Airship
import com.urbanairship.images.ImageRequestOptions

internal fun TextView.setTextOrHide(text: String?) {
    this.text = text
    this.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
}

internal fun ImageView.loadImage(url: String, options: ImageRequestOptions.Builder.() -> Unit = {}) {
    Airship.onReady {
        val requestOptions = ImageRequestOptions.newBuilder(url).apply(options).build()
        imageLoader.load(context, this@loadImage, requestOptions)
    }
}

internal fun ImageView.loadImageOrHide(url: String?, options: ImageRequestOptions.Builder.() -> Unit = {}) {
    if (!url.isNullOrBlank()) {
        this.visibility = View.VISIBLE
        this.loadImage(url, options)
    } else {
        this.visibility = View.GONE
    }
}

internal fun TextView.setHtml(html: String, linkify: Boolean = true) {
    if (linkify) {
        movementMethod = LinkMovementMethod.getInstance()
        linksClickable = true
    }
    text = html.parseAsHtml()
}
