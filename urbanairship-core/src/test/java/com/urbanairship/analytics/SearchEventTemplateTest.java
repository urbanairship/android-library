/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;

public class SearchEventTemplateTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    Analytics analytics;

    @Before
    public void setup() {
        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);
    }

    /**
     * Test basic Search Event with no optional value or properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBasicSearchEvent() throws JSONException {
        CustomEvent event = SearchEventTemplate.newSearchTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", "search");
        EventTestUtils.validateEventValue(event, "template_type", "search");
        EventTestUtils.validateNestedEventValue(event, "properties", "total_results", 0);
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test Search Event with optional value and properties.
     *
     * @throws JSONException
     */
    @Test
    public void testSearchEvent() throws JSONException {
        CustomEvent event = SearchEventTemplate.newSearchTemplate()
                .setValue(new BigDecimal(123))
                .setType("FakeType")
                .setQuery("FakeQuery")
                .setCategory("FakeCategory")
                .setId("FakeID")
                .setTotalResults(23)
                .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", "search");
        EventTestUtils.validateEventValue(event, "template_type", "search");
        EventTestUtils.validateEventValue(event, "event_value", 123000000L);
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "FakeType");
        EventTestUtils.validateNestedEventValue(event, "properties", "query", "FakeQuery");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "FakeCategory");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "FakeID");
        EventTestUtils.validateNestedEventValue(event, "properties", "total_results", 23);
    }
}
