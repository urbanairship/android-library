package com.urbanairship.iam.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppEventContextTest {
    private val scheduleID = UUID.randomUUID().toString()
    private val campaigns = jsonMapOf("campaign1" to "data1", "campaign2" to "data2").toJsonValue()

    @Test
    public fun testJSON() {
        val context = InAppEventContext(
            pager = InAppEventContext.Pager(
                identifier = "pager id",
                pageIdentifier = "page id",
                pageIndex = 1,
                completed = true,
                count = 2
            ),
            button = InAppEventContext.Button(
                identifier = "button id"
            ),
            form = InAppEventContext.Form(
                identifier = "form id",
                submitted = true,
                type = "form type"
            ),
            reportingContext = JsonValue.wrap("reporting context"),
            experimentReportingData = listOf(
                jsonMapOf("experiment 1" to "result"),
                jsonMapOf("experiment 2" to "result")
            )
        )

        val json = """
            {
              "reporting_context":"reporting context",
              "form":{
                 "type":"form type",
                 "identifier":"form id",
                 "submitted":true
              },
              "button":{
                 "identifier":"button id"
              },
              "pager":{
                 "page_identifier":"page id",
                 "page_index":1,
                 "identifier":"pager id",
                 "completed":true,
                 "count":2
              },
              "experiments":[
                 {"experiment 1": "result"},
                 {"experiment 2": "result"}
              ]
           }
        """.trimIndent()

        assertEquals(JsonValue.parseString(json), context.toJsonValue())
    }

    @Test
    public fun testMake() {
        val experimentResult = ExperimentResult(
            channelId = "some channel",
            contactId = "some contact",
            isMatching = true,
            allEvaluatedExperimentsMetadata = listOf(jsonMapOf("some" to "reporting")),
        )

        val reportingMetadata = JsonValue.wrap("reporting info")
        val layoutData = LayoutData(
            FormInfo("form-identifier", "form-type", "form-response-type", true),
            PagerData("pager-id", 1, "page-id", 2, false),
            "button-identifier"
        )
        val displayContext = InAppEventContext.Display(
            triggerSessionId = UUID.randomUUID().toString(),
            isFirstDisplay = true,
            isFirstDisplayTriggerSessionId = false
        )

        val context = InAppEventContext.makeContext(
            reportingContext = reportingMetadata,
            experimentResult = experimentResult,
            layoutContext = layoutData,
            displayContext = displayContext
        )

        val expected = InAppEventContext(
            pager = InAppEventContext.Pager(
                identifier = "pager-id",
                pageIndex = 1,
                pageIdentifier = "page-id",
                count = 2,
                completed = false
            ),
            form = InAppEventContext.Form(
                identifier = "form-identifier",
                submitted = true,
                type = "form-type",
                responseType = "form-response-type",
            ),
            button = InAppEventContext.Button("button-identifier"),
            reportingContext = reportingMetadata,
            experimentReportingData = experimentResult.allEvaluatedExperimentsMetadata,
            display = displayContext
        )

        assertEquals(expected, context)
    }

    @Test
    public fun testMakeEmpty() {
        val context = InAppEventContext.makeContext(null, null, null, null)
        assertNull(context)
    }
}
