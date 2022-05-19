/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.json.JsonValue;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class ReportingEvent extends Event {

    public enum ReportType {
        PAGE_VIEW,
        PAGE_SWIPE,
        BUTTON_TAP,
        OUTSIDE_DISMISS,
        BUTTON_DISMISS,
        FORM_RESULT,
        FORM_DISPLAY
    }

    @NonNull
    private final ReportType reportType;

    protected ReportingEvent(@NonNull ReportType reportType) {
        super(EventType.REPORTING_EVENT);
        this.reportType = reportType;
    }

    @NonNull
    public ReportType getReportType() {
        return reportType;
    }

    /**
     * Bubbled up to the top level when a pager changes page.
     */
    public static class PageView extends PagerReportingEvent {

        private final long displayedAt;

        public PageView(@NonNull PagerData pagerData, long displayedAt) {
            super(ReportType.PAGE_VIEW, pagerData);
            this.displayedAt = displayedAt;
        }

        public long getDisplayedAt() {
            return displayedAt;
        }

        @NonNull
        @Override
        public String toString() {
            return "ReportingEvent.PageView{" +
                    "pagerData=" + getPagerData() +
                    ", displayedAt=" + getDisplayedAt() +
                    '}';
        }

    }

    /**
     * Bubbled up to the top level when a pager changes page due to a swipe.
     */
    public static class PageSwipe extends PagerReportingEvent {

        private final int fromPageIndex;
        private final int toPageIndex;
        private final String fromPageId;
        private final String toPageId;

        public PageSwipe(@NonNull PagerData pagerData, int fromPageIndex, @NonNull String fromPageId, int toPageIndex, @NonNull String toPageId) {
            super(ReportType.PAGE_SWIPE, pagerData);
            this.fromPageIndex = fromPageIndex;
            this.fromPageId = fromPageId;
            this.toPageIndex = toPageIndex;
            this.toPageId = toPageId;
        }

        public int getFromPageIndex() {
            return fromPageIndex;
        }

        @NonNull
        public String getFromPageId() {
            return fromPageId;
        }

        public int getToPageIndex() {
            return toPageIndex;
        }

        @NonNull
        public String getToPageId() {
            return toPageId;
        }

        @Override
        public String toString() {
            return "PageSwipe{" +
                    "fromPageIndex=" + fromPageIndex +
                    ", toPageIndex=" + toPageIndex +
                    ", fromPageId='" + fromPageId + '\'' +
                    ", toPageId='" + toPageId + '\'' +
                    '}';
        }

    }

    /**
     * Bubbled up to the top level when a button is tapped.
     */
    public static class ButtonTap extends ReportingEvent {

        @NonNull
        private final String buttonId;

        public ButtonTap(@NonNull String buttonId) {
            super(ReportType.BUTTON_TAP);
            this.buttonId = buttonId;
        }

        @NonNull
        public String getButtonId() {
            return buttonId;
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.ButtonTap{" +
                    "buttonId='" + buttonId + '\'' +
                    '}';
        }

    }

    /**
     * Bubbled up to the top level when the view is dismissed from outside the view.
     */
    public static class DismissFromOutside extends DismissReportingEvent {

        public DismissFromOutside(long displayTime) {
            super(ReportType.OUTSIDE_DISMISS, displayTime);
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.DismissFromOutside{" +
                    "displayTime=" + getDisplayTime() +
                    '}';
        }

    }

    /**
     * Bubbled up to the top level when the view is dismissed from a button.
     */
    public static class DismissFromButton extends DismissReportingEvent {

        @NonNull
        private final String buttonId;
        @Nullable
        private final String buttonDescription;
        private final boolean cancel;

        public DismissFromButton(
                @NonNull String buttonId,
                @Nullable String buttonDescription,
                boolean cancel,
                long displayTime
        ) {
            super(ReportType.BUTTON_DISMISS, displayTime);
            this.buttonId = buttonId;
            this.buttonDescription = buttonDescription;
            this.cancel = cancel;
        }

        @NonNull
        public String getButtonId() {
            return buttonId;
        }

        @Nullable
        public String getButtonDescription() {
            return buttonDescription;
        }

        public boolean isCancel() {
            return cancel;
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.DismissFromButton{" +
                    "buttonId='" + buttonId + '\'' +
                    ", buttonDescription='" + buttonDescription + '\'' +
                    ", cancel=" + cancel +
                    ", displayTime=" + getDisplayTime() +
                    '}';
        }

    }

    public static class FormResult extends ReportingEvent {

        @NonNull
        private final FormData.BaseForm formData;

        @NonNull
        private final FormInfo formInfo;

        @NonNull
        private final Map<AttributeName, JsonValue> attributes;

        public FormResult(@NonNull FormData.BaseForm formData, @NonNull FormInfo formInfo, @NonNull Map<AttributeName, JsonValue> attributes) {
            super(ReportType.FORM_RESULT);
            this.formData = formData;
            this.formInfo = formInfo;
            this.attributes = attributes;
        }

        @NonNull
        public FormData.BaseForm getFormData() {
            return formData;
        }

        @NonNull
        public FormInfo getFormInfo() {
            return formInfo;
        }

        @Override
        public String toString() {
            return "FormResult{" +
                    "formData=" + formData +
                    ", formInfo=" + formInfo +
                    ", attributes=" + attributes +
                    '}';
        }

        @NonNull
        public Map<AttributeName, JsonValue> getAttributes() {
            return attributes;
        }

    }

    public static class FormDisplay extends ReportingEvent {

        @NonNull
        private final FormInfo formInfo;

        public FormDisplay(@NonNull FormInfo formInfo) {
            super(ReportType.FORM_DISPLAY);
            this.formInfo = formInfo;
        }

        @NonNull
        public FormInfo getFormInfo() {
            return formInfo;
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.FormDisplay{" +
                    "formInfo='" + formInfo + '\'' +
                    '}';
        }

    }

    private abstract static class DismissReportingEvent extends ReportingEvent {

        private final long displayTime;

        public DismissReportingEvent(@NonNull ReportType type, long displayTime) {
            super(type);
            this.displayTime = displayTime;
        }

        public long getDisplayTime() {
            return displayTime;
        }

    }

    private abstract static class PagerReportingEvent extends ReportingEvent {

        @NonNull
        private final PagerData pagerData;

        public PagerReportingEvent(@NonNull ReportType type, @NonNull PagerData pagerData) {
            super(type);
            this.pagerData = pagerData;
        }

        @NonNull
        public PagerData getPagerData() {
            return pagerData;
        }

    }

}
