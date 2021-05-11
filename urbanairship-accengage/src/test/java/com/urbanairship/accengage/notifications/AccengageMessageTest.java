package com.urbanairship.accengage.notifications;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;

import com.urbanairship.accengage.AccengageMessage;
import com.urbanairship.push.PushMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;

import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AccengageMessageTest {

    @Test(expected = IllegalArgumentException.class)
    public void testFromAirshipPushMessage() {
        Bundle extras = new Bundle();
        PushMessage pushMessage = new PushMessage(extras);

        AccengageMessage.fromAirshipPushMessage(pushMessage);
    }

    @Test
    public void testGetAccengageTitle() {
        Bundle extras = new Bundle();
        extras.putString("a4stitlehtml", "testTitle");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push title should be testTitle", "testTitle",
                accengageMessage.getAccengageTitle());
    }

    @Test
    public void testGetAccengageContent() {
        Bundle extras = new Bundle();
        extras.putString("a4scontenthtml", "testContent");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push content should be testContent", "testContent",
                accengageMessage.getAccengageContent());
    }

    @Test
    public void testGetAccengageSystemId() {
        Bundle extras = new Bundle();
        extras.putInt("a4ssysid", 1002);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push System ID should be 1002", 1002,
                accengageMessage.getAccengageSystemId());
    }

    @Test
    public void testGetAccengagePriority() {
        Bundle extras = new Bundle();
        extras.putInt("a4spriority", 3);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Priority should be 3", 3,
                accengageMessage.getAccengagePriority());
    }

    @Test
    public void testGetAccengageCategory() {
        Bundle extras = new Bundle();
        extras.putString("a4scategory", NotificationCompat.CATEGORY_EVENT);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Category should be Event", NotificationCompat.CATEGORY_EVENT,
                accengageMessage.getAccengageCategory());
    }

    @Test
    public void testGetAccengageAccentColor() {
        Bundle extras = new Bundle();
        extras.putString("a4saccentcolor", "#FF0000");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Color should be #FF0000", Color.parseColor("#FF0000"),
                accengageMessage.getAccengageAccentColor());
    }

    @Test
    public void testGetAccengageSmallIcon() {
        Application application = ApplicationProvider.getApplicationContext();
        Context context = Mockito.spy(application);
        Bundle extras = new Bundle();
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Icon Int should be 0", 0,
                accengageMessage.getAccengageSmallIcon(context));
    }

    @Test
    public void testGetAccengageNotificationSound() {
        Bundle extras = new Bundle();
        extras.putString("a4snotifsound", "testCustomSound");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Notification sound should be testCustomSound",
                "testCustomSound", accengageMessage.getAccengageNotificationSound());
    }

    @Test
    public void testGetAccengageGroup() {
        Bundle extras = new Bundle();
        extras.putString("a4sgroup", "testGroup");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Notification group should be testGroup",
                "testGroup", accengageMessage.getAccengageGroup());
    }

    @Test
    public void testGetAccengageGroupSummary() {
        Bundle extras = new Bundle();
        extras.putBoolean("a4sgroupsummary", true);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertTrue("The Push Notification group should be true",
                accengageMessage.getAccengageGroupSummary());
    }

    @Test
    public void testGetAccengageContentInfo() {
        Bundle extras = new Bundle();
        extras.putString("a4scontentinfo", "testContentInfo");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Content info should be testContentInfo",
                "testContentInfo", accengageMessage.getAccengageContentInfo());
    }

    @Test
    public void testGetAccengageSubtext() {
        Bundle extras = new Bundle();
        extras.putString("a4ssubtext", "testSubText");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Subtext should be testSubText",
                "testSubText", accengageMessage.getAccengageSubtext());
    }

    @Test
    public void testGetAccengageSummaryText() {
        Bundle extras = new Bundle();
        extras.putString("a4ssummarytext", "testSummaryText");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Summary Text should be testSummaryText",
                "testSummaryText", accengageMessage.getAccengageSummaryText());
    }

    @Test
    public void testIsAccengageMultipleLines() {
        Bundle extras = new Bundle();
        extras.putBoolean("a4smultiplelines", true);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertTrue("The Push Multiple lines should be true",
                accengageMessage.isAccengageMultipleLines());
    }

    @Test
    public void testGetAccengageBigTemplate() {
        Bundle extras = new Bundle();
        extras.putString("a4sbigtemplate", "testBigTemplate");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Big Template should be testBigTemplate",
                "testBigTemplate", accengageMessage.getAccengageBigTemplate());
    }

    @Test
    public void testGetAccengageTemplate() {
        Bundle extras = new Bundle();
        extras.putString("a4stemplate", "testTemplate");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push Template should be testTemplate",
                "testTemplate", accengageMessage.getAccengageTemplate());
    }

    @Test
    public void testGetAccengageBigContent() {
        Bundle extras = new Bundle();
        extras.putString("a4sbigcontenthtml", "testBigContent");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push BigContent should be testBigContent",
                "testBigContent", accengageMessage.getAccengageBigContent());
    }

    @Test
    public void testGetAccengageBigPictureUrl() {
        Bundle extras = new Bundle();
        extras.putString("a4sbigpicture", "testBigPictureUrl");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push BigPictureUrl should be testBigPictureUrl",
                "testBigPictureUrl", accengageMessage.getAccengageBigPictureUrl());
    }

    @Test
    public void testGetAccengageChannel() {
        Bundle extras = new Bundle();
        extras.putString("acc_channel", "testChannel");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push channel should be testChannel",
                "testChannel", accengageMessage.getAccengageChannel());
    }

    @Test
    public void testGetAccengageLargeIcon() {
        Bundle extras = new Bundle();
        extras.putString("a4sicon", "testIcon");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push icon should be testIcon",
                "testIcon", accengageMessage.getAccengageLargeIcon());
    }

    @Test
    public void testGetAccengageForeground() {
        Bundle extras = new Bundle();
        extras.putBoolean("a4sforeground", true);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertTrue("The Push Foreground property should be true",
                accengageMessage.getAccengageForeground());
    }

    @Test
    public void testGetAccengageAction() {
        Bundle extras = new Bundle();
        extras.putString("acc_action", "testAction");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push action should be testAction",
                "testAction", accengageMessage.getAccengageAction());
    }

    @Test
    public void testGetAccengageOpenWithBrowser() {
        Bundle extras = new Bundle();
        extras.putBoolean("openWithSafari", true);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertTrue("The Push open with browser should be true",
                accengageMessage.getAccengageOpenWithBrowser());
    }

    @Test
    public void testGetAccengageIsDecorated() {
        Bundle extras = new Bundle();
        extras.putBoolean("a4sIsDecorated", true);
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertTrue("The Push property isDecorated should be true",
                accengageMessage.getAccengageIsDecorated());
    }

    @Test
    public void testGetAccengageAppName() {
        Bundle extras = new Bundle();
        extras.putString("a4sappname", "testAppName");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push app name should be testAppName",
                "testAppName", accengageMessage.getAccengageAppName());
    }

    @Test
    public void testGetAccengageHeaderText() {
        Bundle extras = new Bundle();
        extras.putString("a4sheadertext", "testHeaderText");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push header text name should be testHeaderText",
                "testHeaderText", accengageMessage.getAccengageHeaderText());
    }

    @Test
    public void testGetExtra() {
        Bundle extras = new Bundle();
        extras.putString("testCustomKey", "testCustomValue");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push customKey's value should be customValue",
                "testCustomValue", accengageMessage.getExtra("testCustomKey"));
    }

    @Test
    public void testGetExtraDefaultValue() {
        Bundle extras = new Bundle();
        extras.putString("testCustomKey", "testCustomValue");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push customKey's value should be customValue",
                "testDefaultValue", accengageMessage.getExtra("fakeKey", "testDefaultValue"));
    }

    @Test
    public void testGetAccengageUrl() {
        Bundle extras = new Bundle();
        extras.putString("acc_url", "testAccUrl");
        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertEquals("The Push url value should be testAccUrl",
                "testAccUrl", accengageMessage.getAccengageUrl());
    }

    @Test
    public void testGetButtons() {
        Bundle extras = new Bundle();
        try {
            JSONArray array = new JSONArray();
            JSONObject object = new JSONObject();
            object.put("oa", true);
            object.put("icon", "com_ad4screen_sdk_notification_icon_yes");
            object.put("action", "webView");
            object.put("id", 1);
            object.put("title", "Accepter");
            object.put("url", "https://www.google.fr");
            array.put(object);

            JSONObject object2 = new JSONObject();
            object2.put("oa", false);
            object2.put("icon", "com_ad4screen_sdk_notification_icon_later");
            object2.put("action", "webView");
            object2.put("id", 2);
            object2.put("title", "Plus tard");
            object2.put("url", "");
            array.put(object2);
            extras.putString("a4sb", array.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        extras.putString("a4scontent", "accengage");
        extras.putInt("a4sid", 77);

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(pushMessage);

        Assert.assertTrue(accengageMessage.getButtons().get(0).getOpenApp());
        Assert.assertEquals("com_ad4screen_sdk_notification_icon_yes",
                accengageMessage.getButtons().get(0).getIconName());
        Assert.assertEquals("webView",
                accengageMessage.getButtons().get(0).getAccengageAction());
        Assert.assertEquals("1",
                accengageMessage.getButtons().get(0).getId());
        Assert.assertEquals("Accepter",
                accengageMessage.getButtons().get(0).getTitle());
        Assert.assertEquals("https://www.google.fr",
                accengageMessage.getButtons().get(0).getAccengageUrl());

        Assert.assertFalse(accengageMessage.getButtons().get(1).getOpenApp());
        Assert.assertEquals("com_ad4screen_sdk_notification_icon_later",
                accengageMessage.getButtons().get(1).getIconName());
        Assert.assertEquals("webView",
                accengageMessage.getButtons().get(1).getAccengageAction());
        Assert.assertEquals("2",
                accengageMessage.getButtons().get(1).getId());
        Assert.assertEquals("Plus tard",
                accengageMessage.getButtons().get(1).getTitle());
        Assert.assertEquals("",
                accengageMessage.getButtons().get(1).getAccengageUrl());
    }
}
