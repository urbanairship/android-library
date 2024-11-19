/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.reporting.FormData.Score
import com.urbanairship.android.layout.reporting.FormData.TextInput
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class FormDataTest {

    @Test
    @Throws(JsonException::class)
    public fun testSerialization() {
        assertEquals(JsonValue.parseString(expectedFormJson), form.toJsonValue())
    }

    @Test
    public fun testEqualsAndHashCode() {
        val radioInputControllerDataCopy = radioInputControllerData.copy()
        val checkboxControllerDataCopy = checkboxControllerData.copy()
        val textInputDataCopy = textInputData.copy()
        val toggleDataCopy = toggleData.copy()
        val scoreDataCopy = scoreData.copy()
        val npsFormDataCopy = npsFormData.copy()
        val formDataCopy = formData.copy()
        val formCopy = form.copy()

        assertNotSame(radioInputControllerData, radioInputControllerDataCopy)
        assertEquals(radioInputControllerData, radioInputControllerDataCopy)
        assertEquals(radioInputControllerData.hashCode(), radioInputControllerDataCopy.hashCode())

        assertNotSame(checkboxControllerData, checkboxControllerDataCopy)
        assertEquals(checkboxControllerData, checkboxControllerDataCopy)
        assertEquals(checkboxControllerData.hashCode(), checkboxControllerDataCopy.hashCode())

        assertNotSame(textInputData, textInputDataCopy)
        assertEquals(textInputData, textInputDataCopy)
        assertEquals(textInputData.hashCode(), textInputDataCopy.hashCode())

        assertNotSame(toggleData, toggleDataCopy)
        assertEquals(toggleData, toggleDataCopy)
        assertEquals(toggleData.hashCode(), toggleDataCopy.hashCode())

        assertNotSame(scoreData, scoreDataCopy)
        assertEquals(scoreData, scoreDataCopy)
        assertEquals(scoreData.hashCode(), scoreDataCopy.hashCode())

        assertNotSame(npsFormData, npsFormDataCopy)
        assertEquals(npsFormData, npsFormDataCopy)
        assertEquals(npsFormData.hashCode(), npsFormDataCopy.hashCode())

        assertNotSame(formData, formDataCopy)
        assertEquals(formData, formDataCopy)
        assertEquals(formData.hashCode(), formDataCopy.hashCode())

        assertNotSame(form, formCopy)
        assertEquals(form, formCopy)
        assertEquals(form.hashCode(), formCopy.hashCode())
    }

    public companion object {
        private val radioInputControllerData: FormData.RadioInputController =
            FormData.RadioInputController(
                identifier = "single choice",
                value = JsonValue.wrap("single choice value"),
                isValid = true
            )

        private val checkboxControllerData: FormData.CheckboxController =
            FormData.CheckboxController(
                identifier = "multiple choice",
                value = setOf(JsonValue.wrap("multiple choice value")),
                isValid = true
            )

        private val textInputData: TextInput =
            TextInput(textInput = FormInputType.TEXT, identifier = "text input", value = "text input value", isValid = true)

        private val emailInputData: TextInput =
            TextInput(textInput = FormInputType.EMAIL, identifier = "email input", value = "text@value", isValid = true)

        private val toggleData: FormData.Toggle =
            FormData.Toggle(identifier = "toggle input", value = true, isValid = true)

        private val scoreData: Score =
            Score(identifier = "score", value = 5, isValid = true)

        private val npsFormData: FormData.Nps =
            FormData.Nps(
                identifier = "child nps",
                responseType = "child nps response type",
                scoreId = "child score",
                children = setOf(
                    Score(identifier = "child score", value = 7, isValid = true)
                )
            )

        private val formData: FormData.Form =
            FormData.Form(
                identifier = "child form",
                responseType = "child form response type",
                children = setOf(
                    TextInput(textInput = FormInputType.TEXT, identifier = "child text", value = "child text input", isValid = true)
                )
            )

        private val children: Set<FormData<*>> = setOf(
            radioInputControllerData,
            checkboxControllerData,
            textInputData,
            toggleData,
            scoreData,
            npsFormData,
            formData,
            emailInputData
        )

        private val form: FormData.Form = FormData.Form(
            identifier = "parent form",
            responseType = "parent form response type",
            children = children
        )

        @Language("json")
        private val expectedFormJson: String = """{
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
                 "email input":{
                    "type":"email_input",
                    "value":"text@value"
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
    }
}
