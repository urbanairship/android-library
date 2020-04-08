/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import android.graphics.Color;

import com.urbanairship.iam.modal.ModalDisplayContent;
import com.urbanairship.json.JsonException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link HtmlDisplayContent} tests.
 */
@RunWith(AndroidJUnit4.class)
public class HtmlDisplayContentTest {

    @Test
    public void testJson() throws JsonException {
        HtmlDisplayContent content = HtmlDisplayContent.newBuilder()
                                                       .setUrl("www.cool.story")
                                                       .setDismissButtonColor(Color.BLUE)
                                                       .setBackgroundColor(Color.RED)
                                                       .setBorderRadius(10)
                                                       .setAllowFullscreenDisplay(true)
                                                       .setRequireConnectivity(false)
                                                       .setSize(100, 200, false)
                                                       .build();

        HtmlDisplayContent fromJson = HtmlDisplayContent.fromJson(content.toJsonValue());
        assertEquals(content, fromJson);
        assertEquals(content.hashCode(), fromJson.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingUrl() {
        ModalDisplayContent.newBuilder()
                           .build();
    }

}
