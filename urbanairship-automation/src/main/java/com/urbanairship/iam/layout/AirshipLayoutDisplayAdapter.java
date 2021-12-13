/* Copyright Airship and Contributors */

package com.urbanairship.iam.layout;

import android.content.Context;
import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.BasePayload;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.android.layout.util.UrlInfo;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.ForegroundDisplayAdapter;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageWebViewClient;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.Network;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Supplier;

/**
 * Airship layout display adapter.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipLayoutDisplayAdapter extends ForegroundDisplayAdapter {

    @VisibleForTesting
    interface DisplayRequestCallback {

        DisplayRequest prepareDisplay(@NonNull BasePayload basePayload) throws DisplayException;

    }

    private static DisplayRequestCallback DEFAULT_CALLBACK = Thomas::prepareDisplay;

    private final InAppMessage message;
    private final AirshipLayoutDisplayContent displayContent;
    private final DisplayRequestCallback prepareDisplayCallback;
    private final Supplier<Boolean> isConnectedSupplier;
    private final UrlAllowList urlAllowList;
    private final List<UrlInfo> urlInfoList;
    private final Map<String, String> assetCacheMap = new HashMap<>();
    private DisplayRequest displayRequest;


    @VisibleForTesting
    AirshipLayoutDisplayAdapter(@NonNull InAppMessage message,
                                @NonNull AirshipLayoutDisplayContent displayContent,
                                @NonNull DisplayRequestCallback prepareDisplayCallback,
                                @NonNull UrlAllowList urlAllowList,
                                @NonNull Supplier<Boolean> isConnectedSupplier) {

        this.message = message;
        this.displayContent = displayContent;
        this.prepareDisplayCallback = prepareDisplayCallback;
        this.urlAllowList = urlAllowList;
        this.isConnectedSupplier = isConnectedSupplier;
        this.urlInfoList = UrlInfo.from(displayContent.getPayload().getView());
    }

    /**
     * Creates a new display adapter.
     *
     * @param message The in-app message.
     * @return The adapter.
     */
    @NonNull
    public static AirshipLayoutDisplayAdapter newAdapter(@NonNull InAppMessage message) {
        AirshipLayoutDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new AirshipLayoutDisplayAdapter(
                message,
                displayContent,
                DEFAULT_CALLBACK,
                UAirship.shared().getUrlAllowList(),
                Network::isConnected
        );
    }

    @PrepareResult
    @Override
    public int onPrepare(@NonNull Context context, @NonNull Assets assets) {
        assetCacheMap.clear();
        for (UrlInfo urlInfo : this.urlInfoList) {
            if (!urlAllowList.isAllowed(urlInfo.getUrl(), UrlAllowList.SCOPE_OPEN_URL)) {
                Logger.error("Url not allowed: %s. Unable to display message %s.", urlInfo.getUrl(), message.getName());
                return CANCEL;
            }

            if (urlInfo.getType() == UrlInfo.UrlType.IMAGE) {
                File file = assets.file(urlInfo.getUrl());
                if (file.exists()) {
                    assetCacheMap.put(urlInfo.getUrl(), Uri.fromFile(file).toString());
                }
            }
        }

        try {
            this.displayRequest = this.prepareDisplayCallback.prepareDisplay(displayContent.getPayload());
        } catch (DisplayException e) {
            Logger.error("Unable to display layout", e);
            return InAppMessageAdapter.CANCEL;
        }
        return InAppMessageAdapter.OK;
    }

    @Override
    public boolean isReady(@NonNull Context context) {
        boolean isConnected = isConnectedSupplier.get();

        for (UrlInfo urlInfo : this.urlInfoList) {
            switch (urlInfo.getType()) {
                case VIDEO:
                case WEB_PAGE:
                    if (!isConnected) {
                        Logger.error("Message not ready. Device is not connected and the message contains a webpage or video.", urlInfo.getUrl(), message);
                        return false;
                    }
                    break;

                case IMAGE:
                    if (assetCacheMap.get(urlInfo.getUrl()) != null) {
                        continue;
                    }

                    if (!isConnected) {
                        Logger.error("Message not ready. Device is not connected and the message contains a webpage or video.", urlInfo.getUrl(), message);
                        return false;
                    }
                    break;
            }
        }

        return true;
    }

    @Override
    public void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler) {
        this.displayRequest.setListener(new Listener(message, displayHandler))
                           .setImageCache(new AssetImageCache(assetCacheMap))
                           .setWebViewClientFactory(() -> new InAppMessageWebViewClient(message))
                           .display(context);
    }

    @Override
    public void onFinish(@NonNull Context context) {
    }

    private static class AssetImageCache implements ImageCache {

        private final Map<String, String> assetCacheMap;

        private AssetImageCache(Map<String, String> assetCacheMap) {
            this.assetCacheMap = assetCacheMap;
        }

        @Nullable
        @Override
        public String get(@NonNull String url) {
            return assetCacheMap.get(url);
        }

    }

    private static class Listener implements ThomasListener {

        private final InAppMessage message;
        private final DisplayHandler displayHandler;
        private final String scheduleId;
        private final Map<String, Set<Integer>> viewedPageIndex = new HashMap<>();

        private Listener(@NonNull InAppMessage message, @NonNull DisplayHandler displayHandler) {
            this.message = message;
            this.displayHandler = displayHandler;
            this.scheduleId = displayHandler.getScheduleId();
        }

        @Override
        public void onPageView(@NonNull PagerData pagerData, @Nullable LayoutData layoutData) {
            Set<Integer> indexes = viewedPageIndex.get(pagerData.getIdentifier());
            if (indexes == null) {
                indexes = new HashSet<>();
                viewedPageIndex.put(pagerData.getIdentifier(), indexes);
            }

            if (indexes.contains(pagerData.getIndex())) {
                return;
            }

            indexes.add(pagerData.getIndex());

            InAppReportingEvent event = InAppReportingEvent.pageView(scheduleId, message, pagerData)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onPageSwipe(@NonNull PagerData pagerData, int toIndex, int fromIndex, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.pageSwipe(scheduleId, message, pagerData, toIndex, fromIndex)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onButtonTap(@NonNull String buttonId, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.buttonTap(scheduleId, message, buttonId)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onDismiss(long displayTime) {
            ResolutionInfo resolutionInfo = ResolutionInfo.dismissed();
            InAppReportingEvent event = InAppReportingEvent.resolution(scheduleId, message, displayTime, resolutionInfo);
            displayHandler.addEvent(event);
            displayHandler.notifyFinished(resolutionInfo);
        }

        @Override
        public void onDismiss(@NonNull String buttonId, @Nullable String buttonDescription, boolean cancel, long displayTime, @Nullable LayoutData layoutData) {
            ResolutionInfo resolutionInfo = ResolutionInfo.buttonPressed(buttonId, buttonDescription, cancel);
            InAppReportingEvent event = InAppReportingEvent.resolution(scheduleId, message, displayTime, resolutionInfo)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
            displayHandler.notifyFinished(resolutionInfo);

            if (cancel) {
                displayHandler.cancelFutureDisplays();
            }
        }

        @Override
        public void onFormResult(@NonNull FormData<?> formData, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.formResult(scheduleId, message, formData)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

        @Override
        public void onFormDisplay(@NonNull String formId, @Nullable LayoutData layoutData) {
            InAppReportingEvent event = InAppReportingEvent.formDisplay(scheduleId, message, formId)
                                                           .setLayoutData(layoutData);

            displayHandler.addEvent(event);
        }

    }

}
