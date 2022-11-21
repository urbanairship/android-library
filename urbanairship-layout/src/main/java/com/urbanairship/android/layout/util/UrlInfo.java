package com.urbanairship.android.layout.util;

import com.urbanairship.android.layout.info.ImageButtonInfo;
import com.urbanairship.android.layout.info.ItemInfo;
import com.urbanairship.android.layout.info.MediaInfo;
import com.urbanairship.android.layout.info.ViewGroupInfo;
import com.urbanairship.android.layout.info.ViewInfo;
import com.urbanairship.android.layout.info.WebViewInfo;
import com.urbanairship.android.layout.property.Image;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class UrlInfo {

    public enum UrlType {
        WEB_PAGE,
        IMAGE,
        VIDEO
    }

    @NonNull
    private final UrlType type;
    @NonNull
    private final String url;

    UrlInfo(@NonNull UrlType type, @NonNull String url) {
        this.type = type;
        this.url = url;
    }

    @NonNull
    public UrlType getType() {
        return type;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public static List<UrlInfo> from(@NonNull ViewInfo info) {
        List<UrlInfo> urlInfos = new ArrayList<>();

        switch (info.getType()) {
            case MEDIA:
                MediaInfo mediaInfo = (MediaInfo) info;

                switch (mediaInfo.getMediaType()) {
                    case IMAGE:
                        urlInfos.add(new UrlInfo(UrlType.IMAGE, mediaInfo.getUrl()));
                        break;
                    case VIDEO:
                    case YOUTUBE:
                        urlInfos.add(new UrlInfo(UrlType.VIDEO, mediaInfo.getUrl()));
                        break;
                }
                break;

            case IMAGE_BUTTON:
                ImageButtonInfo imageButtonInfo = (ImageButtonInfo) info;
                if (imageButtonInfo.getImage().getType() == Image.Type.URL) {
                    String url = ((Image.Url) imageButtonInfo.getImage()).getUrl();
                    urlInfos.add(new UrlInfo(UrlType.IMAGE, url));
                }
                break;

            case WEB_VIEW:
                WebViewInfo webViewInfo = (WebViewInfo) info;
                urlInfos.add(new UrlInfo(UrlType.WEB_PAGE, webViewInfo.getUrl()));
                break;
        }

        if (info instanceof ViewGroupInfo<?>) {
            for (ItemInfo child : ((ViewGroupInfo<?>) info).getChildren()) {
                urlInfos.addAll(from(child.getInfo()));
            }
        }

        return urlInfos;
    }
}
