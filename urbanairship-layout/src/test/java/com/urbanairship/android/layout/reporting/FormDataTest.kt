/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.reporting.FormData.Score
import com.urbanairship.android.layout.reporting.FormData.TextInput
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.NonCancellable.children
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class FormDataTest {

    @Test
    @Throws(JsonException::class)
    public fun testData() {
        val children = listOf(
            FormData.RadioInputController(
                identifier = "single choice",
                value = JsonValue.wrap("single choice value"),
                isValid = true
            ),

            FormData.CheckboxController(
                identifier = "multiple choice",
                value = setOf(JsonValue.wrap("multiple choice value")),
                isValid = true
            ),

            TextInput(identifier = "text input", value = "text input value", isValid = true),

            FormData.Toggle(identifier = "toggle input", value = true, isValid = true),

            Score(identifier = "score", value = 5, isValid = true),

            FormData.Nps(
                identifier = "child nps",
                responseType = "child nps response type",
                scoreId = "child score",
                children = setOf(
                    Score(identifier = "child score", value = 7, isValid = true)
                )
            ),

            FormData.Form(
                identifier = "child form",
                responseType = "child form response type",
                children = setOf(
                    TextInput(identifier = "child text", value = "child text input", isValid = true)
                )
            )
        )

        val form = FormData.Form(
            identifier = "parent form",
            responseType = "parent form response type",
            children = children
        )

        @Language("json")
        val expected = """{
           "parent form":{
              "response_type":"parent form response type",
              "type":"form",
              "children":{
                 "score":{
                    "type":"score",
                    "value":5
                 },
                 "child nps":{
                    "response_type":"child nps response type",
                    "type":"nps",
                    "children":{
                       "child score":{
                          "type":"score",
                          "value":7
                       }
                    },
                    "score_id":"child score"
                 },
                 "toggle input":{
                    "type":"toggle",
                    "value":true
                 },
                 "multiple choice":{
                    "type":"multiple_choice",
                    "value":[
                       "multiple choice value"
                    ]
                 },
                 "text input":{
                    "type":"text_input",
                    "value":"text input value"
                 },
                 "single choice":{
                    "type":"single_choice",
                    "value":"single choice value"
                 },
                 "child form":{
                    "response_type":"child form response type",
                    "type":"form",
                    "children":{
                       "child text":{
                          "type":"text_input",
                          "value":"child text input"
                       }
                    }
                 }
              }
           }
        }""".trimIndent()

        Assert.assertEquals(JsonValue.parseString(expected), form.toJsonValue())
    }
}
