/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;

/**
 * Attributes mutation tests.
 */
public class AttributeMutationTest extends BaseTestCase {
    @Test
    public void testSetStringAttribute() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\"}]";

        assertEquals(expected, toString(mutations));
    }

    @Test
    public void testRemoveStringAttribute() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key"));

        String expected = "[{\"action\":\"remove\",\"key\":\"expected_key\"}]";

        assertEquals(expected, toString(mutations));
    }

    @Test
    public void testSetAndRemoveString() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key"));

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\"},{\"action\":\"remove\",\"key\":\"expected_key\"}]";

        assertEquals(expected, toString(mutations));
    }

    @Test
    public void testSetNumberAttributes() {
        List<AttributeMutation> mutations = new ArrayList<>();

        int expectedInt = 11;
        long expectedLong = 11;
        float expectedFloat = 11.11f;
        double expectedDouble = 11.11;

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", expectedInt));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", expectedLong));

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", expectedFloat));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", expectedDouble));

        String expected = "[{\"action\":\"set\",\"value\":" + expectedInt + ",\"key\":\"expected_key\"}," +
                "{\"action\":\"set\",\"value\":" + expectedLong + ",\"key\":\"expected_key\"}," +
                "{\"action\":\"set\",\"value\":" + JsonValue.wrap(expectedFloat) + ",\"key\":\"expected_key\"}," +
                "{\"action\":\"set\",\"value\":" + expectedDouble + ",\"key\":\"expected_key\"}]";


        assertEquals(expected, toString(mutations));
    }

    @Test(expected = NumberFormatException.class)
    public void testNaNFloatAttributes() throws NumberFormatException {
        AttributeMutation.newSetAttributeMutation("expected_key", Float.NaN);
    }

    @Test(expected = NumberFormatException.class)
    public void testNaNDoubleAttributes() throws NumberFormatException {
       AttributeMutation.newSetAttributeMutation("expected_key", Double.NaN);
    }

    @Test(expected = NumberFormatException.class)
    public void testInfiniteFloatAttributes() throws NumberFormatException {
        AttributeMutation.newSetAttributeMutation("expected_key", Float.POSITIVE_INFINITY);
    }

    @Test(expected = NumberFormatException.class)
    public void testNegativeInfiniteFloatAttributes() throws NumberFormatException {
        AttributeMutation.newSetAttributeMutation("expected_key", Float.NEGATIVE_INFINITY);
    }

    @Test(expected = NumberFormatException.class)
    public void testInfiniteDoubleAttributes() throws NumberFormatException {
        AttributeMutation.newSetAttributeMutation("expected_key", Double.POSITIVE_INFINITY);
    }

    @Test(expected = NumberFormatException.class)
    public void testNegativeInfiniteDoubleAttributes() throws NumberFormatException {
        AttributeMutation.newSetAttributeMutation("expected_key", Double.NEGATIVE_INFINITY);
    }

    @NonNull
    static String toString(@NonNull List<AttributeMutation> mutations) {
        List<JsonValue> jsonValues = new ArrayList<>();

        for (AttributeMutation mutation : mutations) {
            jsonValues.add(mutation.toJsonValue());
        }

        JsonList result = new JsonList(jsonValues);

        return result.toString() ;
    }
}
