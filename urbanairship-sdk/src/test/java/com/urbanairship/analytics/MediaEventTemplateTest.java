/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.mockito.Mockito.mock;

public class MediaEventTemplateTest extends BaseTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    Analytics analytics;

    @Before
    public void setup() {
        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);
    }

    /**
     * Test browsed event.
     *
     * @throws JSONException
     */
    @Test
    public void testBrowsedEventBasic() throws JSONException {
        CustomEvent event = MediaEventTemplate.newBrowsedTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.BROWSED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test browsed content event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBrowsedEvent() throws JSONException {
        CustomEvent event = MediaEventTemplate.newBrowsedTemplate()
                                              .setCategory("media-category")
                                              .setId("starred-content-ID 1")
                                              .setDescription("This is a starred content media event.")
                                              .setType("audio type")
                                              .setAuthor("The Cool UA")
                                              .setFeature(true)
                                              .setPublishedDate("November 4, 2015")
                                              .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.BROWSED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"media-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"starred-content-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a starred content media event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "\"audio type\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "author", "\"The Cool UA\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "feature", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "published_date", "\"November 4, 2015\"");
    }

    /**
     * Test starred content event.
     *
     * @throws JSONException
     */
    @Test
    public void testStarredEventBasic() throws JSONException {
        CustomEvent event = MediaEventTemplate.newStarredTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.STARRED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test starred content event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testStarredEvent() throws JSONException {
        CustomEvent event = MediaEventTemplate.newStarredTemplate()
                                              .setCategory("media-category")
                                              .setId("starred-content-ID 1")
                                              .setDescription("This is a starred content media event.")
                                              .setType("audio type")
                                              .setAuthor("The Cool UA")
                                              .setFeature(true)
                                              .setPublishedDate("November 4, 2015")
                                              .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.STARRED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"media-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"starred-content-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a starred content media event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "\"audio type\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "author", "\"The Cool UA\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "feature", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "published_date", "\"November 4, 2015\"");
    }

    /**
     * Test shared content event.
     *
     * @throws JSONException
     */
    @Test
    public void testSharedEventBasic() throws JSONException {
        CustomEvent event = MediaEventTemplate.newSharedTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.SHARED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test shared content event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testSharedEvent() throws JSONException {
        CustomEvent event = MediaEventTemplate.newSharedTemplate("facebook", "social")
                                              .setCategory("media-category")
                                              .setId("shared-content-ID 2")
                                              .setDescription("This is a shared content media event.")
                                              .setType("video type")
                                              .setAuthor("The Cool UA")
                                              .setFeature(true)
                                              .setPublishedDate("November 4, 2015")
                                              .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.SHARED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "source", "\"facebook\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "medium", "\"social\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"media-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"shared-content-ID 2\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a shared content media event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "\"video type\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "author", "\"The Cool UA\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "feature", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "published_date", "\"November 4, 2015\"");
    }

    /**
     * Test consumed content event.
     *
     * @throws JSONException
     */
    @Test
    public void testConsumedEventBasic() throws JSONException {
        CustomEvent event = MediaEventTemplate.newConsumedTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.CONSUMED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test consumed content event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testConsumedEvent() throws JSONException {
        CustomEvent event = MediaEventTemplate.newConsumedTemplate(2.99)
                                              .setCategory("media-category")
                                              .setId("consumed-content-ID 1")
                                              .setDescription("This is a consumed content media event.")
                                              .setType("audio type")
                                              .setAuthor("The Cool UA")
                                              .setFeature(true)
                                              .setPublishedDate("November 4, 2015")
                                              .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", MediaEventTemplate.CONSUMED_CONTENT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "media");
        EventTestUtils.validateEventValue(event, "event_value", 2990000.0);
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"media-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"consumed-content-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a consumed content media event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "\"audio type\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "author", "\"The Cool UA\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "feature", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "published_date", "\"November 4, 2015\"");
    }
}
