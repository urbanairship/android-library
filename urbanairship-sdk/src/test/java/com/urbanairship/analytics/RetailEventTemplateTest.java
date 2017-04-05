/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;

public class RetailEventTemplateTest extends BaseTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
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
        CustomEvent event = RetailEventTemplate.newBrowsedTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.BROWSED_PRODUCT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test browsed event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testBrowsedEvent() throws JSONException {
        CustomEvent event = RetailEventTemplate.newBrowsedTemplate()
                                               .setCategory("retail-category")
                                               .setId("browsed-ID 1")
                                               .setDescription("This is a browsed retail event.")
                                               .setValue(99.99)
                                               .setTransactionId("123")
                                               .setBrand("nike")
                                               .setNewItem(true)
                                               .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.BROWSED_PRODUCT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateEventValue(event, "event_value", 99990000);
        EventTestUtils.validateEventValue(event, "transaction_id", "123");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"retail-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"browsed-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a browsed retail event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "brand", "\"nike\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "new_item", "true");
    }

    /**
     * Test added to cart event.
     *
     * @throws JSONException
     */
    @Test
    public void testAddedToCartEventBasic() throws JSONException {
        CustomEvent event = RetailEventTemplate.newAddedToCartTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.ADDED_TO_CART_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test added to cart event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testAddedToCartEvent() throws JSONException {
        CustomEvent event = RetailEventTemplate.newAddedToCartTemplate()
                                               .setCategory("retail-category")
                                               .setId("added-to-cart-ID 1")
                                               .setDescription("This is an added to cart retail event.")
                                               .setValue(1.99)
                                               .setTransactionId("123")
                                               .setBrand("columbia")
                                               .setNewItem(true)
                                               .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.ADDED_TO_CART_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateEventValue(event, "event_value", 1990000);
        EventTestUtils.validateEventValue(event, "transaction_id", "123");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"retail-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"added-to-cart-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is an added to cart retail event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "brand", "\"columbia\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "new_item", "true");
    }

    /**
     * Test starred event.
     *
     * @throws JSONException
     */
    @Test
    public void testStarredEventBasic() throws JSONException {
        CustomEvent event = RetailEventTemplate.newStarredProductTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.STARRED_PRODUCT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test starred event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testStarredEvent() throws JSONException {
        CustomEvent event = RetailEventTemplate.newStarredProductTemplate()
                                               .setCategory("retail-category")
                                               .setId("starred-product-ID 1")
                                               .setDescription("This is a starred retail event.")
                                               .setValue(99.99)
                                               .setTransactionId("123")
                                               .setBrand("nike")
                                               .setNewItem(true)
                                               .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.STARRED_PRODUCT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateEventValue(event, "event_value", 99990000);
        EventTestUtils.validateEventValue(event, "transaction_id", "123");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"retail-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"starred-product-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a starred retail event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "brand", "\"nike\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "new_item", "true");
    }

    /**
     * Test shared event.
     *
     * @throws JSONException
     */
    @Test
    public void testSharedEventBasic() throws JSONException {
        CustomEvent event = RetailEventTemplate.newSharedProductTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.SHARED_PRODUCT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test shared event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testSharedEvent() throws JSONException {
        CustomEvent event = RetailEventTemplate.newSharedProductTemplate("facebook", "social")
                                               .setCategory("retail-category")
                                               .setId("shared-product-ID 1")
                                               .setDescription("This is a shared retail event.")
                                               .setValue(49.99)
                                               .setTransactionId("123")
                                               .setBrand("nike")
                                               .setNewItem(true)
                                               .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.SHARED_PRODUCT_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateEventValue(event, "event_value", 49990000);
        EventTestUtils.validateEventValue(event, "transaction_id", "123");
        EventTestUtils.validateNestedEventValue(event, "properties", "source", "\"facebook\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "medium", "\"social\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"retail-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"shared-product-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a shared retail event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "brand", "\"nike\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "new_item", "true");
    }

    /**
     * Test purchased event.
     *
     * @throws JSONException
     */
    @Test
    public void testPurchasedEventBasic() throws JSONException {
        CustomEvent event = RetailEventTemplate.newPurchasedTemplate().createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.PURCHASED_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "false");
    }

    /**
     * Test purchased event with optional properties.
     *
     * @throws JSONException
     */
    @Test
    public void testPurchasedEvent() throws JSONException {
        CustomEvent event = RetailEventTemplate.newPurchasedTemplate()
                                               .setCategory("retail-category")
                                               .setId("purchased-product-ID 1")
                                               .setDescription("This is a purchased retail event.")
                                               .setValue(99.99)
                                               .setTransactionId("123")
                                               .setBrand("nike")
                                               .setNewItem(true)
                                               .createEvent();

        EventTestUtils.validateEventValue(event, "event_name", RetailEventTemplate.PURCHASED_EVENT);
        EventTestUtils.validateEventValue(event, "template_type", "retail");
        EventTestUtils.validateEventValue(event, "event_value", 99990000);
        EventTestUtils.validateEventValue(event, "transaction_id", "123");
        EventTestUtils.validateNestedEventValue(event, "properties", "ltv", "true");
        EventTestUtils.validateNestedEventValue(event, "properties", "category", "\"retail-category\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "id", "\"purchased-product-ID 1\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "description", "\"This is a purchased retail event.\"");
        EventTestUtils.validateNestedEventValue(event, "properties", "brand", "\"nike\"");
    }
}
