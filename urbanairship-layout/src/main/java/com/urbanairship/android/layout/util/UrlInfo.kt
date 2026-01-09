package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.property.Image
import com.urbanairship.android.layout.property.MediaType

public class UrlInfo(
    public val type: UrlType,
    public val url: String,
    public val requiresNetwork: Boolean = true
) {
    public enum class UrlType {
        WEB_PAGE,
        IMAGE,
        VIDEO
    }

    public companion object {

        public fun from(info: ViewInfo): Set<UrlInfo> {
            val result = mutableSetOf<UrlInfo>()

            when(info) {
                is MediaInfo -> {
                    when (info.mediaType) {
                        MediaType.IMAGE -> result.add(UrlInfo(UrlType.IMAGE, info.url))
                        MediaType.VIDEO,
                        MediaType.YOUTUBE,
                        MediaType.VIMEO -> result.add(UrlInfo(UrlType.VIDEO, info.url))
                    }
                }
                is ImageButtonInfo -> {
                    when(info.image) {
                        is Image.Url -> result.add(UrlInfo(UrlType.IMAGE, info.image.url))
                        is Image.Icon -> {}
                    }
                }
                is WebViewInfo -> {
                    result.add(UrlInfo(UrlType.WEB_PAGE, info.url))
                }
                is ViewGroupInfo<*> -> {
                    info.children
                        .map { from(it.info) }
                        .flatten()
                        .let(result::addAll)
                }
                else -> {}
            }

            return result
        }
    }
}
