/* Copyright 2017 Urban Airship and Contributors */

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

public class AccountEventTemplateTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    Analytics analytics;

    @Before
    public void setup() {
        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);
    }

    /**
     * Test basic AccountEvent with no optional value or properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBasicAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newRegisteredTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.REGISTERED_ACCOUNT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test AccountEvent with optional value and properties.
     * @throws JSONException
     */
    @Test
    public void testAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newRegisteredTemplate()
                                                .setValue(new BigDecimal(123))
                                                .setTransactionId("Wednesday 11/4/2015")
                                                .setCategory("Premium")
                                                .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.REGISTERED_ACCOUNT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateEventValue(event, "event_value", 123000000L);
        EventTestUtils.validateEventValue(event, "transaction_id", "Wednesday 11/4/2015");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"Premium\"");
    }
}
