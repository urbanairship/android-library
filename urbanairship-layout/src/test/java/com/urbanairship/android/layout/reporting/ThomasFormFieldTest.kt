/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.reporting.ThomasFormField.Score
import com.urbanairship.android.layout.reporting.ThomasFormField.TextInput
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasFormFieldTest {

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
        val npsFormDataCopy = npsThomasFormField.copy()
        val formDataCopy = thomasFormField.copy()
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

        assertNotSame(npsThomasFormField, npsFormDataCopy)
        assertEquals(npsThomasFormField, npsFormDataCopy)
        assertEquals(npsThomasFormField.hashCode(), npsFormDataCopy.hashCode())

        assertNotSame(thomasFormField, formDataCopy)
        assertEquals(thomasFormField, formDataCopy)
        assertEquals(thomasFormField.hashCode(), formDataCopy.hashCode())

        assertNotSame(form, formCopy)
        assertEquals(form, formCopy)
        assertEquals(form.hashCode(), formCopy.hashCode())
    }

    @Test
    public fun justFileTypeTest() {
        val value = "test value"

        assertEquals(ThomasFormField.FieldType.just(
            value = value,
            validator = { false }
        ), ThomasFormField.FieldType.Instant<String>(null))

        assertEquals(ThomasFormField.FieldType.just(
            value = value,
            validator = { true }
        ), ThomasFormField.FieldType.Instant(ThomasFormField.Result(value)))

        assertEquals(ThomasFormField.FieldType.just(
            value = value
        ), ThomasFormField.FieldType.Instant(ThomasFormField.Result(value)))
    }

    public companion object {
        private val radioInputControllerData: ThomasFormField.RadioInputController =
            ThomasFormField.RadioInputController(
                identifier = "single choice",
                originalValue = JsonValue.wrap("single choice value"),
                fieldType = ThomasFormField.FieldType.just(
                    value = JsonValue.wrap("single choice value"),
                    validator = { true }
                )

            )

        private val checkboxControllerData: ThomasFormField.CheckboxController =
            ThomasFormField.CheckboxController(
                identifier = "multiple choice",
                originalValue = setOf(JsonValue.wrap("multiple choice value")),
                fieldType = ThomasFormField.FieldType.just(
                    value = setOf(JsonValue.wrap("multiple choice value")),
                    validator = { true }
                )
            )

        private val textInputData: TextInput =
            TextInput(
                textInput = FormInputType.TEXT,
                identifier = "text input",
                originalValue = "text input value",
                fieldType = ThomasFormField.FieldType.just("text input value")
            )

        private val emailInputData: TextInput =
            TextInput(
                textInput = FormInputType.EMAIL,
                identifier = "email input",
                originalValue = "text@value",
                fieldType = ThomasFormField.FieldType.just("text@value")
            )

        private val toggleData: ThomasFormField.Toggle =
            ThomasFormField.Toggle(
                identifier = "toggle input",
                originalValue = true,
                fieldType = ThomasFormField.FieldType.just(true)
            )

        private val scoreData: Score =
            Score(
                identifier = "score",
                originalValue = 5,
                fieldType = ThomasFormField.FieldType.just(5)
            )

        private val npsThomasFormField: ThomasFormField.Nps
            get() {
                val children = setOf(
                    Score(
                        identifier = "child score",
                        originalValue = 7,
                        fieldType = ThomasFormField.FieldType.just(7)
                    )
                )

                return ThomasFormField.Nps(
                    identifier = "child nps",
                    responseType = "child nps response type",
                    scoreId = "child score",
                    children = children,
                    fieldType = ThomasFormField.FieldType.just(children)
                )
            }


        private val thomasFormField: ThomasFormField.Form
            get() {
                val children = setOf(
                    TextInput(
                        textInput = FormInputType.TEXT,
                        identifier = "child text",
                        originalValue = "child text input",
                        fieldType = ThomasFormField.FieldType.just("child text input")
                    )
                )

                return ThomasFormField.Form(
                    identifier = "child form",
                    responseType = "child form response type",
                    children = children,
                    fieldType = ThomasFormField.FieldType.just(children)
                )
            }


        private val children: Set<ThomasFormField<*>> = setOf(
            radioInputControllerData,
            checkboxControllerData,
            textInputData,
            toggleData,
            scoreData,
            npsThomasFormField,
            thomasFormField,
            emailInputData
        )

        private val form: ThomasFormField.Form = ThomasFormField.Form(
            identifier = "parent form",
            responseType = "parent form response type",
            children = children,
            fieldType = ThomasFormField.FieldType.just(children)
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
