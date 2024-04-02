package com.urbanairship.automation.rewrite

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.automation.rewrite.deferred.DeferredAutomationData
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.content.Custom
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationScheduleTests {

    @Test
    public fun testParseActions() {
        val json = """
            {
               "id": "test_schedule",
               "triggers": [
                   {
                       "type": "custom_event_count",
                       "goal": 1,
                       "id": "json-id"
                   }
               ],
               "group": "test_group",
               "priority": 2,
               "limit": 5,
               "start": "2023-12-20T00:00:00Z",
               "end": "2023-12-21T00:00:00Z",
               "audience": {},
               "delay": {},
               "interval": 3600,
               "type": "actions",
               "actions": {
                   "foo": "bar"
               },
               "bypass_holdout_groups": true,
               "edit_grace_period": 7,
               "metadata": {},
               "frequency_constraint_ids": ["constraint1", "constraint2"],
               "message_type": "test_type",
               "last_updated": "2023-12-20T12:30:00Z",
               "created": "2023-12-20T12:00:00Z"
           }
        """.trimIndent()

        val expected = AutomationSchedule(
            identifier = "test_schedule",
            data = AutomationSchedule.ScheduleData.Actions(jsonMapOf("foo" to "bar").toJsonValue()),
            triggers = listOf(
                AutomationTrigger.Event(
                    EventAutomationTrigger(
                        id = "json-id",
                        type = EventAutomationTriggerType.CUSTOM_EVENT_COUNT,
                        goal = 1.0,
                        predicate = null
                    )
                )
            ),
            created = 1703073600000U,
            group = "test_group",
            priority = 2,
            limit = 5U,
            startDate = 1703030400000U,
            endDate = 1703116800000U,
            audience = AutomationAudience(AudienceSelector.newBuilder().build()),
            delay = AutomationDelay(),
            interval = 3600U,
            bypassHoldoutGroups = true,
            editGracePeriodDays = 7U,
            metadata = jsonMapOf().toJsonValue(),
            frequencyConstraintIDs = listOf("constraint1", "constraint2"),
            messageType = "test_type"
        )

        verify(json, expected)
    }

    @Test
    public fun testParseDeferred() {
        val json = """
            {
               "id": "test_schedule",
               "triggers": [
                   {
                       "type": "custom_event_count",
                       "goal": 1,
                       "id": "json-id"
                   }
               ],
               "group": "test_group",
               "priority": 2,
               "limit": 5,
               "start": "2023-12-20T00:00:00Z",
               "end": "2023-12-21T00:00:00Z",
               "audience": {
                 "new_user": true,
                 "miss_behavior": "cancel"
               },
               "delay": {},
               "interval": 3600,
               "type": "deferred",
               "deferred": {
                   "url": "https://some.url",
                   "retry_on_timeout": true,
                   "type": "in_app_message"
               },
               "bypass_holdout_groups": true,
               "edit_grace_period": 7,
               "metadata": {},
               "frequency_constraint_ids": ["constraint1", "constraint2"],
               "message_type": "test_type",
               "last_updated": "2023-12-20T12:30:00Z",
               "created": "2023-12-20T12:00:00Z"
           }
        """.trimIndent()

        val expected = AutomationSchedule(
            identifier = "test_schedule",
            data = AutomationSchedule.ScheduleData.Deferred(
                DeferredAutomationData(
                    url = Uri.parse("https://some.url"),
                    retryOnTimeOut = true,
                    type = DeferredAutomationData.DeferredType.IN_APP_MESSAGE)),
            triggers = listOf(
                AutomationTrigger.Event(
                    EventAutomationTrigger(
                        id = "json-id",
                        type = EventAutomationTriggerType.CUSTOM_EVENT_COUNT,
                        goal = 1.0,
                        predicate = null
                    )
                )
            ),
            created = 1703073600000U,
            group = "test_group",
            priority = 2,
            limit = 5U,
            startDate = 1703030400000U,
            endDate = 1703116800000U,
            audience = AutomationAudience(
                audienceSelector = AudienceSelector
                    .newBuilder()
                    .setNewUser(true)
                    .setMissBehavior(AudienceSelector.MissBehavior.CANCEL)
                    .build(),
                missBehavior = AutomationAudience.MissBehavior.CANCEL),
            delay = AutomationDelay(),
            interval = 3600U,
            bypassHoldoutGroups = true,
            editGracePeriodDays = 7U,
            metadata = jsonMapOf().toJsonValue(),
            frequencyConstraintIDs = listOf("constraint1", "constraint2"),
            messageType = "test_type"
        )

        verify(json, expected)
    }

    @Test
    public fun testParseInAppMessage() {
        val json = """
            {
               "id": "test_schedule",
               "triggers": [
                   {
                       "type": "custom_event_count",
                       "goal": 1,
                       "id": "json-id"
                   }
               ],
               "group": "test_group",
               "priority": 2,
               "limit": 5,
               "start": "2023-12-20T00:00:00Z",
               "end": "2023-12-21T00:00:00Z",
               "audience": {},
               "delay": {},
               "interval": 3600,
               "type": "in_app_message",
               "message": {
                   "source": "app-defined",
                   "display" : {
                       "cool": "story"
                   },
                   "display_type" : "custom",
                   "name" : "woot"
               },
               "bypass_holdout_groups": true,
               "edit_grace_period": 7,
               "metadata": {},
               "frequency_constraint_ids": ["constraint1", "constraint2"],
               "message_type": "test_type",
               "last_updated": "2023-12-20T12:30:00Z",
               "created": "2023-12-20T12:00:00Z"
           }
        """.trimIndent()

        val message = InAppMessage(
            name = "woot",
            displayContent = InAppMessageDisplayContent.CustomContent(
                Custom(jsonMapOf("cool" to "story").toJsonValue())
            ),
            source = InAppMessage.InAppMessageSource.APP_DEFINED
        )

        val expected = AutomationSchedule(
            identifier = "test_schedule",
            data = AutomationSchedule.ScheduleData.InAppMessageData(message),
            triggers = listOf(
                AutomationTrigger.Event(
                    EventAutomationTrigger(
                        id = "json-id",
                        type = EventAutomationTriggerType.CUSTOM_EVENT_COUNT,
                        goal = 1.0,
                        predicate = null
                    )
                )
            ),
            created = 1703073600000U,
            group = "test_group",
            priority = 2,
            limit = 5U,
            startDate = 1703030400000U,
            endDate = 1703116800000U,
            audience = AutomationAudience(AudienceSelector.newBuilder().build()),
            delay = AutomationDelay(),
            interval = 3600U,
            bypassHoldoutGroups = true,
            editGracePeriodDays = 7U,
            metadata = jsonMapOf().toJsonValue(),
            frequencyConstraintIDs = listOf("constraint1", "constraint2"),
            messageType = "test_type"
        )

        verify(json, expected)
    }

    private fun verify(json: String, expected: AutomationSchedule) {
        val fromJson = AutomationSchedule.fromJson(JsonValue.parseString(json))
        assertEquals(fromJson, expected)

        val roundTrip = AutomationSchedule.fromJson(fromJson.toJsonValue())
        assertEquals(roundTrip, fromJson)
    }
}
