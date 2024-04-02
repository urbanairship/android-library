package com.urbanairship.automation.rewrite.inappmessage.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppEventMessageIDTest {
    private val scheduleID = UUID.randomUUID().toString()
    private val campaigns = jsonMapOf("campaign1" to "data1", "campaign2" to "data2").toJsonValue()

    @Test
    public fun testLegacy() {
        val messageID = InAppEventMessageID.Legacy(scheduleID)
        val json = """ "$scheduleID" """.trimIndent()
        assertEquals(JsonValue.parseString(json), messageID.toJsonValue())
    }

    @Test
    public fun testAppDefined() {
        val messageID = InAppEventMessageID.AppDefined(scheduleID)
        val json = """
            {
              "message_id": "$scheduleID"
            }
        """.trimIndent()
        assertEquals(JsonValue.parseString(json), messageID.toJsonValue())
    }

    @Test
    public fun testAirship() {
        val messageID = InAppEventMessageID.AirshipID(scheduleID, campaigns)
        val json = """
            {
              "message_id":"$scheduleID",
              "campaigns":$campaigns
            }
        """.trimIndent()

        assertEquals(JsonValue.parseString(json), messageID.toJsonValue())
    }

    @Test
    public fun testAirshipNoCampaigns() {
        val messageID = InAppEventMessageID.AirshipID(scheduleID, campaigns = null)
        val json = """
            {
              "message_id":"$scheduleID"
            }
        """.trimIndent()

        assertEquals(JsonValue.parseString(json), messageID.toJsonValue())
    }
}
