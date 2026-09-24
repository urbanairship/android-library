package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.info.HasBackgrounds
import com.urbanairship.android.layout.info.HasParallaxBackgrounds
import com.urbanairship.android.layout.info.ImageButtonInfo
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.info.StackImageButtonInfo
import com.urbanairship.android.layout.info.StackImageViewInfo
import com.urbanairship.android.layout.info.StackItemInfo
import com.urbanairship.android.layout.info.ViewGroupInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.info.WebViewInfo
import com.urbanairship.android.layout.property.Background
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

            // A `backgrounds` media layer is as much a part of the scene as an in-flow one: it
            // has to be prefetched, and it has to count toward whether the scene can be shown
            // without a network. The host itself is some other kind of view, so this runs
            // alongside the branch below rather than as one of its cases.
            //
            // That includes a layer nothing in the scene interacts with. A message is not shown
            // at all when its assets are missing, and which of them the author considered
            // essential is not something this can know -- a full-bleed backdrop may well be the
            // whole design. Deciding one is decorative on the author's behalf is the call not to
            // make.
            backgroundLayers(info)
                .filterIsInstance<Background.MediaBackground>()
                // Every media layer carries a real `MediaInfo`, so its urls, selectors and
                // overrides are collected through exactly the same branch an in-flow `media`
                // view goes through.
                .forEach { result.addAll(from(it.mediaInfo)) }

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
                is StackImageViewInfo -> {
                    for (item in info.items) {
                        if (item is StackItemInfo.ImageItem) {
                            result.add(UrlInfo(UrlType.IMAGE, item.imageUrl))
                            item.urlSelectors.mapTo(result) { UrlInfo(UrlType.IMAGE, it.url) }
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

        /** A view's own `backgrounds`, whether or not they can carry `parallax`. */
        private fun backgroundLayers(info: ViewInfo): List<Background> = when (info) {
            is HasBackgrounds -> info.backgrounds.orEmpty()
            is HasParallaxBackgrounds -> info.backgrounds.orEmpty().map { it.background }
            else -> emptyList()
        }
    }
}
