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
    public void testSetAttribute() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\"}]";

        assertEquals(expected, toString(mutations));
    }

    @Test
    public void testRemoveAttribute() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key"));

        String expected = "[{\"action\":\"remove\",\"key\":\"expected_key\"}]";

        assertEquals(expected, toString(mutations));
    }

    @Test
    public void testSetAndRemove() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));
        mutations.add(AttributeMutation.newRemoveAttributeMutation("expected_key"));

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\"},{\"action\":\"remove\",\"key\":\"expected_key\"}]";

        assertEquals(expected, toString(mutations));
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
