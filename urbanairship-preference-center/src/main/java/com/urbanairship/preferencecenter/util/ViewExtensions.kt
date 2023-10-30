package com.urbanairship.preferencecenter.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.urbanairship.UAirship
import com.urbanairship.images.ImageRequestOptions

internal fun TextView.setTextOrHide(text: String?) {
    this.text = text
    this.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
}

internal fun ImageView.loadImage(url: String, options: ImageRequestOptions.Builder.() -> Unit = {}) {
    UAirship.shared { airship ->
        val requestOptions = ImageRequestOptions.newBuilder(url).apply(options).build()
        airship.imageLoader.load(context, this, requestOptions)
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
