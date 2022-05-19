/* Copyright Airship and Contributors */

package com.urbanairship.iam.events;

import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

/**
 * In-app automation reporting.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InAppReportingEvent {

    public static class PageViewSummary implements JsonSerializable {

        private final String identifier;
        private final int index;
        private final long durationMs;

        public PageViewSummary(int index, @NonNull String identifier, long durationMs) {
            this.index = index;
            this.identifier = identifier;
            this.durationMs = durationMs;
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(PAGE_ID, identifier)
                          .put(PAGE_INDEX, index)
                          .put(PAGE_VIEW_DISPLAY_TIME, Event.millisecondsToSecondsString(durationMs))
                          .build()
                          .toJsonValue();
        }

    }

    // Event types
    @NonNull
    public static final String TYPE_RESOLUTION = "in_app_resolution";
    @NonNull
    public static final String TYPE_DISPLAY = "in_app_display";
    @NonNull
    public static final String TYPE_PAGE_VIEW = "in_app_page_view";
    @NonNull
    public static final String TYPE_PAGE_SWIPE = "in_app_page_swipe";
    @NonNull
    public static final String TYPE_FORM_DISPLAY = "in_app_form_display";
    @NonNull
    public static final String TYPE_FORM_RESULT = "in_app_form_result";
    @NonNull
    public static final String TYPES_PAGER_SUMMARY = "in_app_pager_summary";
    @NonNull
    public static final String TYPES_PAGER_COMPLETED = "in_app_pager_completed";
    @NonNull
    public static final String TYPE_BUTTON_TAP = "in_app_button_tap";
    @NonNull
    public static final String TYPE_PERMISSION_RESULT_EVENT = "in_app_permission_result";

    // Permission result keys
    private static final String PERMISSION_KEY = "permission";
    private static final String STARTING_PERMISSION_STATUS_KEY = "starting_permission_status";
    private static final String ENDING_PERMISSION_STATUS_KEY = "ending_permission_status";

    // Form keys
    private static final String FORM_ID = "form_identifier";
    private static final String FORM_TYPE_KEY = "form_type";
    private static final String FORM_RESPONSE_TYPE_KEY = "form_response_type";

    private static final String FORMS = "forms";

    // Pager keys
    private static final String PAGER_ID = "pager_identifier";
    private static final String PAGE_INDEX = "page_index";
    private static final String PAGE_ID = "page_identifier";
    private static final String PAGER_COUNT = "page_count";
    private static final String PAGER_VIEWED_COUNT = "viewed_count";
    private static final String PAGER_VIEWED_PAGES = "viewed_pages";
    private static final String PAGER_COMPLETED = "completed";
    private static final String PAGER_TO_INDEX = "to_page_index";
    private static final String PAGER_TO_ID = "to_page_identifier";
    private static final String PAGER_FROM_INDEX = "from_page_index";
    private static final String PAGER_FROM_ID = "from_page_identifier";
    private static final String PAGE_VIEW_DISPLAY_TIME = "display_time";

    // Button keys
    private static final String BUTTON_IDENTIFIER = "button_identifier";

    // Resolution keys
    private static final String RESOLUTION = "resolution";
    private static final String RESOLUTION_TYPE = "type";
    private static final String LEGACY_MESSAGE_REPLACED = "replaced";
    private static final String LEGACY_MESSAGE_DIRECT_OPEN = "direct_open";
    private static final String DISPLAY_TIME = "display_time";
    private static final String BUTTON_ID = "button_id";
    private static final String BUTTON_DESCRIPTION = "button_description";
    private static final String REPLACEMENT_ID = "replacement_id";

    // Common keys
    private static final String ID = "id";
    private static final String CONVERSION_SEND_ID = "conversion_send_id";
    private static final String CONVERSION_METADATA = "conversion_metadata";
    private static final String SOURCE = "source";
    private static final String CONTEXT = "context";
    private static final String LOCALE = "locale";

    // Context keys
    private static final String REPORTING_CONTEXT = "reporting_context";
    private static final String REPORTING_CONTEXT_FORM = "form";
    private static final String REPORTING_CONTEXT_FORM_ID = "identifier";
    private static final String REPORTING_CONTEXT_FORM_SUBMITTED = "submitted";
    private static final String REPORTING_CONTEXT_FORM_TYPE = "type";
    private static final String REPORTING_CONTEXT_FORM_RESPONSE_TYPE = "response_type";

    private static final String REPORTING_CONTEXT_PAGER = "pager";
    private static final String REPORTING_CONTEXT_PAGER_ID = "identifier";
    private static final String REPORTING_CONTEXT_PAGER_COUNT = "count";

    private static final String REPORTING_CONTEXT_BUTTON = "button";
    private static final String REPORTING_CONTEXT_BUTTON_ID = "identifier";

    // ID keys
    private static final String MESSAGE_ID = "message_id";
    private static final String CAMPAIGNS = "campaigns";

    // Source
    private static final String SOURCE_URBAN_AIRSHIP = "urban-airship";
    private static final String SOURCE_APP_DEFINED = "app-defined";

    private final String type;
    private final String scheduleId;
    @InAppMessage.Source
    private final String source;
    private final Map<String, JsonValue> renderedLocale;

    private JsonValue campaigns;
    private JsonValue reportingContext;
    private LayoutData layoutState;
    private JsonMap overrides;

    private InAppReportingEvent(@NonNull String type, @NonNull String scheduleId, @NonNull InAppMessage message) {
        this.type = type;
        this.scheduleId = scheduleId;
        this.source = message.getSource();
        this.renderedLocale = message.getRenderedLocale();
    }

    private InAppReportingEvent(@NonNull String type, @NonNull String scheduleId, @NonNull @InAppMessage.Source String source) {
        this.type = type;
        this.scheduleId = scheduleId;
        this.source = source;
        this.renderedLocale = null;
    }

    public static InAppReportingEvent display(@NonNull String scheduleId, @NonNull InAppMessage message) {
        return new InAppReportingEvent(TYPE_DISPLAY, scheduleId, message);
    }

    public static InAppReportingEvent interrupted(@NonNull String scheduleId, @NonNull @InAppMessage.Source String source) {
        JsonMap resolutionData = resolutionData(ResolutionInfo.dismissed(), 0);
        return new InAppReportingEvent(TYPE_RESOLUTION, scheduleId, source)
                .setOverrides(JsonMap.newBuilder().put(RESOLUTION, resolutionData).build());
    }

    public static InAppReportingEvent resolution(@NonNull String scheduleId,
                                                 @NonNull InAppMessage message,
                                                 long displayMilliseconds,
                                                 @NonNull ResolutionInfo resolutionInfo) {

        return new InAppReportingEvent(TYPE_RESOLUTION, scheduleId, message)
                .setOverrides(JsonMap.newBuilder().put(RESOLUTION, resolutionData(resolutionInfo, displayMilliseconds)).build());
    }

    public static InAppReportingEvent legacyReplaced(@NonNull String scheduleId, @NonNull String newId) {
        JsonMap resolutionInfo = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, LEGACY_MESSAGE_REPLACED)
                                        .put(REPLACEMENT_ID, newId)
                                        .build();

        return new InAppReportingEvent(TYPE_RESOLUTION, scheduleId, InAppMessage.SOURCE_LEGACY_PUSH)
                .setOverrides(JsonMap.newBuilder().put(RESOLUTION, resolutionInfo).build());
    }

    public static InAppReportingEvent legacyPushOpened(@NonNull String scheduleId) {
        JsonMap resolutionInfo = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, LEGACY_MESSAGE_DIRECT_OPEN)
                                        .build();

        return new InAppReportingEvent(TYPE_RESOLUTION, scheduleId, InAppMessage.SOURCE_LEGACY_PUSH)
                .setOverrides(JsonMap.newBuilder().put(RESOLUTION, resolutionInfo).build());
    }

    public static InAppReportingEvent permissionResultEvent(@NonNull String scheduleId,
                                                            @NonNull InAppMessage message,
                                                            @NonNull Permission permission,
                                                            @NonNull PermissionStatus before,
                                                            @NonNull PermissionStatus after) {

        return new InAppReportingEvent(TYPE_PERMISSION_RESULT_EVENT, scheduleId, message)
                .setOverrides(JsonMap.newBuilder()
                                     .put(PERMISSION_KEY, permission)
                                     .put(STARTING_PERMISSION_STATUS_KEY, before)
                                     .put(ENDING_PERMISSION_STATUS_KEY, after)
                                     .build());
    }

    public static InAppReportingEvent formDisplay(@NonNull String scheduleId,
                                                  @NonNull InAppMessage message,
                                                  @NonNull FormInfo formInfo) {
        return new InAppReportingEvent(TYPE_FORM_DISPLAY, scheduleId, message)
                .setOverrides(JsonMap.newBuilder()
                                     .put(FORM_ID, formInfo.getIdentifier())
                                     .put(FORM_RESPONSE_TYPE_KEY, formInfo.getFormResponseType())
                                     .put(FORM_TYPE_KEY, formInfo.getFormType())
                                     .build());
    }

    public static InAppReportingEvent formResult(@NonNull String scheduleId,
                                                 @NonNull InAppMessage message,
                                                 @NonNull FormData.BaseForm formData) {

        return new InAppReportingEvent(TYPE_FORM_RESULT, scheduleId, message)
                .setOverrides(JsonMap.newBuilder().put(FORMS, formData).build());
    }

    public static InAppReportingEvent buttonTap(@NonNull String scheduleId,
                                                @NonNull InAppMessage message,
                                                @NonNull String buttonId) {
        return new InAppReportingEvent(TYPE_BUTTON_TAP, scheduleId, message)
                .setOverrides(JsonMap.newBuilder().put(BUTTON_IDENTIFIER, buttonId).build());
    }

    public static InAppReportingEvent pageView(@NonNull String scheduleId,
                                               @NonNull InAppMessage message,
                                               @NonNull PagerData pagerData,
                                               int viewCount) {
        return new InAppReportingEvent(TYPE_PAGE_VIEW, scheduleId, message)
                .setOverrides(JsonMap.newBuilder()
                                     .put(PAGER_COMPLETED, pagerData.isCompleted())
                                     .put(PAGER_ID, pagerData.getIdentifier())
                                     .put(PAGER_COUNT, pagerData.getCount())
                                     .put(PAGE_INDEX, pagerData.getIndex())
                                     .put(PAGE_ID, pagerData.getPageId())
                                     .put(PAGER_VIEWED_COUNT, viewCount)
                                     .build());
    }

    public static InAppReportingEvent pageSwipe(@NonNull String scheduleId,
                                                @NonNull InAppMessage message,
                                                @NonNull PagerData pagerData,
                                                int toPageIndex,
                                                @NonNull String toPageId,
                                                int fromPageIndex,
                                                @NonNull String fromPageId) {

        return new InAppReportingEvent(TYPE_PAGE_SWIPE, scheduleId, message)
                .setOverrides(JsonMap.newBuilder()
                                     .put(PAGER_ID, pagerData.getIdentifier())
                                     .put(PAGER_TO_INDEX, toPageIndex)
                                     .put(PAGER_TO_ID, toPageId)
                                     .put(PAGER_FROM_INDEX, fromPageIndex)
                                     .put(PAGER_FROM_ID, fromPageId)
                                     .build());
    }

    public static InAppReportingEvent pagerCompleted(@NonNull String scheduleId,
                                                     @NonNull InAppMessage message,
                                                     @NonNull PagerData pagerData) {

        return new InAppReportingEvent(TYPES_PAGER_COMPLETED, scheduleId, message)
                .setOverrides(JsonMap.newBuilder()
                                     .put(PAGER_ID, pagerData.getIdentifier())
                                     .put(PAGE_INDEX, pagerData.getIndex())
                                     .put(PAGE_ID, pagerData.getPageId())
                                     .put(PAGER_COUNT, pagerData.getCount())
                                     .build());
    }

    public static InAppReportingEvent pagerSummary(@NonNull String scheduleId,
                                                   @NonNull InAppMessage message,
                                                   @NonNull PagerData pagerData,
                                                   @NonNull List<PageViewSummary> pageViews) {

        return new InAppReportingEvent(TYPES_PAGER_SUMMARY, scheduleId, message)
                .setOverrides(JsonMap.newBuilder()
                                     .put(PAGER_ID, pagerData.getIdentifier())
                                     .put(PAGER_COUNT, pagerData.getCount())
                                     .put(PAGER_COMPLETED, pagerData.isCompleted())
                                     .putOpt(PAGER_VIEWED_PAGES, pageViews)
                                     .build());
    }

    public InAppReportingEvent setCampaigns(@Nullable JsonValue campaigns) {
        this.campaigns = campaigns;
        return this;
    }

    public InAppReportingEvent setLayoutData(@Nullable LayoutData layoutState) {
        this.layoutState = layoutState;
        return this;
    }

    public InAppReportingEvent setReportingContext(@Nullable JsonValue reportingContext) {
        this.reportingContext = reportingContext;
        return this;
    }

    private InAppReportingEvent setOverrides(JsonMap overrides) {
        this.overrides = overrides;
        return this;
    }

    public void record(Analytics analytics) {
        boolean isAppDefined = InAppMessage.SOURCE_APP_DEFINED.equals(source);
        JsonMap.Builder builder = JsonMap.newBuilder()
                                         .put(ID, createEventId(scheduleId, source, campaigns))
                                         .put(SOURCE, isAppDefined ? SOURCE_APP_DEFINED : SOURCE_URBAN_AIRSHIP)
                                         .putOpt(CONVERSION_SEND_ID, analytics.getConversionSendId())
                                         .putOpt(CONVERSION_METADATA, analytics.getConversionMetadata())
                                         .put(CONTEXT, contextData(layoutState, reportingContext));

        if (renderedLocale != null) {
            builder.putOpt(LOCALE, renderedLocale);
        }

        if (this.overrides != null) {
            builder.putAll(overrides);
        }

        analytics.addEvent(new AnalyticsEvent(type, builder.build()));
    }

    private static JsonMap resolutionData(ResolutionInfo resolutionInfo, long displayMilliseconds) {
        displayMilliseconds = displayMilliseconds > 0 ? displayMilliseconds : 0;

        JsonMap.Builder resolutionDataBuilder = JsonMap.newBuilder()
                                                       .put(RESOLUTION_TYPE, resolutionInfo.getType())
                                                       .put(DISPLAY_TIME, Event.millisecondsToSecondsString(displayMilliseconds));

        if (ResolutionInfo.RESOLUTION_BUTTON_CLICK.equals(resolutionInfo.getType()) && resolutionInfo.getButtonInfo() != null) {
            String description = resolutionInfo.getButtonInfo().getLabel().getText();
            resolutionDataBuilder.put(BUTTON_ID, resolutionInfo.getButtonInfo().getId())
                                 .put(BUTTON_DESCRIPTION, description);
        }
        return resolutionDataBuilder.build();
    }

    private static JsonMap contextData(@Nullable LayoutData layoutState, @Nullable JsonValue reportingContext) {
        JsonMap.Builder contextBuilder = JsonMap.newBuilder()
                                                .put(REPORTING_CONTEXT, reportingContext);

        if (layoutState != null) {
            FormInfo formInfo = layoutState.getFormInfo();
            if (formInfo != null) {
                boolean isSubmitted = formInfo.getFormSubmitted() != null ? formInfo.getFormSubmitted() : false;
                JsonMap formContext = JsonMap.newBuilder()
                                             .put(REPORTING_CONTEXT_FORM_ID, formInfo.getIdentifier())
                                             .put(REPORTING_CONTEXT_FORM_SUBMITTED, isSubmitted)
                                             .put(REPORTING_CONTEXT_FORM_RESPONSE_TYPE, formInfo.getFormResponseType())
                                             .put(REPORTING_CONTEXT_FORM_TYPE, formInfo.getFormType())
                                             .build();
                contextBuilder.put(REPORTING_CONTEXT_FORM, formContext);
            }

            PagerData pagerData = layoutState.getPagerData();
            if (pagerData != null) {
                JsonMap pagerContext = JsonMap.newBuilder()
                                              .put(REPORTING_CONTEXT_PAGER_ID, pagerData.getIdentifier())
                                              .put(REPORTING_CONTEXT_PAGER_COUNT, pagerData.getCount())
                                              .put(PAGE_INDEX, pagerData.getIndex())
                                              .put(PAGE_ID, pagerData.getPageId())
                                              .put(PAGER_COMPLETED, pagerData.isCompleted())
                                              .build();

                contextBuilder.put(REPORTING_CONTEXT_PAGER, pagerContext);
            }

            String buttonId = layoutState.getButtonIdentifier();
            if (buttonId != null) {
                JsonMap buttonContext = JsonMap.newBuilder()
                                             .put(REPORTING_CONTEXT_BUTTON_ID, buttonId)
                                             .build();
                contextBuilder.put(REPORTING_CONTEXT_BUTTON, buttonContext);
            }
        }

        JsonMap contextData = contextBuilder.build();
        return contextData.isEmpty() ? null : contextData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InAppReportingEvent event = (InAppReportingEvent) o;
        return ObjectsCompat.equals(type, event.type) && ObjectsCompat.equals(scheduleId, event.scheduleId) &&
                ObjectsCompat.equals(source, event.source) && ObjectsCompat.equals(renderedLocale, event.renderedLocale) &&
                ObjectsCompat.equals(campaigns, event.campaigns) && ObjectsCompat.equals(reportingContext, event.reportingContext) &&
                ObjectsCompat.equals(layoutState, event.layoutState) && ObjectsCompat.equals(overrides, event.overrides);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(type, scheduleId, source, renderedLocale, campaigns, reportingContext, layoutState, overrides);
    }

    @NonNull
    private static JsonValue createEventId(@NonNull String scheduleId, @NonNull @InAppMessage.Source String source, @Nullable JsonValue campaigns) {
        switch (source) {
            case InAppMessage.SOURCE_LEGACY_PUSH:
                return JsonValue.wrap(scheduleId);

            case InAppMessage.SOURCE_REMOTE_DATA:
                return JsonMap.newBuilder()
                              .put(MESSAGE_ID, scheduleId)
                              .put(CAMPAIGNS, campaigns)
                              .build()
                              .toJsonValue();

            case InAppMessage.SOURCE_APP_DEFINED:
                return JsonMap.newBuilder()
                              .put(MESSAGE_ID, scheduleId)
                              .build()
                              .toJsonValue();
        }

        return JsonValue.NULL;
    }

    private static class AnalyticsEvent extends Event {

        private final String type;
        private final JsonMap data;

        private AnalyticsEvent(@NonNull String type, @NonNull JsonMap data) {
            this.type = type;
            this.data = data;
        }

        @NonNull
        @Override
        public String getType() {
            return type;
        }

        @NonNull
        @Override
        public JsonMap getEventData() {
            return data;
        }

        @NonNull
        @Override
        public String toString() {
            return "AnalyticsEvent{" +
                    "type='" + type + '\'' +
                    ", data=" + data +
                    '}';
        }

    }

}
