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
    @NonNull
    private final LayoutData state;

    protected ReportingEvent(@NonNull ReportType reportType, @Nullable LayoutData state) {
        super(EventType.REPORTING_EVENT);
        this.reportType = reportType;
        this.state = state != null ? state : new LayoutData(null, null);
    }

    @NonNull
    public ReportType getReportType() {
        return reportType;
    }

    @NonNull
    public LayoutData getState() {
        return state;
    }

    public abstract ReportingEvent overrideState(@NonNull FormInfo formInfo);
    public abstract ReportingEvent overrideState(@NonNull PagerData pagerData);

    protected LayoutData copyState(@NonNull FormInfo formInfo) {
        return state.withFormInfo(formInfo);
    }

    protected LayoutData copyState(@NonNull PagerData data) {
        return state.withPagerData(data);
    }

    /**
     * Bubbled up to the top level when a pager changes page.
     */
    public static class PageView extends PagerReportingEvent {
        private final long displayedAt;

        public PageView(@NonNull PagerData pagerData, long displayedAt) {
            super(ReportType.PAGE_VIEW, pagerData, new LayoutData(null, pagerData));
            this.displayedAt = displayedAt;
        }

        private PageView(@NonNull PagerData pagerData, @Nullable LayoutData state, long displayedAt) {
            super(ReportType.PAGE_VIEW, pagerData, state);
            this.displayedAt = displayedAt;
        }

        @Override
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new PageView(getPagerData(), copyState(formInfo), displayedAt);
        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData pagerData) {
            return new PageView(getPagerData(), copyState(pagerData), displayedAt);
        }

        public long getDisplayedAt() {
            return displayedAt;
        }

        @NonNull
        @Override
        public String toString() {
            return "ReportingEvent.PageView{" +
                "pagerData=" + getPagerData() +
                ", state=" + getState() +
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
            this(pagerData, fromPageIndex, fromPageId, toPageIndex, toPageId, new LayoutData(null, pagerData));
        }

        private PageSwipe(@NonNull PagerData pagerData, int fromPageIndex, @NonNull String fromPageId, int toPageIndex, @NonNull String toPageId, @Nullable LayoutData state) {
            super(ReportType.PAGE_SWIPE, pagerData, state);
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
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new PageSwipe(getPagerData(), fromPageIndex, fromPageId, toPageIndex, toPageId, copyState(formInfo));
        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData data) {
            return new PageSwipe(getPagerData(), fromPageIndex, fromPageId, toPageIndex, toPageId, copyState(data));
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
            this(buttonId, null);
        }

        private ButtonTap(@NonNull String buttonId, @Nullable LayoutData state) {
            super(ReportType.BUTTON_TAP, state);
            this.buttonId = buttonId;
        }

        @NonNull
        public String getButtonId() {
            return buttonId;
        }

        @Override
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new ButtonTap(buttonId, copyState(formInfo));
        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData pagerData) {
            return new ButtonTap(buttonId, copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.ButtonTap{" +
                "buttonId='" + buttonId + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    /**
     * Bubbled up to the top level when the view is dismissed from outside the view.
     */
    public static class DismissFromOutside extends DismissReportingEvent {

        public DismissFromOutside(long displayTime) {
            this(displayTime, null);
        }

        public DismissFromOutside(long displayTime, @Nullable LayoutData state) {
            super(ReportType.OUTSIDE_DISMISS, displayTime, state);
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.DismissFromOutside{" +
                "displayTime=" + getDisplayTime() +
                '}';
        }

        @Override
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new DismissFromOutside(getDisplayTime(), copyState(formInfo));

        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData pagerData) {
            return new DismissFromOutside(getDisplayTime(), copyState(pagerData));
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
            this(buttonId, buttonDescription, cancel, displayTime, null);
        }

        public DismissFromButton(
            @NonNull String buttonId,
            @Nullable String buttonDescription,
            boolean cancel,
            long displayTime,
            @Nullable LayoutData state
        ) {
            super(ReportType.BUTTON_DISMISS, displayTime, state);

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
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new DismissFromButton(buttonId, buttonDescription, cancel, getDisplayTime(), copyState(formInfo));
        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData pagerData) {
            return new DismissFromButton(buttonId, buttonDescription, cancel, getDisplayTime(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.DismissFromButton{" +
                "buttonId='" + buttonId + '\'' +
                ", buttonDescription='" + buttonDescription + '\'' +
                ", cancel=" + cancel +
                ", state=" + getState() +
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
            this(formData, formInfo, new LayoutData(formInfo, null), attributes);
        }

        private FormResult(@NonNull FormData.BaseForm formData, @NonNull FormInfo formInfo, @Nullable LayoutData state, @NonNull Map<AttributeName, JsonValue> attributes) {
            super(ReportType.FORM_RESULT, state);
            this.formData = formData;
            this.formInfo  = formInfo;
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

        @Override
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new FormResult(getFormData(), getFormInfo(), copyState(formInfo), attributes);
        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData pagerData) {
            return new FormResult(getFormData(), getFormInfo(), copyState(pagerData), attributes);
        }
    }

    public static class FormDisplay extends ReportingEvent {
        @NonNull
        private final FormInfo formInfo;

        public FormDisplay(@NonNull FormInfo formInfo) {
            this(formInfo, new LayoutData(formInfo, null));
        }

        private FormDisplay(@NonNull FormInfo formInfo, @Nullable LayoutData state) {
            super(ReportType.FORM_DISPLAY, state);
            this.formInfo = formInfo;
        }

        @NonNull
        public FormInfo getFormInfo() {
            return formInfo;
        }


        @Override
        public ReportingEvent overrideState(@NonNull FormInfo formInfo) {
            return new FormDisplay(getFormInfo(), copyState(formInfo));
        }

        @Override
        public ReportingEvent overrideState(@NonNull PagerData pagerData) {
            return new FormDisplay(getFormInfo(), copyState(pagerData));
        }

        @Override
        @NonNull
        public String toString() {
            return "ReportingEvent.FormDisplay{" +
                "formInfo='" + formInfo + '\'' +
                ", state=" + getState() +
                '}';
        }
    }

    private abstract static class DismissReportingEvent extends ReportingEvent {
        private final long displayTime;

        public DismissReportingEvent(@NonNull ReportType type, long displayTime, @Nullable LayoutData state) {
            super(type, state);
            this.displayTime = displayTime;
        }

        public long getDisplayTime() {
            return displayTime;
        }
    }

    private abstract static class PagerReportingEvent extends ReportingEvent {
        @NonNull
        private final PagerData pagerData;

        public PagerReportingEvent(@NonNull ReportType type, @NonNull PagerData pagerData, @Nullable LayoutData state) {
            super(type, state);
            this.pagerData = pagerData;
        }

        @NonNull
        public PagerData getPagerData() {
            return pagerData;
        }
    }
}
