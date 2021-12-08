package com.urbanairship.iam.layout;

import android.content.Context;

import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.Event;
import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.EventMatchers;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.core.util.ObjectsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AirshipLayoutDisplayAdapter} tests.
 */
@RunWith(AndroidJUnit4.class)
public class AirshipLayoutDisplayAdapterTest {

    private AirshipLayoutDisplayAdapter adapter;
    private AirshipLayoutDisplayContent displayContent;

    private AirshipLayoutDisplayAdapter.PrepareDisplayCallback mockCallback = Mockito.mock(AirshipLayoutDisplayAdapter.PrepareDisplayCallback.class);
    private Context context = mock(Context.class);
    private Assets assets = mock(Assets.class);
    private DisplayHandler displayHandler = mock(DisplayHandler.class);
    private String scheduleId = UUID.randomUUID().toString();
    private InAppMessage message;

    @Before
    public void setup() throws JsonException {
        String payloadString = "{\n" +
                "    \"layout\": {\n" +
                "        \"version\": 1,\n" +
                "        \"presentation\": {\n" +
                "          \"type\": \"modal\",\n" +
                "          \"default_placement\": {\n" +
                "            \"size\": {\n" +
                "              \"width\": \"100%\",\n" +
                "              \"height\": \"100%\"\n" +
                "            },\n" +
                "            \"position\": { \n" +
                "                \"horizontal\": \"center\",\n" +
                "                \"vertical\": \"center\" \n" +
                "            },\n" +
                "            \"shade_color\": {\n" +
                "              \"default\": { \n" +
                "                  \"type\": \"hex\", \n" +
                "                  \"hex\": \"#000000\", \n" +
                "                  \"alpha\": 0.2 }\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"view\": {\n" +
                "            \"type\": \"empty_view\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        JsonValue payload = JsonValue.parseString(payloadString);
        displayContent = AirshipLayoutDisplayContent.fromJson(payload);
        message = InAppMessage.newBuilder()
                              .setDisplayContent(displayContent)
                              .setName("layout name")
                              .build();

        adapter = new AirshipLayoutDisplayAdapter(message, displayContent, mockCallback);

        when(displayHandler.getScheduleId()).thenReturn(scheduleId);
    }

    @Test
    public void testPrepareDisplayException() throws JsonException, Thomas.DisplayException {
        Mockito.doThrow(new Thomas.DisplayException("nope")).when(mockCallback).prepareDisplay(Mockito.any());
        assertEquals(InAppMessageAdapter.CANCEL, adapter.onPrepare(context, assets));
    }

    @Test
    public void testDisplay() throws Thomas.DisplayException {
        Thomas.PendingDisplay pendingDisplay = mock(Thomas.PendingDisplay.class);
        when(pendingDisplay.setListener(any())).thenReturn(pendingDisplay);
        when(mockCallback.prepareDisplay(displayContent.getPayload())).thenReturn(pendingDisplay);

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        adapter.onDisplay(context, displayHandler);

        verify(pendingDisplay).setListener(any());
        verify(pendingDisplay).display(context);
    }

    @Test
    public void testButtonTap() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        listener.onButtonTap("button id", layoutData);

        InAppReportingEvent expected = InAppReportingEvent.buttonTap(scheduleId, message, "button id")
                .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPageView() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 1, 2, true);
        listener.onPageView(pagerData, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pageView(scheduleId, message, pagerData)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPageSwipe() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 10, 20, true);
        listener.onPageSwipe(pagerData, 10, 20, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pageSwipe(scheduleId, message, pagerData, 10, 20)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testFormDisplay() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        listener.onFormDisplay("form id", layoutData);

        InAppReportingEvent expected = InAppReportingEvent.formDisplay(scheduleId, message, "form id")
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testFormResult() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        Map<String, FormData<?>> children = new HashMap<>();
        children.put("score_id", new FormData.Score(1));
        FormData<?> formData = new FormData.Nps("form_id", "score_id", children);

        listener.onFormResult(formData, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.formResult(scheduleId, message, formData)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testDismissed() throws Thomas.DisplayException {
        ThomasListener listener = prepareListenerTest();
        listener.onDismiss(100);

        InAppReportingEvent expected = InAppReportingEvent.resolution(scheduleId, message,100, ResolutionInfo.dismissed());

        verify(displayHandler).addEvent(eq(expected));
        verify(displayHandler).notifyFinished(eq(ResolutionInfo.dismissed()));
    }

    @Test
    public void testButtonDismissed() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);

        ThomasListener listener = prepareListenerTest();
        listener.onDismiss("button id", "button description", false, 100, layoutData);

        ResolutionInfo resolutionInfo = ResolutionInfo.buttonPressed("button id", "button description", false);
        InAppReportingEvent expected = InAppReportingEvent.resolution(scheduleId, message,100, resolutionInfo)
                .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
        verify(displayHandler).notifyFinished(eq(resolutionInfo));
    }

    @Test
    public void testButtonCancel() throws Thomas.DisplayException {
        LayoutData layoutData = mock(LayoutData.class);

        ThomasListener listener = prepareListenerTest();
        listener.onDismiss("button id", "button description", true, 100, layoutData);

        ResolutionInfo resolutionInfo = ResolutionInfo.buttonPressed("button id", "button description", true);
        InAppReportingEvent expected = InAppReportingEvent.resolution(scheduleId, message,100, resolutionInfo)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
        verify(displayHandler).notifyFinished(eq(resolutionInfo));
        verify(displayHandler).cancelFutureDisplays();
    }

    private ThomasListener prepareListenerTest() throws Thomas.DisplayException {
        ArgumentCaptor<ThomasListener> argumentCaptor = ArgumentCaptor.forClass(ThomasListener.class);

        Thomas.PendingDisplay pendingDisplay = mock(Thomas.PendingDisplay.class);
        when(pendingDisplay.setListener(argumentCaptor.capture())).thenReturn(pendingDisplay);
        when(mockCallback.prepareDisplay(displayContent.getPayload())).thenReturn(pendingDisplay);
        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        adapter.onDisplay(context, displayHandler);

        return ObjectsCompat.requireNonNull(argumentCaptor.getValue());
    }
}
