/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;

import org.json.JSONException;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class InstallAttributionEventTest extends BaseTestCase {

    @Test
    public void testData() throws JSONException {
        InstallAttributionEvent event = new InstallAttributionEvent("referrer");
        EventTestUtils.validateEventValue(event, "google_play_referrer", "referrer");
    }

    @Test
    public void testType() {
        InstallAttributionEvent event = new InstallAttributionEvent("referrer");
        assertEquals("install_attribution", event.getType());
    }
}
