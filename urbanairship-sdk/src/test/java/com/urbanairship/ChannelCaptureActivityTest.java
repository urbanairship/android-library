package com.urbanairship;


import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;

import com.urbanairship.util.UriUtils;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowActivity;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.shadows.ShadowView.clickOn;

public class ChannelCaptureActivityTest extends BaseTestCase {

    private ChannelCaptureActivity channelCaptureActivity;
    private ShadowActivity shadowChannelCaptureActivity;

    @Before
    public void setUp() {
        Intent intent = new Intent(RuntimeEnvironment.application, ChannelCaptureActivity.class);
        intent.putExtra(ChannelCapture.CHANNEL, "channel id");
        intent.putExtra(ChannelCapture.URL, "https://go.urbanairship.com/lol");

        channelCaptureActivity = Robolectric.buildActivity(ChannelCaptureActivity.class)
                .withIntent(intent)
                .create()
                .start()
                .visible()
                .get();

        shadowChannelCaptureActivity = shadowOf(channelCaptureActivity);
    }

    @Test
    public void testOnShareButtonClicked() {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, "channel id");

        Button shareButton = (Button) channelCaptureActivity.findViewById(R.id.share_button);
        clickOn(shareButton);

        Intent shadowIntent = (Intent) shadowChannelCaptureActivity.getNextStartedActivity().getExtras().get(Intent.EXTRA_INTENT);
        assertEquals(intent.getAction(), shadowIntent.getAction());
        assertEquals(intent.getExtras().size(), shadowIntent.getExtras().size());
        assertEquals(intent.getExtras().get(Intent.EXTRA_TEXT), shadowIntent.getExtras().get(Intent.EXTRA_TEXT));
    }

    @Test
    public void testOnCopyButtonClicked() {
        Button copyButton = (Button) channelCaptureActivity.findViewById(R.id.copy_button);
        clickOn(copyButton);

        ClipboardManager clipboardManager = (ClipboardManager) RuntimeEnvironment.application.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("channel id", clipboardManager.getPrimaryClip().getItemAt(0).getText());
    }

    @Test
    public void testOnOpenUrlButtonClicked() {
        Uri uri = UriUtils.parse("https://go.urbanairship.com/lol");

        Button openButton = (Button) channelCaptureActivity.findViewById(R.id.open_button);
        clickOn(openButton);

        Intent shadowIntent = shadowChannelCaptureActivity.getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, shadowIntent.getAction());
        assertEquals(uri, shadowIntent.getData());
    }
}
