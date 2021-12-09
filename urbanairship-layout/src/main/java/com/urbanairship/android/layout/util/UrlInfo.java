package com.urbanairship.android.layout.util;

import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.ImageButtonModel;
import com.urbanairship.android.layout.model.LayoutModel;
import com.urbanairship.android.layout.model.MediaModel;
import com.urbanairship.android.layout.model.WebViewModel;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.property.MediaType;

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
    public static List<UrlInfo> from(@NonNull BaseModel model) {
        List<UrlInfo> urlInfos = new ArrayList<>();

        switch (model.getType()) {
            case MEDIA:
                MediaModel mediaModel = (MediaModel) model;

                switch (mediaModel.getMediaType()) {
                    case IMAGE:
                        urlInfos.add(new UrlInfo(UrlType.IMAGE, mediaModel.getUrl()));
                        break;
                    case VIDEO:
                    case YOUTUBE:
                        urlInfos.add(new UrlInfo(UrlType.VIDEO, mediaModel.getUrl()));
                        break;
                }
                break;

            case IMAGE_BUTTON:
                ImageButtonModel imageButtonModel = (ImageButtonModel) model;
                if (imageButtonModel.getImage().getType() == Image.Type.URL) {
                    String url = ((Image.Url) imageButtonModel.getImage()).getUrl();
                    urlInfos.add(new UrlInfo(UrlType.IMAGE, url));
                }
                break;

            case WEB_VIEW:
                WebViewModel webViewModel = (WebViewModel) model;
                urlInfos.add(new UrlInfo(UrlType.WEB_PAGE, webViewModel.getUrl()));
                break;
        }

        if (model instanceof LayoutModel) {
            for (BaseModel child : ((LayoutModel) model).getChildren()) {
                urlInfos.addAll(from(child));
            }
        }

        return urlInfos;
    }
}
