/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import androidx.arch.core.util.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JsonDataStoreQueueTest extends BaseTestCase {

    JsonDataStoreQueue<JsonValue> queue;

    @Before
    public void setup() {
        queue = new JsonDataStoreQueue<>(TestApplication.getApplication().preferenceDataStore, "some-key", new Function<JsonValue, JsonSerializable>() {
            @Override
            public JsonSerializable apply(JsonValue input) {
                return input;
            }
        }, new Function<JsonValue, JsonValue>() {
            @Override
            public JsonValue apply(JsonValue input) {
                return input;
            }
        });
    }

    @Test
    public void testRemoveAll() {
        queue.add(JsonValue.wrapOpt("neat"));
        queue.add(JsonValue.wrapOpt("rad"));
        assertEquals(2, queue.getList().size());

        queue.removeAll();
        assertTrue(queue.getList().isEmpty());
        assertNull(queue.peek());
        assertNull(queue.pop());
    }

    @Test
    public void testAddAll() {
        queue.addAll(Arrays.asList(JsonValue.wrapOpt("neat"), JsonValue.wrapOpt("rad")));
        assertEquals(2, queue.getList().size());
        assertEquals("neat", queue.pop().getString());
        assertEquals("rad", queue.pop().getString());
        assertNull(queue.pop());
    }

    @Test
    public void testAdd() {
        queue.add(JsonValue.wrapOpt("neat"));
        queue.add(JsonValue.wrapOpt("rad"));
        assertEquals(2, queue.getList().size());
        assertEquals("neat", queue.pop().getString());
        assertEquals("rad", queue.pop().getString());
        assertNull(queue.pop());
    }

    @Test
    public void testPop() {
        queue.add(JsonValue.wrapOpt("neat"));
        queue.add(JsonValue.wrapOpt("rad"));
        assertEquals(2, queue.getList().size());
        assertEquals("neat", queue.pop().getString());
        assertEquals(1, queue.getList().size());
        assertEquals("rad", queue.pop().getString());
        assertTrue(queue.getList().isEmpty());
    }

    @Test
    public void testPeek() {
        queue.add(JsonValue.wrapOpt("neat"));
        queue.add(JsonValue.wrapOpt("rad"));
        assertEquals(2, queue.getList().size());
        assertEquals("neat", queue.peek().getString());
        assertEquals(2, queue.getList().size());
    }
    @Test
    public void testApply() {
        queue.add(JsonValue.wrapOpt("neat"));
        queue.add(JsonValue.wrapOpt("rad"));

        queue.apply(new Function<List<JsonValue>, List<JsonValue>>() {
            @Override
            public List<JsonValue> apply(List<JsonValue> input) {
                return Arrays.asList(JsonValue.wrapOpt("what?"));
            }
        });

        assertEquals(1, queue.getList().size());
        assertEquals("what?", queue.peek().getString());
    }

}
