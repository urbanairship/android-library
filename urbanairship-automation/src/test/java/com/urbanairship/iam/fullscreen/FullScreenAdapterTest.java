/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


/**
 * {@link FullScreenAdapter} tests.
 */
@Config(application = TestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FullScreenAdapterTest  {

    private FullScreenAdapter adapter;
    private InAppMessage message;
    private DisplayHandler displayHandler;

    @Before
    public void setup() {

        FullScreenDisplayContent displayContent = FullScreenDisplayContent.newBuilder()
                                                                          .setBody(TextInfo.newBuilder()
                                                                                           .setText("oh hi")
                                                                                           .build())
                                                                          .build();

        message = InAppMessage.newBuilder()
                              .setDisplayContent(displayContent)
                              .build();

        displayHandler = new DisplayHandler("schedule ID", true, JsonValue.NULL, JsonValue.NULL);

        adapter = FullScreenAdapter.newAdapter(message);
    }

    @Test
    public void testDisplay() {
        Context context = mock(Context.class);
        adapter.onDisplay(context, displayHandler);

        verify(context).startActivity(Mockito.argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent argument) {
                if (!displayHandler.equals(argument.getParcelableExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY))) {
                    return false;
                }

                if (!message.equals(argument.getParcelableExtra(FullScreenActivity.IN_APP_MESSAGE_KEY))) {
                    return false;
                }

                if (!argument.getComponent().getClassName().equals(FullScreenActivity.class.getName())) {
                    return false;
                }

                return true;
            }
        }));
    }

    @Test
    public void testOkPrepare() {
        UAirship.shared().getUrlAllowList().addEntry("*");

        int youTubeOnPrepare = testPrepare(MediaInfo.TYPE_YOUTUBE, "https://www.youtube.com", "Youtube");
        assertEquals(InAppMessageAdapter.OK, youTubeOnPrepare);

        int imageOnPrepare = testPrepare(MediaInfo.TYPE_IMAGE, "cool://story", "Its cool.");
        assertEquals(InAppMessageAdapter.OK, imageOnPrepare);

        int videoType = testPrepare(MediaInfo.TYPE_VIDEO, "cool://story", "Its cool.");
        assertEquals(InAppMessageAdapter.OK, videoType);
    }

    @Test
    public void testUrlAllowListEnabled() {
        int youTubeOnPrepare = testPrepare(MediaInfo.TYPE_YOUTUBE, "https://www.youtube.com", "Youtube");
        assertEquals(InAppMessageAdapter.OK, youTubeOnPrepare);

        int imageOnPrepare = testPrepare(MediaInfo.TYPE_IMAGE, "cool://story", "Its cool.");
        assertEquals(InAppMessageAdapter.OK, imageOnPrepare);

        int videoType = testPrepare(MediaInfo.TYPE_VIDEO, "cool://story", "Its cool.");
        assertEquals(InAppMessageAdapter.CANCEL, videoType);
    }

    @Test
    public void testUrlAllowListEnabledWithEntry() {
        UAirship.shared().getUrlAllowList().addEntry("*://story");

        int youTubeOnPrepare = testPrepare(MediaInfo.TYPE_YOUTUBE, "https://www.youtube.com", "Youtube");
        assertEquals(InAppMessageAdapter.OK, youTubeOnPrepare);

        int imageOnPrepare = testPrepare(MediaInfo.TYPE_IMAGE, "cool://story", "Its cool.");
        assertEquals(InAppMessageAdapter.OK, imageOnPrepare);

        int videoType = testPrepare(MediaInfo.TYPE_VIDEO, "cool://story", "Its cool.");
        assertEquals(InAppMessageAdapter.OK, videoType);

    }

    @Test
    public void testCancelPrepare() {
        int youtubeResult = testPrepare(MediaInfo.TYPE_VIDEO, "badurl", "Youtube");
        assertEquals(InAppMessageAdapter.CANCEL, youtubeResult);

        int imageResult = testPrepare(MediaInfo.TYPE_IMAGE, "badurl", "description");
        assertEquals(InAppMessageAdapter.OK, imageResult);

        int videoResult = testPrepare(MediaInfo.TYPE_VIDEO, "badurl", "description");
        assertEquals(InAppMessageAdapter.CANCEL, videoResult);
    }

    public int testPrepare(String type, String url, String description) {
        Context context = Mockito.mock(Context.class);
        Assets assets = Mockito.mock(Assets.class);

        MediaInfo mediaInfo = MediaInfo.newBuilder()
                                              .setType(type)
                                              .setUrl(url)
                                              .setDescription(description)
                                              .build();

        FullScreenDisplayContent displayContent = FullScreenDisplayContent.newBuilder()
                                                                                 .setBody(TextInfo.newBuilder()
                                                                                                  .setText("oh hi")
                                                                                                  .build())
                                                                                 .setMedia(mediaInfo)
                                                                                 .build();

        InAppMessage message = InAppMessage.newBuilder()
                              .setDisplayContent(displayContent)
                              .build();

        FullScreenAdapter adapter = FullScreenAdapter.newAdapter(message);

        return adapter.onPrepare(context, assets);
    }

}
