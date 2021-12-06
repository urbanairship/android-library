package com.urbanairship.iam.layout;

import com.urbanairship.iam.html.HtmlDisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * {@link AirshipLayoutDisplayContent} tests.
 */
@RunWith(AndroidJUnit4.class)
public class AirshipLayoutDisplayContentTest  {
    @Test
    public void testJson() throws JsonException {

        String payloadString = "{\n" +
                "    \"layout\": {\n" +
                "        \"version\": 1,\n" +
                "        \"presentation\": {\n" +
                "          \"type\": \"modal\",\n" +
                "          \"default_placement\": {\n" +
                "            \"size\": {\n" +
                "              \"width\": \"100%\",\n" +
                "              \"height\": \"100%\"\n" +
                "            },\n" +
                "            \"position\": { \n" +
                "                \"horizontal\": \"center\",\n" +
                "                \"vertical\": \"center\" \n" +
                "            },\n" +
                "            \"shade_color\": {\n" +
                "              \"default\": { \n" +
                "                  \"type\": \"hex\", \n" +
                "                  \"hex\": \"#000000\", \n" +
                "                  \"alpha\": 0.2 }\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"view\": {\n" +
                "            \"type\": \"empty_view\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        JsonValue payload = JsonValue.parseString(payloadString);
        AirshipLayoutDisplayContent content = AirshipLayoutDisplayContent.fromJson(payload);
        assertEquals(content.toJsonValue(), payload);
    }

    @Test(expected = JsonException.class)
    public void testInvalidJson() throws JsonException {
        String payloadString = "{}";
        JsonValue payload = JsonValue.parseString(payloadString);
        AirshipLayoutDisplayContent content = AirshipLayoutDisplayContent.fromJson(payload);
    }
}
