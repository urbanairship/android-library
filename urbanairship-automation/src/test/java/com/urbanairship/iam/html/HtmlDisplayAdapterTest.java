/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import com.urbanairship.iam.InAppMessage;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class HtmlDisplayAdapterTest {

    private InAppMessage htmlMessage;

    @Before
    public void setup() {
        htmlMessage = InAppMessage.newBuilder()
                                  .setDisplayContent(HtmlDisplayContent.newBuilder()
                                                                       .setUrl("https://www.urbanairship.com")
                                                                       .build())
                                  .build();
    }

    @Test
    public void testCreateAdapter() {
        try {
            HtmlDisplayAdapter.newAdapter(htmlMessage);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

}
