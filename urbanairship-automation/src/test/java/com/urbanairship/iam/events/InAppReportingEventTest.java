/* Copyright Airship and Contributors */

package com.urbanairship.iam.events;

import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.EventMatchers;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.iam.custom.CustomDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class InAppReportingEventTest {

    private Analytics mockAnalytics = Mockito.mock(Analytics.class);
    private InAppMessage message;

    @Before
    public void setup() throws JsonException {
        this.message = InAppMessage.newBuilder()
                                   .setName("appDefinedMessage")
                                   .setSource(InAppMessage.SOURCE_REMOTE_DATA)
                                   .setDisplayContent(CustomDisplayContent.fromJson(JsonMap.EMPTY_MAP.toJsonValue()))
                                   .build();
    }

    /**
     * Test button click resolution event.
     */
    @Test
    public void testButtonClickResolutionEvent() {
        ButtonInfo buttonInfo = ButtonInfo.newBuilder()
                                          .setId("button id")
                                          .setLabel(TextInfo.newBuilder()
                                                            .setText("hi")
                                                            .build())
                                          .build();

        InAppReportingEvent.resolution("schedule ID", message, 3500, ResolutionInfo.buttonPressed(buttonInfo))
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("resolution", JsonMap.newBuilder()
                                                                .put("type", "button_click")
                                                                .put("button_id", "button id")
                                                                .put("button_description", "hi")
                                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                                .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_RESOLUTION, expectedData)));
    }

    /**
     * Test on click resolution event.
     */
    @Test
    public void testClickedResolutionEvent() {
        ResolutionInfo resolutionInfo = ResolutionInfo.messageClicked();

        InAppReportingEvent.resolution("schedule ID", message, 3500, resolutionInfo)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("resolution", JsonMap.newBuilder()
                                                                .put("type", "message_click")
                                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                                .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_RESOLUTION, expectedData)));
    }

    /**
     * Test user dismissed resolution event.
     */
    @Test
    public void testUserDismissedResolutionEvent() {
        ResolutionInfo resolutionInfo = ResolutionInfo.dismissed();

        InAppReportingEvent.resolution("schedule ID", message, 3500, resolutionInfo)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("resolution", JsonMap.newBuilder()
                                                                .put("type", "user_dismissed")
                                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                                .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_RESOLUTION, expectedData)));
    }

    /**
     * Test timed out resolution event.
     */
    @Test
    public void testTimedOutResolutionEvent() {
        ResolutionInfo resolutionInfo = ResolutionInfo.timedOut();

        InAppReportingEvent.resolution("schedule ID", message, 3500, resolutionInfo)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("resolution", JsonMap.newBuilder()
                                                                .put("type", "timed_out")
                                                                .put("display_time", Event.millisecondsToSecondsString(3500))
                                                                .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_RESOLUTION, expectedData)));
    }

    /**
     * Test replaced resolution event.
     */
    @Test
    public void testReplacedResolutionEvent() {
        InAppReportingEvent.legacyReplaced("iaa ID", "replacement id")
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", "iaa ID")
                                      .put("resolution", JsonMap.newBuilder()
                                                                .put("type", "replaced")
                                                                .put("replacement_id", "replacement id")
                                                                .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_RESOLUTION, expectedData)));
    }

    /**
     * Test direct open resolution event.
     */
    @Test
    public void testDirectOpenResolutionEvent() {
        InAppReportingEvent.legacyPushOpened("iaa ID")
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", "iaa ID")
                                      .put("resolution", JsonMap.newBuilder()
                                                                .put("type", "direct_open")
                                                                .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_RESOLUTION, expectedData)));
    }

    @Test
    public void testDisplay() {
        InAppReportingEvent.display("schedule ID", message)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_DISPLAY, expectedData)));
    }

    @Test
    public void testButtonTap() {
        InAppReportingEvent.buttonTap("schedule ID", message, "button id")
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("button_identifier", "button id")
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_BUTTON_TAP, expectedData)));
    }

    @Test
    public void testPageView() {
        PagerData pagerData = new PagerData("pager id", 1, "page1", 2, false);
        InAppReportingEvent.pageView("schedule ID", message, pagerData, 1)
                           .record(mockAnalytics);
        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("pager_identifier", "pager id")
                                      .put("page_index", 1)
                                      .put("page_identifier", "page1")
                                      .put("page_count", 2)
                                      .put("viewed_count", 1)
                                      .put("completed", false)
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_PAGE_VIEW, expectedData)));
    }

    @Test
    public void testPagerViewSwipe() {
        PagerData pagerData = new PagerData("pager id", 1, "page1", 2, false);

        InAppReportingEvent.pageSwipe("schedule ID", message, pagerData, 1, "page1", 0, "page0")
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("pager_identifier", "pager id")
                                      .put("to_page_index", 1)
                                      .put("from_page_index", 0)
                                      .put("to_page_identifier", "page1")
                                      .put("from_page_identifier", "page0")
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_PAGE_SWIPE, expectedData)));
    }

    @Test
    public void testPageCompleted() {
        PagerData pagerData = new PagerData("pager id", 1, "page1", 2, true);

        InAppReportingEvent.pagerCompleted("schedule ID", message, pagerData)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("pager_identifier", "pager id")
                                      .put("page_count", 2)
                                      .put("page_index", 1)
                                      .put("page_identifier", "page1")
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPES_PAGER_COMPLETED, expectedData)));
    }

    @Test
    public void testPageViewSummary() {
        InAppReportingEvent.PageViewSummary pageView = new InAppReportingEvent.PageViewSummary(0, "page0", 100000);

        JsonMap expected = JsonMap.newBuilder()
                                  .put("page_index", 0)
                                  .put("page_identifier", "page0")
                                  .put("display_time", "100.000")
                                  .build();

        assertEquals(expected, pageView.toJsonValue().getMap());
    }

    @Test
    public void testPageSummary() {
        PagerData pagerData = new PagerData("pager id", 1, "page1", 2, true);

        List<InAppReportingEvent.PageViewSummary> views = new ArrayList<>();
        views.add(new InAppReportingEvent.PageViewSummary(0, "page0", 100));
        views.add(new InAppReportingEvent.PageViewSummary(1, "page1", 200));
        views.add(new InAppReportingEvent.PageViewSummary(0, "page0", 300));
        views.add(new InAppReportingEvent.PageViewSummary(1, "page1", 100));

        InAppReportingEvent.pagerSummary("schedule ID", message, pagerData, views)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("pager_identifier", "pager id")
                                      .put("page_count", 2)
                                      .put("completed", true)
                                      .putOpt("viewed_pages", views)
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPES_PAGER_SUMMARY, expectedData)));
    }

    @Test
    public void testFormDisplay() {
        FormInfo formInfo = new FormInfo("form id", "form type", "response type", false);

        InAppReportingEvent.formDisplay("schedule ID", message, formInfo)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("form_identifier", "form id")
                                      .put("form_response_type", "response type")
                                      .put("form_type", "form type")
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_FORM_DISPLAY, expectedData)));
    }

    @Test
    public void testFormResult() {
        FormData.BaseForm formData = new FormData.Nps("form_id", "response type", "score_id", Collections.singleton(new FormData.Score("score_id", 1)));

        InAppReportingEvent.formResult("schedule ID", message, formData)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("forms", formData)
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_FORM_RESULT, expectedData)));
    }

    @Test
    public void testCampaigns() {
        InAppReportingEvent.display("schedule ID", message)
                           .setCampaigns(JsonValue.wrap("campaigns!"))
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .put("campaigns", "campaigns!")
                                                        .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_DISPLAY, expectedData)));
    }

    @Test
    public void testRenderedLocale() {
        message = InAppMessage.newBuilder(message)
                              .setRenderedLocale(JsonMap.newBuilder()
                                                        .put("en", "neat")
                                                        .build()
                                                        .getMap())
                              .build();

        InAppReportingEvent.display("schedule ID", message)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("locale", JsonMap.newBuilder()
                                                            .put("en", "neat")
                                                            .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_DISPLAY, expectedData)));
    }

    @Test
    public void testContext() {
        FormInfo formInfo = new FormInfo("form id", "form type", "response type", true);
        PagerData pagerData = new PagerData("pager id", 1, "page1", 2, true);
        LayoutData layoutData = new LayoutData(formInfo, pagerData, "button ID!");
        InAppReportingEvent.display("schedule ID", message)
                           .setLayoutData(layoutData)
                           .setReportingContext(JsonValue.wrap("reporting bits!"))
                           .record(mockAnalytics);

        JsonMap contextData = JsonMap.newBuilder()
                                     .put("reporting_context", "reporting bits!")
                                     .put("pager", JsonMap.newBuilder()
                                                          .put("identifier", "pager id")
                                                          .put("count", 2)
                                                          .put("page_index", 1)
                                                          .put("page_identifier", "page1")
                                                          .put("completed", true)
                                                          .build())
                                     .put("form", JsonMap.newBuilder()
                                                         .put("identifier", "form id")
                                                         .put("submitted", true)
                                                         .put("type", "form type")
                                                         .put("response_type", "response type")
                                                         .build())
                                     .put("button", JsonMap.newBuilder()
                                                           .put("identifier", "button ID!")
                                                           .build())
                                     .build();

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("context", contextData)
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_DISPLAY, expectedData)));
    }

    @Test
    public void testEmptyContextData() {
        InAppReportingEvent.display("schedule ID", message)
                           .setLayoutData(new LayoutData(null, null, null))
                           .setReportingContext(JsonValue.NULL)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_DISPLAY, expectedData)));
    }

    @Test
    public void testConversionIds() {
        when(mockAnalytics.getConversionMetadata()).thenReturn("conversion metadata!");
        when(mockAnalytics.getConversionSendId()).thenReturn("conversion send id!");

        InAppReportingEvent.display("schedule ID", message)
                           .setLayoutData(new LayoutData(null, null, null))
                           .setReportingContext(JsonValue.NULL)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("conversion_send_id", "conversion send id!")
                                      .put("conversion_metadata", "conversion metadata!")
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_DISPLAY, expectedData)));
    }

    @Test
    public void testPermissionResult() {
        InAppReportingEvent.permissionResultEvent("schedule ID", message, Permission.DISPLAY_NOTIFICATIONS, PermissionStatus.DENIED, PermissionStatus.GRANTED)
                           .record(mockAnalytics);

        JsonMap expectedData = JsonMap.newBuilder()
                                      .put("source", "urban-airship")
                                      .put("id", JsonMap.newBuilder()
                                                        .put("message_id", "schedule ID")
                                                        .build())
                                      .put("permission", Permission.DISPLAY_NOTIFICATIONS)
                                      .put("starting_permission_status", PermissionStatus.DENIED)
                                      .put("ending_permission_status", PermissionStatus.GRANTED)
                                      .build();

        verify(mockAnalytics).addEvent(argThat(EventMatchers.event(InAppReportingEvent.TYPE_PERMISSION_RESULT_EVENT, expectedData)));
    }

}
