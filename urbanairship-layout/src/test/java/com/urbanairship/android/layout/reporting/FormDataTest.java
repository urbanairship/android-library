/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class FormDataTest {

    @Test
    public void testData() throws JsonException {
        List<FormData<?>> children = new ArrayList<>();
        children.add(new FormData.RadioInputController("single choice", JsonValue.wrap("single choice valuee")));
        children.add(new FormData.CheckboxController("multiple choice", Collections.singleton(JsonValue.wrap("multiple choice value"))));
        children.add(new FormData.TextInput("text input", "text input value"));
        children.add(new FormData.Toggle("toggle input", true));
        children.add(new FormData.Score("score", 5));

        FormData.Score childScore = new FormData.Score("child score", 7);
        children.add(new FormData.Nps("child nps", "child nps response type", "child score", Collections.singleton(childScore)));

        FormData.TextInput childText = new FormData.TextInput("child text", "child text input");
        children.add(new FormData.Form("child form", "child form response type", Collections.singleton(childText)));

        FormData.Form form = new FormData.Form("parent form", "parent form response type", children);

        String expected = "{\n" +
                "   \"parent form\":{\n" +
                "      \"response_type\":\"parent form response type\",\n" +
                "      \"type\":\"form\",\n" +
                "      \"children\":{\n" +
                "         \"score\":{\n" +
                "            \"type\":\"score\",\n" +
                "            \"value\":5\n" +
                "         },\n" +
                "         \"child nps\":{\n" +
                "            \"response_type\":\"child nps response type\",\n" +
                "            \"type\":\"nps\",\n" +
                "            \"children\":{\n" +
                "               \"child score\":{\n" +
                "                  \"type\":\"score\",\n" +
                "                  \"value\":7\n" +
                "               }\n" +
                "            },\n" +
                "            \"score_id\":\"child score\"\n" +
                "         },\n" +
                "         \"toggle input\":{\n" +
                "            \"type\":\"toggle\",\n" +
                "            \"value\":true\n" +
                "         },\n" +
                "         \"multiple choice\":{\n" +
                "            \"type\":\"multiple_choice\",\n" +
                "            \"value\":[\n" +
                "               \"multiple choice value\"\n" +
                "            ]\n" +
                "         },\n" +
                "         \"text input\":{\n" +
                "            \"type\":\"text_input\",\n" +
                "            \"value\":\"text input value\"\n" +
                "         },\n" +
                "         \"single choice\":{\n" +
                "            \"type\":\"single_choice\",\n" +
                "            \"value\":\"single choice valuee\"\n" +
                "         },\n" +
                "         \"child form\":{\n" +
                "            \"response_type\":\"child form response type\",\n" +
                "            \"type\":\"form\",\n" +
                "            \"children\":{\n" +
                "               \"child text\":{\n" +
                "                  \"type\":\"text_input\",\n" +
                "                  \"value\":\"child text input\"\n" +
                "               }\n" +
                "            }\n" +
                "         }\n" +
                "      }\n" +
                "   }\n" +
                "}";

        assertEquals(JsonValue.parseString(expected), form.toJsonValue());
    }
}
