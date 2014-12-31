package com.urbanairship.push;

import com.urbanairship.RobolectricGradleTestRunner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class ChannelRegistrationPayloadTest {

    private final boolean testOptIn = true;
    private final boolean testBackgroundEnabled = true;
    private final String testAlias = "fakeAlias";
    private final String testDeviceType = "android";
    private final String testPushAddress = "gcmRegistrationId";
    private final String testUserId = "fakeUserId";
    private final String testApid = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
    private final boolean testSetTags = true;
    private Set<String> testTags;

    private ChannelRegistrationPayload payload;

    // These are just for convenience
    private JSONObject body;

    @Before
    public void setUp() throws Exception {
        testTags = new HashSet<>();
        testTags.add("tagOne");
        testTags.add("tagTwo");
    }

    /**
     * Test that the json has the full expected payload.
     */
    @Test
    public void testAsJsonFullPayload() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setBackgroundEnabled(testBackgroundEnabled)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid).build();

        body = payload.asJSON();

        // Top level fields
        assertTrue("Channel should be present in payload.", body.has(ChannelRegistrationPayload.CHANNEL_KEY));
        assertTrue("Identity hints should be present in payload.", body.has(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));

        JSONObject identityHints = body.getJSONObject(ChannelRegistrationPayload.IDENTITY_HINTS_KEY);
        JSONObject channel = body.getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);

        // Identity items
        assertTrue("User ID should be present in payload.", identityHints.has(ChannelRegistrationPayload.USER_ID_KEY));
        assertEquals("User ID should be fakeUserId.", identityHints.getString(ChannelRegistrationPayload.USER_ID_KEY), testUserId);

        assertTrue("APID should be present in payload.", identityHints.has(ChannelRegistrationPayload.APID_KEY));
        assertEquals("APID should match.", identityHints.getString(ChannelRegistrationPayload.APID_KEY), testApid);

        // Channel specific items
        assertTrue("Device type should be present in payload.", channel.has(ChannelRegistrationPayload.DEVICE_TYPE_KEY));
        assertEquals("Device type should be android.", channel.getString(ChannelRegistrationPayload.DEVICE_TYPE_KEY), testDeviceType);
        assertTrue("Opt in should be present in payload.", channel.has(ChannelRegistrationPayload.OPT_IN_KEY));
        assertEquals("Opt in should be true.", channel.getBoolean(ChannelRegistrationPayload.OPT_IN_KEY), testOptIn);
        assertTrue("Background flag should be present in payload.", channel.has(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY));
        assertEquals("Background flag should be true.", channel.getBoolean(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY), testBackgroundEnabled);
        assertTrue("Push address should be present in payload.", channel.has(ChannelRegistrationPayload.PUSH_ADDRESS_KEY));
        assertEquals("Push address should be gcmRegistrationId.", channel.getString(ChannelRegistrationPayload.PUSH_ADDRESS_KEY), testPushAddress);
        assertTrue("Alias should be present in payload", channel.has(ChannelRegistrationPayload.ALIAS_KEY));
        assertEquals("Alias should be fakeAlias.", channel.getString(ChannelRegistrationPayload.ALIAS_KEY), testAlias);
        assertTrue("Set tags should be present in payload", channel.has(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be true.", channel.getBoolean(ChannelRegistrationPayload.SET_TAGS_KEY), testSetTags);
        assertTrue("Tags should be present in payload", channel.has(ChannelRegistrationPayload.TAGS_KEY));

        // Check the tags within channel item
        JSONArray tagsArray = channel.getJSONArray(ChannelRegistrationPayload.TAGS_KEY);
        assertEquals("Tags size should be 2.", tagsArray.length(), testTags.size());
        assertTrue("Tags should contain tagOne.", testTags.contains(tagsArray.getString(0)));
        assertTrue("Tags should contain tagTwo.", testTags.contains(tagsArray.getString(1)));
    }

    /**
     * Test when tags are empty.
     */
    @Test
    public void testAsJsonEmptyTags() throws JSONException {
        Set<String> emptyTags = new HashSet<>();

        // Create payload with empty tags
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setTags(testSetTags, emptyTags).build();
        JSONObject body = payload.asJSON();
        JSONObject channel = body.getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);

        // Verify setTags is true in order for tags to be present in payload
        assertTrue("Set tags should be present in payload.", channel.has(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be true.", channel.getBoolean(ChannelRegistrationPayload.SET_TAGS_KEY), testSetTags);

        // Verify tags are present, but empty
        assertTrue("Tags should be present in payload.", channel.has(ChannelRegistrationPayload.TAGS_KEY));
        JSONArray tagsArray = channel.getJSONArray(ChannelRegistrationPayload.TAGS_KEY);
        assertEquals("Tags size should be 0.", tagsArray.length(), emptyTags.size());
    }

    /**
     * Test that tags are not sent when setTags is false.
     */
    @Test
    public void testAsJsonNoTags() throws JSONException {
        // Create payload with setTags is false
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setTags(false, testTags).build();
        JSONObject body = payload.asJSON();
        JSONObject channel = body.getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);

        // Verify setTags is present and is false
        assertTrue("Set tags should be present in payload.", channel.has(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be false.", channel.getBoolean(ChannelRegistrationPayload.SET_TAGS_KEY), false);

        // Verify tags are not present
        assertFalse("Tags should not be present in payload.", channel.has(ChannelRegistrationPayload.TAGS_KEY));
    }

    /**
     * Test that an empty identity hints section is not included.
     */
    @Test
    public void testAsJsonEmptyIdentityHints() throws JSONException {
        // Create empty payload
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        JSONObject body = payload.asJSON();

        // Verify the identity hints section is not included
        assertFalse("Identity hints should not be present in payload.", body.has(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));
    }

    /**
     * Test that an empty user ID is not included in the identity hints.
     */
    @Test
    public void testAsJsonEmptyUserId() throws JSONException {
        // Create payload with empty userId
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().setUserId("").build();
        JSONObject body = payload.asJSON();

        // Verify the identity hints section is not included
        assertFalse("Identity hints should not be present in payload.", body.has(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));
    }

    /**
     * Test that an empty alias is not included.
     */
    @Test
    public void testAsJsonEmptyAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias("").build();

        // Channel specific items
        JSONObject channel = payload.asJSON().getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);
        assertFalse("Alias should not be present in payload.", channel.has(ChannelRegistrationPayload.ALIAS_KEY));
    }

    /**
     * Test that an alias with just spaces is not included.
     */
    @Test
    public void testAsJsonSpacesAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias("         ").build();

        // Channel specific items
        JSONObject channel = payload.asJSON().getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);
        assertFalse("Alias should not be present in payload.", channel.has(ChannelRegistrationPayload.ALIAS_KEY));
    }

    /**
     * Test that an alias with spaces is trimmed and included.
     */
    @Test
    public void testAsJsonSpacesTrimmedAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias("     fakeAlias     ").build();

        // Channel specific items
        JSONObject channel = payload.asJSON().getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);

        assertTrue("Alias should be present in payload", channel.has(ChannelRegistrationPayload.ALIAS_KEY));
        assertEquals("Alias should be fakeAlias.", channel.getString(ChannelRegistrationPayload.ALIAS_KEY), testAlias);
    }

    /**
     * Test that a null alias is not included.
     */
    @Test
    public void testAsJsonNullAlias() throws JSONException {
        payload = new ChannelRegistrationPayload.Builder()
                .setAlias(null).build();

        // Channel specific items
        JSONObject channel = payload.asJSON().getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);
        assertFalse("Alias should not be present in payload.", channel.has(ChannelRegistrationPayload.ALIAS_KEY));
    }

    /**
     * Test an empty builder.
     */
    @Test
    public void testEmptyBuilder() throws JSONException {
        // Create an empty payload
        ChannelRegistrationPayload payload = new ChannelRegistrationPayload.Builder().build();
        JSONObject body = payload.asJSON();

        // Top level fields
        assertTrue("Channel should be present in payload.", body.has(ChannelRegistrationPayload.CHANNEL_KEY));
        assertFalse("Identity hints should not be present in payload.", body.has(ChannelRegistrationPayload.IDENTITY_HINTS_KEY));

        // Channel specific items
        JSONObject channel = body.getJSONObject(ChannelRegistrationPayload.CHANNEL_KEY);
        assertTrue("Opt in should be present in payload.", channel.has(ChannelRegistrationPayload.OPT_IN_KEY));
        assertEquals("Opt in should be false.", channel.getBoolean(ChannelRegistrationPayload.OPT_IN_KEY), false);
        assertTrue("Background flag should be present in payload.", channel.has(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY));
        assertEquals("Background flag should be false.", channel.getBoolean(ChannelRegistrationPayload.BACKGROUND_ENABLED_KEY), false);
        assertFalse("Push address should not be present in payload.", channel.has(ChannelRegistrationPayload.PUSH_ADDRESS_KEY));
        assertFalse("Alias should not be present in payload.", channel.has(ChannelRegistrationPayload.ALIAS_KEY));
        assertTrue("Set tags should be present in payload.", channel.has(ChannelRegistrationPayload.SET_TAGS_KEY));
        assertEquals("Set tags should be false.", channel.getBoolean(ChannelRegistrationPayload.SET_TAGS_KEY), false);
        assertFalse("Tags should not be present in payload.", channel.has(ChannelRegistrationPayload.TAGS_KEY));
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
    public void testCreateFromJSON() {
        payload = new ChannelRegistrationPayload.Builder()
                .setOptIn(testOptIn)
                .setAlias(testAlias)
                .setDeviceType(testDeviceType)
                .setPushAddress(testPushAddress)
                .setTags(testSetTags, testTags)
                .setUserId(testUserId)
                .setApid(testApid).build();

        ChannelRegistrationPayload jsonPayload = ChannelRegistrationPayload.createFromJSON(payload.asJSON());
        assertTrue("Payloads should match.", payload.equals(jsonPayload));
    }

    /**
     * Test payload created from empty JSON
     */
    @Test
    public void testCreateFromEmptyJSON() {
        assertNull("Creating a payload from an empty json object should return null.",
                ChannelRegistrationPayload.createFromJSON(new JSONObject()));
    }

    /**
     * Test payload created from null JSON
     */
    @Test
    public void testCreateFromNullJSON() {
        assertNull("Creating a payload from a null json object should return null.",
                ChannelRegistrationPayload.createFromJSON(new JSONObject()));
    }

}
