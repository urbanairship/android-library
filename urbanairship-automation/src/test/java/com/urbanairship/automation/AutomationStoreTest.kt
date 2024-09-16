package com.urbanairship.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.AutomationStore
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.fail
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AutomationStore.createInMemoryDatabase(context)

    @After
    public fun tearDown() {
        store.close()
    }

    @Test
    public fun testUpsertNewSchedules(): TestResult = runTest {
        val data = listOf(makeSchedule("foo"), makeSchedule("bar")).associateBy { it.schedule.identifier }

        val result = store.upsertSchedules(listOf("foo", "bar")) { identifier, existing ->
            assertNull(existing)
            data[identifier]!!
        }

        assertEquals(result, data.values.toList())
    }

    @Test
    public fun testUpsertMixedSchedules(): TestResult = runTest{
        val original = listOf(makeSchedule("foo"), makeSchedule("bar")).associateBy { it.schedule.identifier }

        var result = store.upsertSchedules(original.keys.toList()) { identifier, existing ->
            assertNull(existing)
            original[identifier]!!
        }

        assertEquals(result, original.values.toList())

        val updated = original.toMutableMap()
        updated["foo"] = makeSchedule("foo") // to not share the same instance
        updated["baz"] = makeSchedule("baz")
        updated["foo"]?.finished(1)

        result = store.upsertSchedules(updated.keys.toList().sorted()) { identifier, existing ->
            assertEquals(existing, original[identifier])
            updated[identifier]!!
        }

        assertEquals(result, updated.values.toList().sortedBy { it.schedule.identifier })
    }

    @Test
    public fun testUpdate(): TestResult = runTest {
        val originalFoo = makeSchedule("foo")
        store.upsertSchedules(listOf("foo")) { _, _ -> originalFoo }

        val triggerInfo = TriggeringInfo(
            context = DeferredTriggerContext("foo", 10.0, JsonValue.wrap("event")),
            date = 1
        )

        val preparedInfo = PreparedScheduleInfo(
            scheduleId = "full",
            productId = "some product",
            campaigns = JsonValue.wrap("campaigns"),
            contactId = "some contact",
            experimentResult = ExperimentResult(
                channelId = "some channel",
                contactId = "some contact",
                isMatching = true,
                allEvaluatedExperimentsMetadata = listOf(jsonMapOf("full" to "reporting"))
            ),
            triggerSessionId = "some trigger session id",
            additionalAudienceCheckResult = true,
            priority = 0
        )

        val date = 1L

        val result = store.updateSchedule("foo") { data ->
            data.paused(date)
            data.setExecutionCount(100)
            data.setTriggeringInfo(triggerInfo)
            data.setSchedule(data.schedule.copyWith(group = "bar"))
            data.setPreparedScheduleInfo(preparedInfo)
            return@updateSchedule data
        }

        fun verify(data: AutomationScheduleData?) {
            assertEquals(data?.executionCount, 100)
            assertEquals(data?.triggerInfo, triggerInfo)
            assertEquals(data?.schedule?.group, "bar")
            assertEquals(data?.preparedScheduleInfo, preparedInfo)
            assertEquals(data?.scheduleState, AutomationScheduleState.PAUSED)
            assertEquals(data?.scheduleStateChangeDate, date)
        }

        verify(result)
        verify(store.getSchedule("foo"))
    }

    @Test
    public fun testUpsertFullData(): TestResult = runTest {
        val schedule = makeSchedule("full")
        schedule.setTriggeringInfo(
            TriggeringInfo(
                context = DeferredTriggerContext("foo", 10.0, JsonValue.wrap("event")),
                date = 1
            )
        )

        schedule.setPreparedScheduleInfo(
            PreparedScheduleInfo(
                scheduleId = "full",
                productId = "some product",
                campaigns = JsonValue.wrap("campaigns"),
                contactId = "some contact",
                experimentResult = ExperimentResult(
                    channelId = "some channel",
                    contactId = "some contact",
                    isMatching = true,
                    allEvaluatedExperimentsMetadata = listOf(jsonMapOf("full" to "reporting"))
                ),
                triggerSessionId = "some trigger session id",
                additionalAudienceCheckResult = true
            )
        )

        val result = store.upsertSchedules(listOf("full")) { _, _ -> schedule}
        assertEquals(result.first(), schedule)
        assertEquals(schedule, store.getSchedule("full"))
    }

    @Test
    public fun testUpdateDoesNotExist(): TestResult = runTest {
        val result = store.updateSchedule("non-existing") { _ ->
            fail()
            throw IllegalArgumentException()
        }

        assertNull(result)
    }

    @Test
    public fun testGetSchedules(): TestResult = runTest {
        val original = listOf(makeSchedule("foo"), makeSchedule("bar")).associateBy { it.schedule.identifier }
        store.upsertSchedules(original.keys.toList()) { key, _ -> original[key]!! }

        assertEquals(original["foo"], store.getSchedule("foo"))
        assertEquals(original["bar"], store.getSchedule("bar"))

        assertNull(store.getSchedule("non-existing"))
    }

    @Test
    public fun testGetSchedulesByGroup(): TestResult = runTest {
        val original = listOf(
            makeSchedule("foo", "groupA"),
            makeSchedule("bar"),
            makeSchedule("baz", "groupA")
        ).associateBy { it.schedule.identifier }

        store.upsertSchedules(original.keys.toList()) { id, _ -> original[id]!! }

        val groupA = store.getSchedules("groupA").sortedBy { it.schedule.identifier }

        assertEquals(listOf(original["baz"], original["foo"]), groupA)
    }

    @Test
    public fun testDeleteIdentifiers(): TestResult = runTest {
        val original = listOf(
            makeSchedule("foo", "groupA"),
            makeSchedule("bar"),
            makeSchedule("baz", "groupA")
        ).associateBy { it.schedule.identifier }

        store.upsertSchedules(original.keys.toList()) { id, _ -> original[id]!! }
        store.deleteSchedules(listOf("foo", "non-existing"))
        val remaining = store.getSchedules().sortedBy { it.schedule.identifier }
        assertEquals(listOf(original["bar"], original["baz"]), remaining)
    }

    @Test
    public fun testDeleteGroup(): TestResult = runTest {
        val original = listOf(
            makeSchedule("foo", "groupA"),
            makeSchedule("bar", "groupB"),
            makeSchedule("baz", "groupA")
        ).associateBy { it.schedule.identifier }

        store.upsertSchedules(original.keys.toList()) { id, _ -> original[id]!! }

        assertEquals(3, store.getSchedules().size)
        store.deleteSchedules("groupA")

        assertEquals(listOf(original["bar"]), store.getSchedules())
    }

    @Test
    public fun testAssociatedData(): TestResult = runTest {
        val associatedData = JsonValue.wrap("some data")
        val schedule = makeSchedule("bar")
        schedule.associatedData = associatedData

        store.upsertSchedules(listOf("bar")) { _, _ ->
            schedule
        }

        assertEquals(associatedData, store.getSchedule("bar")?.associatedData)
    }

    @Test
    public fun testAssociatedDataNull(): TestResult = runTest {
        val schedule = makeSchedule("bar")

        store.upsertSchedules(listOf("bar")) { _, _ ->
            schedule
        }

        assertNull(store.getSchedule("bar")?.associatedData)
    }

    private fun makeSchedule(identifier: String, group: String? = null): AutomationScheduleData {
        val schedule = AutomationSchedule(
            identifier = identifier,
            data = AutomationSchedule.ScheduleData.InAppMessageData(
                InAppMessage(
                name = "some name",
                displayContent = InAppMessageDisplayContent.CustomContent(Custom(JsonValue.wrap("Custom")))
                )
            ),
            triggers = listOf(),
            created = 0U,
            group = group
        )

        return AutomationScheduleData(
            schedule = schedule,
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = 0L,
            executionCount = 0,
            triggerSessionId = UUID.randomUUID().toString()
        )
    }
}
