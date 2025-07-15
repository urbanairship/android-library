package com.urbanairship.android.layout.event

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Locale
import kotlin.time.Duration

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class ReportingEvent {

    public data class FormDisplay(val data: FormDisplayData, val context: LayoutData): ReportingEvent()
    public data class FormResult(
        val data: FormResultData,
        val context: LayoutData,
        val attributes: Map<AttributeName, AttributeValue>,
        val channels: List<ThomasChannelRegistration>
    ): ReportingEvent()

    public data class Gesture(val data: GestureData, val context: LayoutData): ReportingEvent()
    public data class ButtonTap(val data: ButtonTapData, val context: LayoutData): ReportingEvent()

    public data class PageView(val data: PageViewData, val context: LayoutData): ReportingEvent()
    public data class PageSwipe(val data: PageSwipeData, val context: LayoutData): ReportingEvent()
    public data class PageAction(val data: PageActionData, val context: LayoutData): ReportingEvent()

    public data class PagerComplete(val data: PagerCompleteData, val context: LayoutData): ReportingEvent()
    public data class PagerSummary(val data: PageSummaryData, val context: LayoutData): ReportingEvent()

    public data class Dismiss(val data: DismissData, val displayTime: Duration, val context: LayoutData): ReportingEvent()

    public sealed class DismissData {
        public data class ButtonTapped(
            val identifier: String,
            val description: String,
            val cancel: Boolean
        ): DismissData()

        public data object TimedOut: DismissData()
        public data object UserDismissed: DismissData()
    }

    public data class PageViewData(
        val identifier: String,
        val pageIdentifier: String,
        val pageIndex: Int,
        val pageViewCount: Int,
        val pageCount: Int,
        val completed: Boolean
    ): JsonSerializable {
        internal companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val PAGE_INDEX = "page_index"
            private const val PAGE_COUNT = "page_count"
            private const val PAGE_VIEW_COUNT = "viewed_count"
            private const val PAGE_IDENTIFIER = "page_identifier"
            private const val COMPLETED = "completed"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            PAGE_INDEX to pageIndex,
            PAGE_COUNT to pageCount,
            PAGE_VIEW_COUNT to pageViewCount,
            PAGE_IDENTIFIER to pageIdentifier,
            COMPLETED to completed
        ).toJsonValue()
    }

    public data class PagerCompleteData(
        val identifier: String,
        val pageIndex: Int,
        val pageCount: Int,
        val pageIdentifier: String
    ): JsonSerializable {

        internal companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val PAGE_INDEX = "page_index"
            private const val PAGE_COUNT = "page_count"
            private const val PAGE_IDENTIFIER = "page_identifier"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            PAGE_INDEX to pageIndex,
            PAGE_COUNT to pageCount,
            PAGE_IDENTIFIER to pageIdentifier
        ).toJsonValue()
    }

    public data class PageSwipeData(
        val identifier: String,
        val toPageIndex: Int,
        val toPageIdentifier: String,
        val fromPageIndex: Int,
        val fromPageIdentifier: String
    ): JsonSerializable {

        internal companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val TO_PAGE_INDEX = "to_page_index"
            private const val TO_PAGE_IDENTIFIER = "to_page_identifier"
            private const val FROM_PAGE_INDEX = "from_page_index"
            private const val FROM_PAGE_IDENTIFIER = "from_page_identifier"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            TO_PAGE_INDEX to toPageIndex,
            TO_PAGE_IDENTIFIER to toPageIdentifier,
            FROM_PAGE_INDEX to fromPageIndex,
            FROM_PAGE_IDENTIFIER to fromPageIdentifier
        ).toJsonValue()
    }

    public data class PageActionData(
        val identifier: String,
        val metadata: JsonSerializable?
    ): JsonSerializable {

        internal companion object {
            private const val IDENTIFIER = "action_identifier"
            private const val REPORTING_METADATA = "reporting_metadata"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            REPORTING_METADATA to metadata
        ).toJsonValue()
    }

    public data class PageSummaryData(
        val identifier: String,
        val viewedPages: List<PageView>,
        val pageCount: Int,
        val completed: Boolean
    ): JsonSerializable {

        public data class PageView(
            val identifier: String,
            val index: Int,
            val displayTime: Duration
        ): JsonSerializable {

            internal companion object {
                private const val IDENTIFIER = "page_identifier"
                private const val INDEX = "page_index"
                private const val DISPLAY_TIME = "display_time"
            }

            override fun toJsonValue(): JsonValue {
                val displayMilliseconds = displayTime.inWholeMilliseconds / 1000.0

                return jsonMapOf(
                    IDENTIFIER to identifier,
                    INDEX to index,
                    DISPLAY_TIME to String.format(Locale.US, "%.2f", displayMilliseconds)
                ).toJsonValue()
            }
        }

        internal companion object {
            private const val IDENTIFIER = "pager_identifier"
            private const val VIEWED_PAGES = "viewed_pages"
            private const val PAGE_COUNT = "page_count"
            private const val COMPLETED = "completed"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            VIEWED_PAGES to viewedPages,
            PAGE_COUNT to pageCount,
            COMPLETED to completed
        ).toJsonValue()
    }

    public data class GestureData(
        val identifier: String,
        val reportingMetadata: JsonSerializable?
    ): JsonSerializable {

        internal companion object {
            private const val IDENTIFIER = "gesture_identifier"
            private const val REPORTING_METADATA = "reporting_metadata"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            REPORTING_METADATA to reportingMetadata
        ).toJsonValue()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class ButtonTapData(
        val identifier: String,
        val reportingMetadata: JsonSerializable?
    ): JsonSerializable {

        internal companion object {
            private const val IDENTIFIER = "button_identifier"
            private const val REPORTING_METADATA = "reporting_metadata"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            REPORTING_METADATA to reportingMetadata
        ).toJsonValue()
    }

    public data class FormDisplayData(
        val identifier: String,
        val formType: String,
        val responseType: String?
    ): JsonSerializable {

        internal companion object {
            private const val IDENTIFIER = "form_identifier"
            private const val FORM_TYPE = "form_type"
            private const val RESPONSE_TYPE = "form_response_type"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            FORM_TYPE to formType,
            RESPONSE_TYPE to responseType
        ).toJsonValue()
    }

    public data class FormResultData(
        private val forms: ThomasFormField.BaseForm?
    ): JsonSerializable {

        internal companion object {
            private const val FORMS = "forms"
        }
        override fun toJsonValue(): JsonValue = jsonMapOf(
            FORMS  to forms?.toJsonValue(withState = false)
        ).toJsonValue()
    }
}
