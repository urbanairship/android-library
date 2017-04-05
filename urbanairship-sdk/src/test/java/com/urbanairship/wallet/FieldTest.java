/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.wallet;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FieldTest extends BaseTestCase {

    @Test
    public void testFullField() throws Exception {
        String name = "name";
        String label = "label";
        Integer value = 5;

        Field field = Field.newBuilder()
                .setLabel(label)
                .setName(name)
                .setValue(value)
                .build();

        JsonValue expectedJsonValue = JsonMap.newBuilder()
                .put(label, label)
                .put("value", value)
                .build()
                .toJsonValue();

        assertEquals(name, field.getName());
        assertEquals(expectedJsonValue, field.toJsonValue());
    }

    @Test
    public void testFieldVariations() {
        Field.newBuilder()
                .setName("name")
                .setValue(5)
                .build();

        Field.newBuilder()
               .setName("name")
               .setLabel("label")
               .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testFieldOnlyLabel() {
        Field.newBuilder()
              .setLabel("label")
              .build();
    }
}
