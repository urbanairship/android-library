/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.ContentResolver;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

public class UrbanAirshipProviderTest extends BaseTestCase {

    private ContentResolver resolver;
    private Uri richPushUri;

    @Before
    public void setup() {
        resolver = RuntimeEnvironment.application.getContentResolver();
        richPushUri = UrbanAirshipProvider.getRichPushContentUri(TestApplication.getApplication());

        // start with empty databases
        this.resolver.delete(this.richPushUri, null, null);
    }

    @Test
    @Config(shadows = { CustomShadowContentResolver.class })
    public void testGetType() {
        Uri messagesUri = this.richPushUri;
        assertEquals(UrbanAirshipProvider.RICH_PUSH_CONTENT_TYPE, this.resolver.getType(messagesUri));

        Uri messageUri = Uri.withAppendedPath(this.richPushUri, "this.should.work");
        assertEquals(UrbanAirshipProvider.RICH_PUSH_CONTENT_ITEM_TYPE, this.resolver.getType(messageUri));

        Uri failureUri = Uri.parse("content://com.urbanairship/garbage");
        assertNull(this.resolver.getType(failureUri));
    }

}
