package com.urbanairship.iam.layout;

import android.content.Context;

import com.urbanairship.BaseTestCase;
import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestActivity;
import com.urbanairship.TestApplication;
import com.urbanairship.android.layout.AirshipEmbeddedViewManager;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayArgs;
import com.urbanairship.android.layout.display.DisplayException;
import com.urbanairship.android.layout.display.DisplayRequest;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.FormInfo;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.android.layout.reporting.PagerData;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.automation.test.ShadowInAppActivityMonitor;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageWebViewClient;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.iam.events.InAppReportingEvent;
import com.urbanairship.UrlAllowList;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Network;
import com.urbanairship.webkit.AirshipWebViewClient;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.core.util.ObjectsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AirshipLayoutDisplayAdapter} tests.
 */
@Config(
    sdk = 28,
    shadows = { ShadowAirshipExecutorsLegacy.class, ShadowInAppActivityMonitor.class },
    application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class AirshipLayoutDisplayAdapterTest extends BaseTestCase {

    private AirshipLayoutDisplayAdapter adapter;
    private AirshipLayoutDisplayContent displayContent;

    private Context context = ApplicationProvider.getApplicationContext();
    private Assets assets = mock(Assets.class);
    private UrlAllowList allowList = mock(UrlAllowList.class);
    private DisplayHandler displayHandler = mock(DisplayHandler.class);
    private String scheduleId = UUID.randomUUID().toString();
    private TestCallback testCallback = new TestCallback();
    private Network network = mock(Network.class);

    private ShadowInAppActivityMonitor inAppActivityMonitor = Shadow.extract(InAppActivityMonitor.shared(context));

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
                "                  \"type\":\"web_view\",\n" +
                "                  \"url\":\"https://some-youtube-url\"\n" +
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

        adapter = new AirshipLayoutDisplayAdapter(message, displayContent, testCallback, allowList, network);

        when(displayHandler.getScheduleId()).thenReturn(scheduleId);
        when(assets.file(anyString())).thenReturn(new File(UUID.randomUUID().toString()));
    }

    @Test
    public void testPrepareDisplayException() {
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);

        testCallback.exception = new DisplayException("nope");
        assertEquals(InAppMessageAdapter.CANCEL, adapter.onPrepare(context, assets));
    }

    @Test
    public void testPrepareRejectUrls() {
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(false);
        assertEquals(InAppMessageAdapter.CANCEL, adapter.onPrepare(context, assets));
    }

    @Test
    public void testIsReadyNotConnectedOrCached() {
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        assertFalse(adapter.isReady(context));
    }

    @Test
    public void testIsReadyConnectedNotCachedActivityStarted() {
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        inAppActivityMonitor.hasResumedActivities = true;

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        assertTrue(adapter.isReady(context));
    }

    @Test
    public void testIsReadyConnectedNotCachedActivityStopped() {
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        inAppActivityMonitor.hasResumedActivities = false;

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        assertFalse(adapter.isReady(context));
    }

    @Test
    public void testImageCache() {
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
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
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));

        adapter.onDisplay(context, displayHandler);

        AirshipWebViewClient webViewClient = testCallback.displayArgs.getWebViewClientFactory().create();
        assertTrue(webViewClient instanceof InAppMessageWebViewClient);
    }

    @Test
    public void testDisplay() {
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
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

        listener.onButtonTap("button id", null, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.buttonTap(scheduleId, message, "button id", null)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testButtonTapWithMetadata() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        JsonValue metadata = JsonMap.newBuilder()
                                    .put("foo", "bar")
                                    .build()
                                    .toJsonValue();

        listener.onButtonTap("button id", metadata, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.buttonTap(scheduleId, message, "button id", metadata)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPageView() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 1, "page1", 2, true);
        listener.onPageView(pagerData, layoutData, 0);

        InAppReportingEvent expected = InAppReportingEvent.pageView(scheduleId, message, pagerData, 1)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testDedupePagerCompleted() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        listener.onPageView(new PagerData("some id", 0, "page0",2, false), layoutData, 0);
        listener.onPageView(new PagerData("some id", 1, "page1",2, true), layoutData, 1);
        listener.onPageView(new PagerData("some id", 0, "page0",2, true), layoutData, 2);
        listener.onPageView(new PagerData("some id", 1, "page1",2, true), layoutData, 3);

        InAppReportingEvent pagerCompleted = InAppReportingEvent.pagerCompleted(scheduleId, message, new PagerData("some id", 1, "page1",2, true))
                                                                .setLayoutData(layoutData);

        verify(displayHandler, times(1)).addEvent(eq(pagerCompleted));
    }

    @Test
    public void testPagerComplete() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 10, "page10", 20, true);
        listener.onPageView(pagerData, layoutData, 2);

        InAppReportingEvent expected = InAppReportingEvent.pagerCompleted(scheduleId, message, pagerData)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPagerSummary() {
       // TODO
    }

    @Test
    public void testPageSwipe() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        PagerData pagerData = new PagerData("some id", 10, "page10", 20, true);
        listener.onPageSwipe(pagerData, 10, "page10", 20, "page20", layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pageSwipe(scheduleId, message, pagerData, 10, "page10", 20, "page20")
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPagerGesture() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();
        JsonValue metadata = JsonValue.wrap("foo");

        listener.onPagerGesture("gesture ID", metadata, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pagerGesture(scheduleId, message, "gesture ID", metadata)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testPagerAutomatedAction() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();
        JsonValue metadata = JsonValue.wrap("foo");

        listener.onPagerAutomatedAction("action ID", metadata, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.pagerAction(scheduleId, message, "action ID", metadata)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testFormDisplay() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        FormInfo formInfo = new FormInfo("form id", "form type", "response type", false);
        listener.onFormDisplay(formInfo, layoutData);

        InAppReportingEvent expected = InAppReportingEvent.formDisplay(scheduleId, message, formInfo)
                                                          .setLayoutData(layoutData);

        verify(displayHandler).addEvent(eq(expected));
    }

    @Test
    public void testFormResult() {
        LayoutData layoutData = mock(LayoutData.class);
        ThomasListener listener = prepareListenerTest();

        Set<FormData<?>> children = Collections.singleton(new FormData.Score("score_id", 1, true, null, null));
        FormData.BaseForm formData = new FormData.Nps("form_id", "response type", "score_id", children);

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
        when(network.isConnected(any(Context.class))).then((Answer<Boolean>) invocation -> true);
        when(allowList.isAllowed(anyString(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        adapter.onDisplay(context, displayHandler);

        return ObjectsCompat.requireNonNull(testCallback.displayArgs.getListener());
    }

    private static class TestCallback implements AirshipLayoutDisplayAdapter.DisplayRequestCallback {

        DisplayArgs displayArgs;
        DisplayException exception;

        @Override
        public DisplayRequest prepareDisplay(
                @NonNull LayoutInfo layoutInfo,
                @NonNull AirshipEmbeddedViewManager embeddedViewManager
        ) throws DisplayException {
            if (exception != null) {
                throw exception;
            }

            return new DisplayRequest(layoutInfo, (context, args) -> {
                this.displayArgs = args;
            });
        }
    }
}
