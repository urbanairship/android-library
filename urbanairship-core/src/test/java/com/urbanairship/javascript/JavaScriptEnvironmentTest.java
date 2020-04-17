package com.urbanairship.javascript;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.Arrays;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertTrue;

public class JavaScriptEnvironmentTest extends BaseTestCase {

    @Test
    public void testStringGetter() {
        JavaScriptEnvironment environment = JavaScriptEnvironment.newBuilder()
                             .addGetter("cool", "neat")
                             .build();

        String javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext());
        String expected = "_UAirship.cool = function(){return \"neat\";};";
        assertTrue(javaScript.contains(expected));
    }

    @Test
    public void testNumberGetter() {
        JavaScriptEnvironment environment = JavaScriptEnvironment.newBuilder()
                                                                 .addGetter("rad", 100)
                                                                 .build();

        String javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext());
        String expected = "_UAirship.rad = function(){return 100;};";
        assertTrue(javaScript.contains(expected));
    }

    @Test
    public void testJsonMapGetter() {
        JsonMap map = JsonMap.newBuilder()
                .put("cool", "story")
                .build();

        JavaScriptEnvironment environment = JavaScriptEnvironment.newBuilder()
                                                                 .addGetter("jsonMap", map)
                                                                 .build();

        String javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext());
        String expected = "_UAirship.jsonMap = function(){return {\"cool\":\"story\"};};";
        assertTrue(javaScript.contains(expected));
    }

    @Test
    public void testJsonArrayGetter() {
        JsonValue value = JsonValue.wrapOpt(Arrays.asList("foo", "bar"));

        JavaScriptEnvironment environment = JavaScriptEnvironment.newBuilder()
                                                                 .addGetter("jsonArray", value)
                                                                 .build();

        String javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext());
        String expected = "_UAirship.jsonArray = function(){return [\"foo\",\"bar\"];};";
        assertTrue(javaScript.contains(expected));
    }

}
