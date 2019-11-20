package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;

public class PendingAttributeMutationTest extends BaseTestCase {
    @Test
    public void testAttributeToPendingAttribute() {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", "expected_value"));

        List<PendingAttributeMutation> pendingMutations = PendingAttributeMutation.fromAttributeMutations(mutations, 0);

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expected, JsonValue.wrapOpt(pendingMutations).toString());
    }
}
