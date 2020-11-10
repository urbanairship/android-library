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
     * Test basic Registered AccountEvent with no optional value or properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBasicRegisteredAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newRegisteredTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.REGISTERED_ACCOUNT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test basic Logged in AccountEvent with no optional value or properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBasicLoggedInAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newLoggedInTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.LOGGED_IN);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test basic Logged out AccountEvent with no optional value or properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBasicLoggedOutAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newLoggedOutTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.LOGGED_OUT);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test Registered AccountEvent with optional value and properties.
     *
     * @throws JSONException
     */
    @Test
    public void testRegisteredAccountEvent() throws JSONException {
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
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "Premium");
    }

    /**
     * Test Logged in AccountEvent with optional value and properties.
     *
     * @throws JSONException
     */
    @Test
    public void testLoggedInAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newLoggedInTemplate()
                .setValue(new BigDecimal(123))
                .setTransactionId("Wednesday 11/4/2015")
                .setCategory("Premium")
                .setUserId("FakeUserId")
                .setType("FakeType")
                .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.LOGGED_IN);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateEventValue(event, "event_value", 123000000L);
        EventTestUtils.validateEventValue(event, "transaction_id", "Wednesday 11/4/2015");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "Premium");
        EventTestUtils.validateNestedEventValue(event, "properties", "user_id", "FakeUserId");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "FakeType");
    }

    /**
     * Test Logged out AccountEvent with optional value and properties.
     *
     * @throws JSONException
     */
    @Test
    public void testLoggedOutAccountEvent() throws JSONException {
        CustomEvent event = AccountEventTemplate.newLoggedOutTemplate()
                .setValue(new BigDecimal(123))
                .setTransactionId("Wednesday 11/4/2015")
                .setCategory("Premium")
                .setUserId("FakeUserId")
                .setType("FakeType")
                .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", AccountEventTemplate.LOGGED_OUT);
        EventTestUtils.validateEventValue(event, "template_type", "account");
        EventTestUtils.validateEventValue(event, "event_value", 123000000L);
        EventTestUtils.validateEventValue(event, "transaction_id", "Wednesday 11/4/2015");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "Premium");
        EventTestUtils.validateNestedEventValue(event, "properties", "user_id", "FakeUserId");
        EventTestUtils.validateNestedEventValue(event, "properties", "type", "FakeType");
    }

}
