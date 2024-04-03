/* Copyright Airship and Contributors */

package com.urbanairship.iam.layout;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.PermissionResultReceiver;
import com.urbanairship.actions.PromptPermissionAction;
import com.urbanairship.android.layout.AirshipEmbeddedViewManager;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.android.layout.util.UrlInfo;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.embedded.EmbeddedViewManager;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.ForegroundDisplayAdapter;
import com.urbanairship.iam.InAppActionUtils;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageWebViewClient;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.iam.events.InAppReportingEvent.PageViewSummary;
import com.urbanairship.UrlAllowList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.util.Network;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.urbanairship.iam.ResolutionInfo.buttonPressed;
import static com.urbanairship.iam.ResolutionInfo.dismissed;
import static com.urbanairship.iam.events.InAppReportingEvent.buttonTap;
import static com.urbanairship.iam.events.InAppReportingEvent.formDisplay;
import static com.urbanairship.iam.events.InAppReportingEvent.formResult;
import static com.urbanairship.iam.events.InAppReportingEvent.pageSwipe;
import static com.urbanairship.iam.events.InAppReportingEvent.pagerAction;
import static com.urbanairship.iam.events.InAppReportingEvent.pagerGesture;
import static com.urbanairship.iam.events.InAppReportingEvent.pagerSummary;
import static com.urbanairship.iam.events.InAppReportingEvent.permissionResultEvent;
import static com.urbanairship.iam.events.InAppReportingEvent.resolution;

/**
 * Airship layout display adapter.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipLayoutDisplayAdapter extends ForegroundDisplayAdapter {

    @VisibleForTesting
    interface DisplayRequestCallback {

        DisplayRequest prepareDisplay(
                @NonNull LayoutInfo payload,
                @NonNull JsonMap extras,
                @NonNull AirshipEmbeddedViewManager embeddedViewManager
        ) throws DisplayException;
    }

    private static final DisplayRequestCallback DEFAULT_CALLBACK = Thomas::prepareDisplay;

    private final InAppMessage message;
    private final AirshipLayoutDisplayContent displayContent;
    private final DisplayRequestCallback prepareDisplayCallback;
    private final Network network;
    private final UrlAllowList urlAllowList;
    private final Set<UrlInfo> urlInfoList;
    private final Map<String, String> assetCacheMap = new HashMap<>();
    private final AirshipEmbeddedViewManager embeddedViewManager;
    private DisplayRequest displayRequest;

    @VisibleForTesting
    AirshipLayoutDisplayAdapter(@NonNull InAppMessage message,
                                @NonNull AirshipLayoutDisplayContent displayContent,
                                @NonNull DisplayRequestCallback prepareDisplayCallback,
                                @NonNull UrlAllowList urlAllowList,
                                @NonNull Network network) {

        this.message = message;
        this.displayContent = displayContent;
        this.prepareDisplayCallback = prepareDisplayCallback;
        this.urlAllowList = urlAllowList;
        this.network = network;
        this.urlInfoList = UrlInfo.from(displayContent.getPayload().getView());
        this.embeddedViewManager = EmbeddedViewManager.INSTANCE;
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
                Network.shared()
        );
    }

    @PrepareResult
    @Override
    public int onPrepare(@NonNull Context context, @NonNull Assets assets) {
        assetCacheMap.clear();
        for (UrlInfo urlInfo : this.urlInfoList) {
            if (urlInfo.getType() == UrlInfo.UrlType.WEB_PAGE && !urlAllowList.isAllowed(urlInfo.getUrl(), UrlAllowList.SCOPE_OPEN_URL)) {
                UALog.e("Url not allowed: %s. Unable to display message %s.", urlInfo.getUrl(), message.getName());
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
            displayRequest = prepareDisplayCallback.prepareDisplay(
                    displayContent.getPayload(),
                    message.getExtras(),
                    embeddedViewManager
            );
        } catch (DisplayException e) {
            UALog.e("Unable to display layout", e);
            return InAppMessageAdapter.CANCEL;
        }
        return InAppMessageAdapter.OK;
    }

    @Override
    public boolean isReady(@NonNull Context context) {
        if (!super.isReady(context)) {
            return false;
        }

        boolean isConnected = network.isConnected(context);

        for (UrlInfo urlInfo : this.urlInfoList) {
            switch (urlInfo.getType()) {
                case VIDEO:
                case WEB_PAGE:
                    if (!isConnected) {
                        UALog.e("Message not ready. Device is not connected and the message contains a webpage or video.", urlInfo.getUrl(), message);
                        return false;
                    }
                    break;

                case IMAGE:
                    if (assetCacheMap.get(urlInfo.getUrl()) != null) {
                        continue;
                    }

                    if (!isConnected) {
                        UALog.e("Message not ready. Device is not connected and the message contains a webpage or video.", urlInfo.getUrl(), message);
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
                           .setInAppActivityMonitor(InAppActivityMonitor.shared(context))
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
        private final Set<String> completedPagers = new HashSet<>();
        private final Map<String, PagerSummary> pagerSummaryMap = new HashMap<>();
        private final Map<String, Map<Integer, Integer>> pagerViewCounts = new HashMap<>();

        private Listener(@NonNull InAppMessage message, @NonNull DisplayHandler displayHandler) {
            this.message = message;
            this.displayHandler = displayHandler;
            this.scheduleId = displayHandler.getScheduleId();
        }

        @Override
        public void onPageView(@NonNull PagerData pagerData, @Nullable LayoutData layoutData, long displayedAt) {
            try {
                // View
                int viewCount = updatePageViewCount(pagerData);
                InAppReportingEvent viewed = InAppReportingEvent.pageView(scheduleId, message, pagerData, viewCount)
                                                                .setLayoutData(layoutData);
                displayHandler.addEvent(viewed);

                // Completed
                if (pagerData.isCompleted() && !completedPagers.contains(pagerData.getIdentifier())) {
                    completedPagers.add(pagerData.getIdentifier());

                    InAppReportingEvent completed = InAppReportingEvent.pagerCompleted(scheduleId, message, pagerData)
                                                                       .setLayoutData(layoutData);
                    displayHandler.addEvent(completed);
                }


                // Summary
                PagerSummary summary = this.pagerSummaryMap.get(pagerData.getIdentifier());
                if (summary == null) {
                    summary = new PagerSummary();
                    this.pagerSummaryMap.put(pagerData.getIdentifier(), summary);
                }
                summary.updatePagerData(pagerData, displayedAt);
            } catch (IllegalArgumentException e) {
                UALog.e("pageView InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onPageSwipe(@NonNull PagerData pagerData, int toPageIndex, @NonNull String toPageId, int fromPageIndex, @NonNull String fromPageId, @Nullable LayoutData layoutData) {
            try {
                InAppReportingEvent event = pageSwipe(scheduleId, message, pagerData, toPageIndex, toPageId, fromPageIndex, fromPageId)
                        .setLayoutData(layoutData);

                displayHandler.addEvent(event);
            } catch (IllegalArgumentException e) {
                UALog.e("pageSwipe InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onButtonTap(
                @NonNull String buttonId,
                @Nullable JsonValue reportingMetadata,
                @Nullable LayoutData layoutData
        ) {
            try {
                InAppReportingEvent event = buttonTap(scheduleId, message, buttonId, reportingMetadata)
                        .setLayoutData(layoutData);

                displayHandler.addEvent(event);
            } catch (IllegalArgumentException e) {
                UALog.e("buttonTap InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onDismiss(long displayTime) {
            try {
                ResolutionInfo resolutionInfo = dismissed();
                InAppReportingEvent event = resolution(scheduleId, message, displayTime, resolutionInfo);
                sendPageSummaryEvents(null, displayTime);
                displayHandler.addEvent(event);
                displayHandler.notifyFinished(resolutionInfo);
            } catch (IllegalArgumentException e) {
                UALog.e("dismissed info for resolution InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onDismiss(@NonNull String buttonId, @Nullable String buttonDescription, boolean cancel, long displayTime, @Nullable LayoutData layoutData) {
            try {
                ResolutionInfo resolutionInfo = buttonPressed(buttonId, buttonDescription, cancel);
                InAppReportingEvent event = resolution(scheduleId, message, displayTime, resolutionInfo)
                        .setLayoutData(layoutData);

                sendPageSummaryEvents(layoutData, displayTime);
                displayHandler.addEvent(event);
                displayHandler.notifyFinished(resolutionInfo);

                if (cancel) {
                    displayHandler.cancelFutureDisplays();
                }
            } catch (IllegalArgumentException e) {
                UALog.e("buttonPressed info for resolution InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onFormResult(@NonNull FormData.BaseForm formData, @Nullable LayoutData layoutData) {
            try {
                InAppReportingEvent event = formResult(scheduleId, message, formData)
                        .setLayoutData(layoutData);

                displayHandler.addEvent(event);
            } catch (IllegalArgumentException e) {
                UALog.e("formResult InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onFormDisplay(@NonNull FormInfo formInfo, @Nullable LayoutData layoutData) {
            try {
                InAppReportingEvent event = formDisplay(scheduleId, message, formInfo)
                        .setLayoutData(layoutData);

                displayHandler.addEvent(event);
            } catch (IllegalArgumentException e) {
                UALog.e("formDisplay InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onRunActions(@NonNull Map<String, JsonValue> actions, @Nullable LayoutData layoutData) {
            PermissionResultReceiver permissionResultReceiver = new PermissionResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onResult(@NonNull Permission permission, @NonNull PermissionStatus before, @NonNull PermissionStatus after) {
                    try {
                        InAppReportingEvent event = permissionResultEvent(scheduleId, message, permission, before, after)
                                .setLayoutData(layoutData);

                        displayHandler.addEvent(event);
                    } catch (IllegalArgumentException e) {
                        UALog.e("permissionResultEvent InAppReportingEvent is not valid!", e);
                    }
                }
            };

            InAppActionUtils.runActions(actions, new ActionRunRequestFactory(actionName -> {
                Bundle bundle = new Bundle();
                bundle.putParcelable(PromptPermissionAction.RECEIVER_METADATA, permissionResultReceiver);
                return ActionRunRequest.createRequest(actionName)
                                       .setMetadata(bundle);
            }));
        }

        @Override
        public void onPagerGesture(
                @NonNull String gestureId,
                @Nullable JsonValue reportingMetadata,
                @NonNull LayoutData state
        ) {
            try {
                InAppReportingEvent event = pagerGesture(scheduleId, message, gestureId, reportingMetadata)
                        .setLayoutData(state);
                displayHandler.addEvent(event);
            } catch (IllegalArgumentException e) {
                UALog.e("pagerGesture InAppReportingEvent is not valid!", e);
            }
        }

        @Override
        public void onPagerAutomatedAction(
                @NonNull String actionId,
                @Nullable JsonValue reportingMetadata,
                @NonNull LayoutData state
        ) {
            try {
                InAppReportingEvent event = pagerAction(scheduleId, message, actionId, reportingMetadata)
                        .setLayoutData(state);
                displayHandler.addEvent(event);
            } catch (IllegalArgumentException e) {
                UALog.e("onPagerAutomatedAction InAppReportingEvent is not valid!", e);
            }
        }

        /**
         * Updates the pager page view count map.
         *
         * @param data PagerData from the page view event.
         * @return the updated viewed count for the current page index.
         */
        private int updatePageViewCount(@NonNull PagerData data) {
            if (!pagerViewCounts.containsKey(data.getIdentifier())) {
                pagerViewCounts.put(data.getIdentifier(), new HashMap<>(data.getCount()));
            }
            Map<Integer, Integer> pageViews = pagerViewCounts.get(data.getIdentifier());

            if (pageViews != null && !pageViews.containsKey(data.getIndex())) {
                pageViews.put(data.getIndex(), 0);
            }

            Integer count = pageViews != null ? pageViews.get(data.getIndex()) : Integer.valueOf(0);
            count = count != null ? count + 1 : 1;

            if (pageViews != null) {
                pageViews.put(data.getIndex(), count);
            }
            return count;
        }

        private void sendPageSummaryEvents(@Nullable LayoutData layoutData, long displayTime) {
            for (Map.Entry<String, PagerSummary> summaryEntry : this.pagerSummaryMap.entrySet()) {
                PagerSummary summary = summaryEntry.getValue();
                summary.pageFinished(displayTime);
                if (summary.pagerData == null) {
                    continue;
                }
                try {
                    InAppReportingEvent event = pagerSummary(scheduleId, message, summary.pagerData, summary.pageViewSummaries)
                            .setLayoutData(layoutData);
                    displayHandler.addEvent(event);
                } catch (IllegalArgumentException e) {
                    UALog.e("pagerSummary InAppReportingEvent is not valid!", e);
                }
            }
        }
    }

    private static class PagerSummary {

        @Nullable
        private PagerData pagerData;
        private final List<PageViewSummary> pageViewSummaries = new ArrayList<>();

        private long pageUpdateTime;

        private void updatePagerData(PagerData data, long updateTime) {
            pageFinished(updateTime);
            this.pagerData = data;
            this.pageUpdateTime = updateTime;
        }

        private void pageFinished(long updateTime) {
            if (this.pagerData != null) {
                long duration = updateTime - pageUpdateTime;
                PageViewSummary summary = new PageViewSummary(pagerData.getIndex(), pagerData.getPageId(), duration);
                this.pageViewSummaries.add(summary);
            }
        }

    }
}
