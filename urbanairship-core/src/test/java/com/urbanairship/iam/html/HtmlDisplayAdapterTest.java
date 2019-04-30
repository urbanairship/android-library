/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.InAppMessage;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

public class HtmlDisplayAdapterTest extends BaseTestCase {

    private InAppMessage htmlMessage;

    @Before
    public void setup() {
        htmlMessage = InAppMessage.newBuilder()
                                  .setDisplayContent(HtmlDisplayContent.newBuilder()
                                                                       .setUrl("https://www.urbanairship.com")
                                                                       .build())
                                  .setId("message id")
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