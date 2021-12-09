package com.urbanairship.iam.layout;

import android.content.Context;

import com.urbanairship.android.layout.BasePayload;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayArgs;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageWebViewClient;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.webkit.AirshipWebViewClient;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;
import androidx.core.util.Supplier;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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

    private Context context = mock(Context.class);
    private Assets assets = mock(Assets.class);
    private UrlAllowList allowList = mock(UrlAllowList.class);
    private DisplayHandler displayHandler = mock(DisplayHandler.class);
    private String scheduleId = UUID.randomUUID().toString();
    private TestCallback testCallback = new TestCallback();
    private boolean isConnected = false;

    private InAppMessage message;

    @Before
    public void setup() throws JsonException {
        String payloadString = "{\n" +
                "   \"layout\":{\n" +
                "      \"version\":1,\n" +
                "      \"presentation\":{\n" +
                "         \"type\":\"modal\",\n" +
                "         \"default_placement\":{\n" +
                "            \"size\":{\n" +
                "               \"width\":\"100%\",\n" +
                "               \"height\":\"100%\"\n" +
                "            },\n" +
                "            \"position\":{\n" +
                "               \"horizontal\":\"center\",\n" +
                "               \"vertical\":\"center\"\n" +
                "            },\n" +
                "            \"shade_color\":{\n" +
                "               \"default\":{\n" +
                "                  \"type\":\"hex\",\n" +
                "                  \"hex\":\"#000000\",\n" +
                "                  \"alpha\":0.2\n" +
                "               }\n" +
                "            }\n" +
                "         }\n" +
                "      },\n" +
                "      \"view\":{\n" +
                "         \"type\":\"linear_layout\",\n" +
                "         \"direction\":\"vertical\",\n" +
                "         \"items\":[\n" +
                "            {\n" +
                "               \"size\":{\n" +
                "                  \"width\":\"100%\",\n" +
                "                  \"height\":\"100%\"\n" +
                "               },\n" +
                "               \"view\":{\n" +
                "                  \"type\":\"media\",\n" +
                "                  \"media_fit\":\"center\",\n" +
                "                  \"url\":\"https://some-image-url\",\n" +
                "                  \"media_type\":\"image\"\n" +
                "               }\n" +
                "            },\n" +
                "            {\n" +
                "               \"size\":{\n" +
                "                  \"width\":\"100%\",\n" +
                "                  \"height\":\"100%\"\n" +
                "               },\n" +
                "               \"view\":{\n" +
                "                  \"type\":\"media\",\n" +
                "                  \"media_fit\":\"center\",\n" +
                "                  \"url\":\"https://some-youtube-url\",\n" +
                "                  \"media_type\":\"youtube\"\n" +
                "               }\n" +
                "            },\n" +
                "            {\n" +
                "               \"size\":{\n" +
                "                  \"width\":\"100%\",\n" +
                "                  \"height\":\"100%\"\n" +
                "               },\n" +
                "               \"view\":{\n" +
                "                  \"type\":\"media\",\n" +
                "                  \"media_fit\":\"center\",\n" +
                "                  \"url\":\"https://some-video-url\",\n" +
                "                  \"media_type\":\"video\"\n" +
                "               }\n" +
                "            },\n" +
                "            {\n" +
                "               \"size\":{\n" +
                "                  \"width\":\"100%\",\n" +
                "                  \"height\":\"100%\"\n" +
                "               },\n" +
                "               \"view\":{\n" +
                "                  \"type\":\"image_button\",\n" +
                "                  \"image\":{\n" +
                "                     \"type\":\"url\",\n" +
                "                     \"url\":\"https://some-image-button-url\"\n" +
                "                  },\n" +
                "                  \"identifier\":\"dismiss_button\",\n" +
                "                  \"button_click\":[\n" +
                "                     \"dismiss\"\n" +
                "                  ]\n" +
                "               }\n" +
                "            }\n" +
                "         ]\n" +
                "      }\n" +
                "   }\n" +
                "}";

        JsonValue payload = JsonValue.parseString(payloadString);
        displayContent = AirshipLayoutDisplayContent.fromJson(payload);
        message = InAppMessage.newBuilder()
                              .setDisplayContent(displayContent)
                              .setName("layout name")
                              .build();

        Supplier<Boolean> isConnectedSupplier = () -> isConnected;
        adapter = new AirshipLayoutDisplayAdapter(message, displayContent, testCallback, allowList, isConnectedSupplier);

        when(displayHandler.getScheduleId()).thenReturn(scheduleId);
        when(assets.file(anyString())).thenReturn(new File(UUID.randomUUID().toString()));
    }

    @Test
    public void testPrepareDisplayException() {
        isConnected = true;
        testCallback.exception = new DisplayException("nope");
        assertEquals(InAppMessageAdapter.CANCEL, adapter.onPrepare(context, assets));
    }

    @Test
    public void testPrepareRejectUrls() {
        isConnected = true;
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(false);
        assertEquals(InAppMessageAdapter.CANCEL, adapter.onPrepare(context, assets));
    }

    @Test
    public void testPrepareNotConnectedOrCached() {
        isConnected = false;
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
        assertEquals(InAppMessageAdapter.RETRY, adapter.onPrepare(context, assets));
    }

    @Test
    public void testPrepareConnectedNotCached() {
        isConnected = true;
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
    }

    @Test
    public void testImageCache() {
        isConnected = true;

        File mockImageButtonFile = mock(File.class);
        when(mockImageButtonFile.exists()).thenReturn(true);
        when(mockImageButtonFile.getAbsolutePath()).thenReturn("button-image");
        when(assets.file("https://some-image-button-url")).thenReturn(mockImageButtonFile);

        File mockMediaImage = mock(File.class);
        when(mockMediaImage.exists()).thenReturn(false);
        when(mockMediaImage.getAbsolutePath()).thenReturn("media-image");
        when(assets.file("https://some-image-url")).thenReturn(mockMediaImage);

        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));

        adapter.onDisplay(context, displayHandler);

        ImageCache cache = testCallback.displayArgs.getImageCache();
        assertEquals(cache.get("https://some-image-button-url"), "file://button-image");
        assertNull(cache.get("https://some-image-url"));
    }

    @Test
    public void testExtendedWebViewClient() {
        isConnected = true;
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));

        adapter.onDisplay(context, displayHandler);

        AirshipWebViewClient webViewClient = testCallback.displayArgs.getWebViewClientFactory().create();
        assertTrue(webViewClient instanceof InAppMessageWebViewClient);
    }

    @Test
    public void testDisplay() {
        isConnected = true;
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        adapter.onDisplay(context, displayHandler);

        Assert.assertNotNull(testCallback.displayArgs);
        Assert.assertNotNull(testCallback.displayArgs.getImageCache());
        Assert.assertNotNull(testCallback.displayArgs.getListener());
        Assert.assertNotNull(testCallback.displayArgs.getWebViewClientFactory());
    }

    @Test
    public void testButtonTap() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        listener.onButtonTap("button id", layoutData);

        InAppReportingEvent expected = InAppReportingEvent.buttonTap(scheduleId, message, "button id")
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPageView() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 1, 2, true);
        listener.onPageView(pagerData, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pageView(scheduleId, message, pagerData)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPageSwipe() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 10, 20, true);
        listener.onPageSwipe(pagerData, 10, 20, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pageSwipe(scheduleId, message, pagerData, 10, 20)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testFormDisplay() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        listener.onFormDisplay("form id", layoutData);

        InAppReportingEvent expected = InAppReportingEvent.formDisplay(scheduleId, message, "form id")
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testFormResult() {
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
    public void testDismissed() {
        ThomasListener listener = prepareListenerTest();
        listener.onDismiss(100);

        InAppReportingEvent expected = InAppReportingEvent.resolution(scheduleId, message, 100, ResolutionInfo.dismissed());

        verify(displayHandler).addEvent(eq(expected));
        verify(displayHandler).notifyFinished(eq(ResolutionInfo.dismissed()));
    }

    @Test
    public void testButtonDismissed() {
        LayoutData layoutData = mock(LayoutData.class);

        ThomasListener listener = prepareListenerTest();
        listener.onDismiss("button id", "button description", false, 100, layoutData);

        ResolutionInfo resolutionInfo = ResolutionInfo.buttonPressed("button id", "button description", false);
        InAppReportingEvent expected = InAppReportingEvent.resolution(scheduleId, message, 100, resolutionInfo)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
        verify(displayHandler).notifyFinished(eq(resolutionInfo));
    }

    @Test
    public void testButtonCancel() {
        LayoutData layoutData = mock(LayoutData.class);

        ThomasListener listener = prepareListenerTest();
        listener.onDismiss("button id", "button description", true, 100, layoutData);

        ResolutionInfo resolutionInfo = ResolutionInfo.buttonPressed("button id", "button description", true);
        InAppReportingEvent expected = InAppReportingEvent.resolution(scheduleId, message, 100, resolutionInfo)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
        verify(displayHandler).notifyFinished(eq(resolutionInfo));
        verify(displayHandler).cancelFutureDisplays();
    }

    private ThomasListener prepareListenerTest() {
        isConnected = true;
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        adapter.onDisplay(context, displayHandler);

        return ObjectsCompat.requireNonNull(testCallback.displayArgs.getListener());
    }

    private static class TestCallback implements AirshipLayoutDisplayAdapter.DisplayRequestCallback {

        DisplayArgs displayArgs;
        DisplayException exception;

        @Override
        public DisplayRequest prepareDisplay(@NonNull BasePayload basePayload) throws DisplayException {
            if (exception != null) {
                throw exception;
            }

            return new DisplayRequest(basePayload, (context, args) -> {
                this.displayArgs = args;
            });
        }

    }

}
