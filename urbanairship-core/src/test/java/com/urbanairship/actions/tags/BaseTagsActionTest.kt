/* Copyright Airship and Contributors */
package com.urbanairship.actions.tags

import com.urbanairship.actions.Action
import com.urbanairship.actions.Action.Situation
import com.urbanairship.actions.ActionTestUtils
import com.urbanairship.json.jsonMapOf
import app.cash.turbine.test
import io.mockk.spyk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class BaseTagsActionTest {

    private var action: BaseTagsAction = spyk(object : BaseTagsAction() {
        override fun applyChannelTags(tags: Set<String>) {}
        override fun applyChannelTagGroups(tags: Map<String, Set<String>>) {}
        override fun applyContactTagGroups(tags: Map<String, Set<String>>) {}
    })

    @Test
    public fun testAcceptsArguments() {
        val acceptedSituations: IntArray = intArrayOf(
            Action.SITUATION_PUSH_OPENED,
            Action.SITUATION_MANUAL_INVOCATION,
            Action.SITUATION_WEB_VIEW_INVOCATION,
            Action.SITUATION_PUSH_RECEIVED,
            Action.SITUATION_AUTOMATION,
            Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
            Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        )

        var args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "tag1")
        assertTrue(action.acceptsArguments(args))

        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "[tag1,tag2,tag3]")
        assertTrue(action.acceptsArguments(args))

        args = ActionTestUtils.createArgs(
            Action.SITUATION_MANUAL_INVOCATION, "{group: [tag1, tag2, tag3]}"
        )
        assertTrue(action.acceptsArguments(args))


        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, 1)
        assertFalse(action.acceptsArguments(args))

        args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, null)
        assertFalse(action.acceptsArguments(args))

        // Check every accepted situation
        for (@Situation situation in acceptedSituations) {
            args = ActionTestUtils.createArgs(situation, "tag1")
            assertTrue(action.acceptsArguments(args))
        }
    }

    @Test
    public fun testSingleTag() {
        val singleTagArg = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "tag1")
        action.perform(singleTagArg)
    }

    @Test
    public fun testSingleTagFlow(): TestResult = runTest {
        action.mutationsFlow.test {
            val singleTagArg = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "tag1")
            action.perform(singleTagArg)
            assertEquals(awaitItem(), TagActionMutation.ChannelTags(setOf("tag1")))
        }

        verify {
            action.applyChannelTags(setOf("tag1"))
        }
    }

    @Test
    public fun testTagList(): TestResult = runTest {
        action.mutationsFlow.test {
            val collectionArgs = ActionTestUtils.createArgs(
                Action.SITUATION_PUSH_RECEIVED, mutableListOf("tag1", "tag2", "tag3")
            )
            action.perform(collectionArgs)

            assertEquals(awaitItem(), TagActionMutation.ChannelTags(setOf("tag1", "tag2", "tag3")))
        }

        verify {
            action.applyChannelTags(setOf("tag1", "tag2", "tag3"))
        }
    }

    @Test
    public fun testGroups(): TestResult = runTest {
        val json = jsonMapOf(
            "channel" to jsonMapOf(
                "group1" to listOf("tag1", "tag2", "tag3")
            ), "named_user" to jsonMapOf(
                "group2" to listOf("tag4", "tag5", "tag6")
            )
        )

        action.mutationsFlow.test {
            val args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, json)
            action.perform(args)

            assertEquals(
                awaitItem(), TagActionMutation.ChannelTagGroups(
                    mapOf(
                        "group1" to setOf("tag1", "tag2", "tag3")
                    )
                )
            )

            assertEquals(
                awaitItem(), TagActionMutation.ContactTagGroups(
                    mapOf(
                        "group2" to setOf("tag4", "tag5", "tag6")
                    )
                )
            )
        }

        verify {
            action.applyChannelTagGroups(
                mapOf(
                    "group1" to setOf("tag1", "tag2", "tag3")
                )
            )

            action.applyContactTagGroups(
                mapOf(
                    "group2" to setOf("tag4", "tag5", "tag6")
                )
            )
        }
    }

    @Test
    public fun testGroupsAndDeviceTags(): TestResult = runTest {
        val json = jsonMapOf(
            "channel" to jsonMapOf(
                "group1" to listOf("tag1", "tag2", "tag3")
            ), "named_user" to jsonMapOf(
                "group2" to listOf("tag4", "tag5", "tag6")
            ), "device" to listOf("tag7", "tag8")
        )

        action.mutationsFlow.test {
            val args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, json)
            action.perform(args)

            assertEquals(
                awaitItem(), TagActionMutation.ChannelTagGroups(
                    mapOf(
                        "group1" to setOf("tag1", "tag2", "tag3")
                    )
                )
            )

            assertEquals(
                awaitItem(), TagActionMutation.ContactTagGroups(
                    mapOf(
                        "group2" to setOf("tag4", "tag5", "tag6")
                    )
                )
            )

            assertEquals(
                awaitItem(), TagActionMutation.ChannelTags(setOf("tag7", "tag8"))
            )
        }

        verify {
            action.applyChannelTagGroups(
                mapOf(
                    "group1" to setOf("tag1", "tag2", "tag3")
                )
            )

            action.applyContactTagGroups(
                mapOf(
                    "group2" to setOf("tag4", "tag5", "tag6")
                )
            )

            action.applyChannelTags(
                setOf("tag7", "tag8")
            )
        }
    }
}
