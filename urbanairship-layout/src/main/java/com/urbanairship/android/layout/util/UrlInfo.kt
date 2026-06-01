package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.StackImageButtonInfo
import com.urbanairship.android.layout.info.StackItemInfo
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
                    val urlType = when (info.mediaType) {
                        MediaType.IMAGE -> UrlType.IMAGE
                        MediaType.VIDEO,
                        MediaType.YOUTUBE,
                        MediaType.VIMEO -> UrlType.VIDEO
                    }
                    result.add(UrlInfo(urlType, info.url))
                    info.urlSelectors.mapTo(result) { UrlInfo(urlType, it.url) }
                    info.viewOverrides?.url?.forEach { it.value?.let { url -> result.add(UrlInfo(urlType, url)) } }
                    info.viewOverrides?.urlSelectors?.forEach { override ->
                        override.value?.mapTo(result) { UrlInfo(urlType, it.url) }
                    }
                }
                is ImageButtonInfo -> {
                    when(info.image) {
                        is Image.Url -> {
                            result.add(UrlInfo(UrlType.IMAGE, info.image.url))
                            info.image.urlSelectors.mapTo(result) { UrlInfo(UrlType.IMAGE, it.url) }
                        }
                        is Image.Icon -> {}
                    }
                    info.viewOverrides?.image?.forEach { override ->
                        (override.value as? Image.Url)?.let { img ->
                            result.add(UrlInfo(UrlType.IMAGE, img.url))
                            img.urlSelectors.mapTo(result) { UrlInfo(UrlType.IMAGE, it.url) }
                        }
                    }
                }
                is StackImageButtonInfo -> {
                    for (item in info.items) {
                        if (item is StackItemInfo.ImageItem) {
                            result.add(UrlInfo(UrlType.IMAGE, item.imageUrl))
                            item.urlSelectors.mapTo(result) { UrlInfo(UrlType.IMAGE, it.url) }
                        }
                    }
                    info.viewOverrides?.overrides?.forEach { override ->
                        override.value?.forEach { item ->
                            if (item is StackItemInfo.ImageItem) {
                                result.add(UrlInfo(UrlType.IMAGE, item.imageUrl))
                                item.urlSelectors.mapTo(result) { UrlInfo(UrlType.IMAGE, it.url) }
                            }
                        }
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
