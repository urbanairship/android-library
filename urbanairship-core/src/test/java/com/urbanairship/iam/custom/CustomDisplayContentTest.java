/* Copyright Airship and Contributors */

package com.urbanairship.iam.custom;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link CustomDisplayContent} tests.
 */
public class CustomDisplayContentTest {

    @Test
    public void testJson() throws JsonException {
        CustomDisplayContent content = new CustomDisplayContent(JsonValue.wrapOpt("some value"));

        CustomDisplayContent fromJson = CustomDisplayContent.fromJson(content.toJsonValue());
        assertEquals(content, fromJson);
        assertEquals(content.hashCode(), fromJson.hashCode());
    }

}