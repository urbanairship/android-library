/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import junit.framework.Assert;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ChannelRegistrationPayloadTest extends BaseTestCase {

    private final boolean testOptIn = true;
    private final boolean testBackgroundEnabled = true;
    private final String testAlias = "fakeAlias";
    private final String testDeviceType = "android";
    private final String testPushAddress = "gcmRegistrationId";
    private final String testUserId = "fakeUserId";
    private final String testApid = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final boolean testSetTags = true;
    private Set<String> testTags;
    private final String testLanguage = "test_language";
    private final String testTimezone = "test_timezone";
    private final String testCountry = "test_country";

    private ChannelRegistrationPayload payload;


    @Before
    public void setUp() throws Exception {
        testTags = new HashSet<>();
        testTags.add("tagOne");
        testTags.add("tagTwo");
    }

    /**
     * Test that the json has the full expected payload when analytics is enabled.
     */
    @Test
    public void testAsJsonFullPayloadAnalyticsEnabled() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid)
                .setLanguage(testLanguage)
                .setTimezone(testTimezone)
                .setCountry(testCountry)
                .build();

        JsonMap body = payload.toJsonValue().getMap();

        // Top level fields
        assertTrue("Channel should be present in payload.", body.containsKey(ChannelRegistrationPayload.CHANNEL_KEY));
        assertTrue("Identity hints should be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));

        JsonMap identityHints = body.opt(ChannelRegistrationPayload.IDENTITY_HINTS_KEY).getMap();
        JsonMap channel = body.opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        // Identity items
        assertTrue("User ID should be present in payload.", identityHints.containsKey(ChannelRegistrationPayload.USER_ID_KEY));
        assertEquals("User ID should be fakeUserId.", identityHints.get(ChannelRegistrationPayload.USER_ID_KEY).getString(), testUserId);

        assertTrue("APID should be present in payload.", identityHints.containsKey(ChannelRegistrationPayload.APID_KEY));
        assertEquals("APID should match.", identityHints.get(ChannelRegistrationPayload.APID_KEY).getString(), testApid);

        // Channel specific items
        assertTrue("Device type should be present in payload.", channel.containsKey(ChannelRegistrationPayload.DEVICE_TYPE_KEY));
        assertEquals("Device type should be android.", channel.get(ChannelRegistrationPayload.DEVICE_TYPE_KEY).getString(), testDeviceType);
        assertTrue("Opt in should be present in payload.", channel.containsKey(ChannelRegistrationPayload.OPT_IN_KEY));
        assertEquals("Opt in should be true.", channel.get(ChannelRegistrationPayload.OPT_IN_KEY).getBoolean(!testOptIn), testOptIn);
        assertTrue("Background flag should be present in payload.", channel.containsKey(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY));
        assertEquals("Background flag should be true.", channel.get(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY).getBoolean(!testBackgroundEnabled), testBackgroundEnabled);
        assertTrue("Push address should be present in payload.", channel.containsKey(ChannelRegistrationPayload.PUSH_ADDRESS_KEY));
        assertEquals("Push address should be gcmRegistrationId.", channel.get(ChannelRegistrationPayload.PUSH_ADDRESS_KEY).getString(), testPushAddress);
        assertTrue("Alias should be present in payload", channel.containsKey(ChannelRegistrationPayload.ALIAS_KEY));
        assertEquals("Alias should be fakeAlias.", channel.get(ChannelRegistrationPayload.ALIAS_KEY).getString(), testAlias);
        assertTrue("Set tags should be present in payload", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be true.", channel.get(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(!testSetTags), testSetTags);
        assertTrue("Tags should be present in payload", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));

        assertTrue("Timezone should be in payload", channel.containsKey(ChannelRegistrationPayload.TIMEZONE_KEY));
        assertTrue("Language should be in payload", channel.containsKey(ChannelRegistrationPayload.LANGUAGE_KEY));
        assertTrue("Country should be in payload", channel.containsKey(ChannelRegistrationPayload.COUNTRY_KEY));

        // Check the tags within channel item
        JsonList tags = channel.get(ChannelRegistrationPayload.TAGS_KEY).getList();
        assertEquals("Tags size should be 2.", tags.size(), testTags.size());
        assertTrue("Tags should contain tagOne.", testTags.contains(tags.get(0).getString()));
        assertTrue("Tags should contain tagTwo.", testTags.contains(tags.get(1).getString()));
    }

    /**
     * Test when tags are empty.
     */
    @Test
    public void testAsJsonEmptyTags() throws JSONException {
        // Create payload with empty tags
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setTags(testSetTags, new HashSet<String>()).build();
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        // Verify setTags is true in order for tags to be present in payload
        assertTrue("Set tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be true.", channel.get(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(!testSetTags), testSetTags);

        // Verify tags are present, but empty
        assertTrue("Tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));

        JsonList tags = channel.opt(ChannelRegistrationPayload.TAGS_KEY).getList();
        Assert.assertTrue("Tags size should be 0.", tags.isEmpty());
    }

    /**
     * Test that tags are not sent when setTags is false.
     */
    @Test
    public void testAsJsonNoTags() throws JSONException {
        // Create payload with setTags is false
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setTags(false, testTags).build();
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        // Verify setTags is present and is false
        assertTrue("Set tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be false.", channel.opt(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(true), false);

        // Verify tags are not present
        assertFalse("Tags should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));
    }

    /**
     * Test that an empty identity hints section is not included.
     */
    @Test
    public void testAsJsonEmptyIdentityHints() throws JSONException {
        // Create empty payload
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        JsonMap body = payload.toJsonValue().getMap();

        // Verify the identity hints section is not included
        assertFalse("Identity hints should not be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));
    }

    /**
     * Test that an empty user ID is not included in the identity hints.
     */
    @Test
    public void testAsJsonEmptyUserId() throws JSONException {
        // Create payload with empty userId
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setUserId("").build();
        JsonMap body = payload.toJsonValue().getMap();

        // Verify the identity hints section is not included
        assertFalse("Identity hints should not be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));
    }

    /**
     * Test that an empty alias is not included.
     */
    @Test
    public void testAsJsonEmptyAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias("").build();

        // Channel specific items
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();
        assertFalse("Alias should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.ALIAS_KEY));
    }

    /**
     * Test that an alias with just spaces is not included.
     */
    @Test
    public void testAsJsonSpacesAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias("         ").build();

        // Channel specific items
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();
        assertFalse("Alias should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.ALIAS_KEY));
    }

    /**
     * Test that an alias with spaces is trimmed and included.
     */
    @Test
    public void testAsJsonSpacesTrimmedAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias("     fakeAlias     ").build();

        // Channel specific items
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();

        assertTrue("Alias should be present in payload", channel.containsKey(ChannelRegistrationPayload.ALIAS_KEY));
        assertEquals("Alias should be fakeAlias.", channel.get(ChannelRegistrationPayload.ALIAS_KEY).getString(), testAlias);
    }

    /**
     * Test that a null alias is not included.
     */
    @Test
    public void testAsJsonNullAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias(null).build();

        // Channel specific items
        JsonMap channel = payload.toJsonValue().getMap().opt(ChannelRegistrationPayload.CHANNEL_KEY).getMap();
        assertFalse("Alias should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.ALIAS_KEY));
    }

    /**
     * Test an empty builder.
     */
    @Test
    public void testEmptyBuilder() throws JSONException {
        // Create an empty payload
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        JsonMap body = payload.toJsonValue().getMap();

        // Top level fields
        assertTrue("Channel should be present in payload.", body.containsKey(ChannelRegistrationPayload.CHANNEL_KEY));
        assertFalse("Identity hints should not be present in payload.", body.containsKey(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));

        // Channel specific items
        JsonMap channel = body.get(ChannelRegistrationPayload.CHANNEL_KEY).getMap();
        assertTrue("Opt in should be present in payload.", channel.containsKey(ChannelRegistrationPayload.OPT_IN_KEY));
        assertEquals("Opt in should be false.", channel.get(ChannelRegistrationPayload.OPT_IN_KEY).getBoolean(true), false);
        assertTrue("Background flag should be present in payload.", channel.containsKey(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY));
        assertEquals("Background flag should be false.", channel.get(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY).getBoolean(true), false);
        assertFalse("Push address should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.PUSH_ADDRESS_KEY));
        assertFalse("Alias should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.ALIAS_KEY));
        assertTrue("Set tags should be present in payload.", channel.containsKey(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be false.", channel.get(ChannelRegistrationPayload.SET_TAGS_KEY).getBoolean(true), false);
        assertFalse("Tags should not be present in payload.", channel.containsKey(ChannelRegistrationPayload.TAGS_KEY));
    }

    /**
     * Test when payload is equal to itself
     */
    @Test
    public void testPayloadEqualToItself() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid).build();

        assertTrue("Payload should be equal to itself.", payload.equals(payload));
    }

    /**
     * Test when payloads are the same
     */
    @Test
    public void testPayloadsEqual() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid)
                .setBackgroundEnabled(testBackgroundEnabled).build();

        ChannelRegistrationPayload payload2 = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid)
                .setBackgroundEnabled(testBackgroundEnabled).build();

        assertTrue("Payloads should match.", payload.equals(payload2));
        assertEquals("The hashCode for the payloads should match.", payload.hashCode(), payload2.hashCode());
    }

    /**
     * Test when payloads are not equal
     */
    @Test
    public void testPayloadsNotEqual() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid)
                .setBackgroundEnabled(testBackgroundEnabled).build();

        ChannelRegistrationPayload emptyPayload = new ChannelRegistrationPayload.Builder()
                .setOptIn(false)
                .setAlias(null)
                .setDeviceType(testDeviceType)
                .setPushAddress(null)
                .setTags(false, null)
                .setUserId(null)
                .setApid(null)
                .setBackgroundEnabled(!testBackgroundEnabled).build();

        assertFalse("Payloads should not match.", payload.equals(emptyPayload));
        assertNotSame("The hashCode for the payloads should not match.", payload.hashCode(), emptyPayload.hashCode());
    }

    /**
     * Test empty payloads are equal
     */
    @Test
    public void testEmptyPayloadsEqual() {
        ChannelRegistrationPayload payload1 = new ChannelRegistrationPayload.Builder().build();
        ChannelRegistrationPayload payload2 = new ChannelRegistrationPayload.Builder().build();
        assertTrue("Payloads should match.", payload1.equals(payload2));
        assertEquals("The hashCode for the payloads should match.", payload1.hashCode(), payload2.hashCode());
    }

    /**
     * Test payload created from JSON
     */
    @Test
    public void testCreateFromJSON() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid).build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.parseJson(payload.toJsonValue());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
        assertEquals("Payloads should match.", payload.hashCode(), jsonPayload.hashCode());

    }

    /**
     * Test payload created from empty JSON
     */
    @Test(expected = JsonException.class)
    public void testCreateFromEmptyJSON() throws JsonException {
        ChannelRegistrationPayload.parseJson(JsonValue.NULL);
    }

    @Test
    public void testFromJsonNoTags() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setUserId(testUserId)
                .setApid(testApid).build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.parseJson(payload.toJsonValue());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
        assertEquals("Payloads should match.", payload.hashCode(), jsonPayload.hashCode());
    }


    @Test
    public void testFromJsonEmptyAlias() throws JsonException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias("")
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setUserId(testUserId)
                .setApid(testApid).build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.parseJson(payload.toJsonValue());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
        assertEquals("Payloads should match.", payload.hashCode(), jsonPayload.hashCode());
    }
}
