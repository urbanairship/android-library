package com.urbanairship.automation.action

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionCompletionCallback
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionRunRequestExtender
import com.urbanairship.actions.ActionRunner
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.limits.TestAutomationLedger
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActionAutomationExecutorTest {

    private val ledger = TestAutomationLedger()
    private val actionRunner = TestActionRunner()
    private val executor = ActionAutomationExecutor(ledger = ledger, actionRunner = actionRunner)

    private val preparedInfo = PreparedScheduleInfo(
        scheduleId = "some id",
        triggerSessionId = UUID.randomUUID().toString(),
        ledgerSharedId = "group-1",
        triggerId = "trigger-1"
    )
    private val actions = jsonMapOf("some-action" to "some-value").toJsonValue()

    @Test
    public fun testExecuteRecordsSucceeded(): TestResult = runTest {
        val result = executor.execute(actions, preparedInfo)

        assertEquals(ScheduleExecuteResult.FINISHED, result)
        assertTrue(actionRunner.ranActions.containsKey("some-action"))

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Execution(
                    scheduleId = "some id",
                    sharedId = "group-1",
                    triggerId = "trigger-1",
                    result = LedgerExecutionResult.SUCCEEDED,
                    cancel = false
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testAdditionalAudienceMissRecordsNothing(): TestResult = runTest {
        val result = executor.execute(
            actions,
            preparedInfo.copy(additionalAudienceCheckResult = false)
        )

        assertEquals(ScheduleExecuteResult.FINISHED, result)
        assertTrue(actionRunner.ranActions.isEmpty())
        assertTrue(ledger.recorded.isEmpty())
    }

    private class TestActionRunner : ActionRunner {
        val ranActions = mutableMapOf<String, JsonSerializable?>()

        override fun run(
            name: String,
            value: JsonSerializable?,
            situation: Action.Situation?,
            extender: ActionRunRequestExtender?,
            callback: ActionCompletionCallback?
        ) {
            ranActions[name] = value
            callback?.onFinish(
                ActionArguments(situation = situation ?: Action.Situation.MANUAL_INVOCATION),
                ActionResult.newEmptyResult()
            )
        }
    }
}
