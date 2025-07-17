package com.urbanairship.javascript

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptEnvironmentTest {

    @Test
    fun testStringGetter() {
        val environment = JavaScriptEnvironment.newBuilder()
            .addGetter("cool", "neat")
            .build()

        val javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext())
        val expected = "_UAirship.cool = function(){return \"neat\";};"
        Assert.assertTrue(javaScript.contains(expected))
    }

    @Test
    fun testNumberGetter() {
        val environment = JavaScriptEnvironment.newBuilder()
            .addGetter("rad", 100)
            .build()

        val javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext())
        val expected = "_UAirship.rad = function(){return 100;};"
        Assert.assertTrue(javaScript.contains(expected))
    }

    @Test
    fun testJsonMapGetter() {
        val map = jsonMapOf("cool" to "story")

        val environment = JavaScriptEnvironment.newBuilder()
            .addGetter("jsonMap", map)
            .build()

        val javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext())
        val expected = "_UAirship.jsonMap = function(){return {\"cool\":\"story\"};};"
        Assert.assertTrue(javaScript.contains(expected))
    }

    @Test
    fun testJsonArrayGetter() {
        val value = JsonValue.wrapOpt(listOf("foo", "bar"))

        val environment = JavaScriptEnvironment.newBuilder()
            .addGetter("jsonArray", value)
            .build()

        val javaScript = environment.getJavaScript(ApplicationProvider.getApplicationContext())
        val expected = "_UAirship.jsonArray = function(){return [\"foo\",\"bar\"];};"
        Assert.assertTrue(javaScript.contains(expected))
    }
}
