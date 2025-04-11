/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.reporting

import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.reporting.ThomasFormField.Score
import com.urbanairship.android.layout.reporting.ThomasFormField.TextInput
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
    public fun testSerializationAsyncFields() {
        val children: Set<ThomasFormField<*>> = setOf(
            Score(
                identifier = "field-invalid",
                originalValue = 2,
                filedType = ThomasFormField.FiledType.just(0, { false })
            ),
            Score(
                identifier = "field-error",
                originalValue = 3,
                filedType = ThomasFormField.FiledType.Async(
                    makeFetcher(ThomasFormField.AsyncValueFetcher.PendingResult.Error())
                )
            ),
            Score(
                identifier = "field-pending",
                originalValue = 4,
                filedType = ThomasFormField.FiledType.Async(
                    makeFetcher(null)
                )
            ),
            Score(
                identifier = "field-valid",
                originalValue = 5,
                filedType = ThomasFormField.FiledType.Async(
                    makeFetcher(ThomasFormField.AsyncValueFetcher.PendingResult.Valid(
                        result = ThomasFormField.Result(6)
                    ))
                )
            )
        )

        val form = ThomasFormField.Form(
            identifier = "parent form",
            responseType = "parent form response type",
            children = children,
            filedType = ThomasFormField.FiledType.just(children)
        )

        val expectedJson = """
            {
              "parent form": {
                "children": {
                  "field-invalid": {
                    "status": {
                      "type": "invalid"
                    },
                    "type": "score",
                    "value": 2
                  },
                  "field-error": {
                    "status": {
                      "type": "error"
                    },
                    "type": "score",
                    "value": 3
                  },
                  "field-pending": {
                    "status": {
                      "type": "pending"
                    },
                    "type": "score",
                    "value": 4
                  },
                  "field-valid": {
                    "status": {
                      "type": "valid",
                      "result":{
                          "value":6,
                          "type":"score"
                       }
                    },
                    "type": "score",
                    "value": 5
                  }
                },
                "response_type": "parent form response type",
                "type": "form"
              }
            }
        """.trimIndent()

        assertEquals(JsonValue.parseString(expectedJson), form.toJsonValue())
    }

    private fun <T> makeFetcher(
        result: ThomasFormField.AsyncValueFetcher.PendingResult<T>?
    ): ThomasFormField.AsyncValueFetcher<T> {
        return mockk {
            every { results } returns MutableStateFlow(result)
        }
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

        assertEquals(ThomasFormField.FiledType.just(
            value = value,
            validator = { false }
        ), ThomasFormField.FiledType.Instant<String>(null))

        assertEquals(ThomasFormField.FiledType.just(
            value = value,
            validator = { true }
        ), ThomasFormField.FiledType.Instant(ThomasFormField.Result(value)))

        assertEquals(ThomasFormField.FiledType.just(
            value = value
        ), ThomasFormField.FiledType.Instant(ThomasFormField.Result(value)))
    }

    public companion object {
        private val radioInputControllerData: ThomasFormField.RadioInputController =
            ThomasFormField.RadioInputController(
                identifier = "single choice",
                originalValue = JsonValue.wrap("single choice value"),
                filedType = ThomasFormField.FiledType.just(
                    value = JsonValue.wrap("single choice value"),
                    validator = { true }
                )

            )

        private val checkboxControllerData: ThomasFormField.CheckboxController =
            ThomasFormField.CheckboxController(
                identifier = "multiple choice",
                originalValue = setOf(JsonValue.wrap("multiple choice value")),
                filedType = ThomasFormField.FiledType.just(
                    value = setOf(JsonValue.wrap("multiple choice value")),
                    validator = { true }
                )
            )

        private val textInputData: TextInput =
            TextInput(
                textInput = FormInputType.TEXT,
                identifier = "text input",
                originalValue = "text input value",
                filedType = ThomasFormField.FiledType.just("text input value")
            )

        private val emailInputData: TextInput =
            TextInput(
                textInput = FormInputType.EMAIL,
                identifier = "email input",
                originalValue = "text@value",
                filedType = ThomasFormField.FiledType.just("text@value")
            )

        private val toggleData: ThomasFormField.Toggle =
            ThomasFormField.Toggle(
                identifier = "toggle input",
                originalValue = true,
                filedType = ThomasFormField.FiledType.just(true)
            )

        private val scoreData: Score =
            Score(
                identifier = "score",
                originalValue = 5,
                filedType = ThomasFormField.FiledType.just(5)
            )

        private val npsThomasFormField: ThomasFormField.Nps
            get() {
                val children = setOf(
                    Score(
                        identifier = "child score",
                        originalValue = 7,
                        filedType = ThomasFormField.FiledType.just(7)
                    )
                )

                return ThomasFormField.Nps(
                    identifier = "child nps",
                    responseType = "child nps response type",
                    scoreId = "child score",
                    children = children,
                    filedType = ThomasFormField.FiledType.just(children)
                )
            }


        private val thomasFormField: ThomasFormField.Form
            get() {
                val children = setOf(
                    TextInput(
                        textInput = FormInputType.TEXT,
                        identifier = "child text",
                        originalValue = "child text input",
                        filedType = ThomasFormField.FiledType.just("child text input")
                    )
                )

                return ThomasFormField.Form(
                    identifier = "child form",
                    responseType = "child form response type",
                    children = children,
                    filedType = ThomasFormField.FiledType.just(children)
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
            filedType = ThomasFormField.FiledType.just(children)
        )

        @Language("json")
        private val expectedFormJson: String = """{
           "parent form":{
              "response_type":"parent form response type",
              "type":"form",
              "children":{
                 "score":{
                    "type":"score",
                    "value":5,
                    "status": {
                      "result": {
                        "type":"score",
                        "value":5
                      },
                      "type":"valid"
                    }
                 },
                 "child nps":{
                    "response_type":"child nps response type",
                    "type":"nps",
                    "children":{
                       "child score":{
                          "type":"score",
                          "value":7,
                          "status": {
                              "result": {
                                "type":"score",
                                "value":7
                              },
                              "type":"valid"
                          }
                       }
                    },
                    "score_id":"child score"
                 },
                 "toggle input":{
                    "type":"toggle",
                    "value":true,
                    "status": {
                      "result": {
                        "type":"toggle",
                        "value":true
                      },
                      "type":"valid"
                    }
                 },
                 "multiple choice":{
                    "type":"multiple_choice",
                    "value":[
                       "multiple choice value"
                    ],
                    "status": {
                      "result": {
                        "type":"multiple_choice",
                        "value":["multiple choice value"]
                      },
                      "type":"valid"
                    }
                 },
                 "text input":{
                    "type":"text_input",
                    "value":"text input value",
                    "status": {
                      "result": {
                        "type":"text_input",
                        "value":"text input value"
                      },
                      "type":"valid"
                    }
                 },
                 "email input":{
                    "type":"email_input",
                    "value":"text@value",
                    "status": {
                      "result": {
                        "type":"email_input",
                        "value":"text@value"
                      },
                      "type":"valid"
                    }
                 },
                 "single choice":{
                    "type":"single_choice",
                    "value":"single choice value",
                    "status": {
                      "result": {
                        "type":"single_choice",
                        "value":"single choice value"
                      },
                      "type":"valid"
                    }
                 },
                 "child form":{
                    "response_type":"child form response type",
                    "type":"form",
                    "children":{
                       "child text":{
                          "type":"text_input",
                          "value":"child text input",
                          "status": {
                              "result": {
                                "type":"text_input",
                                "value":"child text input"
                              },
                              "type":"valid"
                          }
                       }
                    }
                 }
              }
           }
        }""".trimIndent()
    }
}
