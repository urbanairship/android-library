package com.urbanairship.iam.analytics.events

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppFormResultEventTest {
    @Test
    public fun testEvent() {
        val children = setOf(
            ThomasFormField.TextInput(
                textInput = FormInputType.TEXT,
                identifier = "text_input_id",
                originalValue = "text_input_value",
                fieldType = ThomasFormField.FieldType.just("text_input_value")
            ),
            ThomasFormField.TextInput(
                textInput = FormInputType.SMS,
                identifier = "sms_input_id",
                originalValue = "sms_input_value",
                fieldType = ThomasFormField.FieldType.just( "sms_input_value")
            )
        )

        val formData = ThomasFormField.Form(
            identifier = "form_id",
            responseType = "form_response_type",
            children = children,
            fieldType = ThomasFormField.FieldType.just(children)
        )

        val event = InAppFormResultEvent(
            data = ReportingEvent.FormResultData(
                forms = formData
            )
        )
        val expected = """
            {
              "forms": {
                "form_id": {
                  "children": {
                    "sms_input_id": {
                      "type": "sms_input",
                      "value": "sms_input_value"
                    },
                    "text_input_id": {
                      "type": "text_input",
                      "value": "text_input_value"
                    }
                  },
                  "response_type": "form_response_type",
                  "type": "form"
                }
              }
            }
        """.trimIndent()

        assertEquals("in_app_form_result", event.eventType.reportingName)
        assertEquals(JsonValue.parseString(expected), event.data.toJsonValue())
    }
}
