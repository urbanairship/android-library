package com.urbanairship.actions.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionValue
import com.urbanairship.channel.TagEditor
import com.urbanairship.channel.TagGroupsEditor
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.jsonMapOf
import io.mockk.Called
import io.mockk.spyk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ModifyTagActionTest {
    private val mockChannelTagEditor = spyk<TagEditor>()
    private val mockChannelTagGroupEditor = spyk<TagGroupsEditor>()
    private val mockContactTagGroupEditor = spyk<TagGroupsEditor>()

    private val action = ModifyTagsAction(
        channelTagEditor = { mockChannelTagEditor },
        channelTagGroupEditor = { mockChannelTagGroupEditor },
        contactTagGroupEditor = { mockContactTagGroupEditor },
    )

    @Test
    public fun rejectsInvalidArguments() {
        assertFalse(action.acceptsArguments(ActionArguments(Action.Situation.MANUAL_INVOCATION)))
        assertFalse(action.acceptsArguments(
            arguments = ActionArguments(
                situation = Action.Situation.MANUAL_INVOCATION,
                value = ActionValue.wrap("invalid")
            )
        ))

        assertFalse(action.acceptsArguments(
            arguments = ActionArguments(
                situation = Action.Situation.MANUAL_INVOCATION,
                value = ActionValue.wrap(jsonMapOf())
            )
        ))
    }

    @Test
    public fun acceptsArguments() {
        assertTrue(action.acceptsArguments(
            arguments = makeArguments(listOf(generateArgumentsJson()))
        ))
    }

    @Test
    public fun processRejectsInvalidArguments() {

        val arguments = ActionArguments(
            situation = Action.Situation.MANUAL_INVOCATION,
            value = ActionValue.wrap("invalid")
        )
        val result = action.perform(arguments)
        assertTrue(result is ActionResult.Empty)
        assertEquals(result.status, ActionResult.Status.REJECTED_ARGUMENTS)
    }

    @Test
    public fun testChannelAdd() {
        val tags = setOf("tag1", "tag2", "tag3")
        val arguments = makeArguments(listOf(generateArgumentsJson(tags = tags)))
        action.perform(arguments)

        verify(exactly = 1) {
            mockChannelTagEditor.addTags(tags)
            mockChannelTagEditor.apply()
        }

        verify { mockChannelTagGroupEditor wasNot Called }
        verify { mockContactTagGroupEditor wasNot Called }
    }

    @Test
    public fun testChannelRemove() {
        val tags = setOf("tag1", "tag2", "tag3")
        val arguments = makeArguments(listOf(generateArgumentsJson(action = "remove", tags = tags)))
        action.perform(arguments)

        verify(exactly = 1) {
            mockChannelTagEditor.removeTags(tags)
            mockChannelTagEditor.apply()
        }

        verify { mockChannelTagGroupEditor wasNot Called }
        verify { mockContactTagGroupEditor wasNot Called }
    }

    @Test
    public fun testChannelAddGroup() {
        val tags = setOf("tag1", "tag2", "tag3")
        val group = "group1"
        val arguments = makeArguments(listOf(generateArgumentsJson(tags = tags, group = group)))
        action.perform(arguments)

        verify(exactly = 1) {
            mockChannelTagGroupEditor.addTags(group, tags)
            mockChannelTagGroupEditor.apply()
        }

        verify { mockChannelTagEditor wasNot Called }
        verify { mockContactTagGroupEditor wasNot Called }
    }

    @Test
    public fun testChannelRemoveGroup() {
        val tags = setOf("tag1", "tag2", "tag3")
        val group = "group1"
        val arguments = makeArguments(listOf(generateArgumentsJson(action = "remove", tags = tags, group = group)))
        action.perform(arguments)

        verify(exactly = 1) {
            mockChannelTagGroupEditor.removeTags(group, tags)
            mockChannelTagGroupEditor.apply()
        }

        verify { mockChannelTagEditor wasNot Called }
        verify { mockContactTagGroupEditor wasNot Called }
    }

    @Test
    public fun testChannelAddInvalidJson() {
        val arguments = makeArguments(listOf(
            ActionValue.wrap(jsonMapOf(
                "type" to "channel",
                "action" to "add",
            )
        )))

        val result = action.perform(arguments)
        assertTrue(result is ActionResult.Error)
    }

    @Test
    public fun testContactGroupAdd() {
        val tags = setOf("tag1", "tag2", "tag3")
        val group = "group1"
        val arguments = makeArguments(
            listOf(generateArgumentsJson(tags = tags, group = group, type = "contact"))
        )

        action.perform(arguments)

        verify(exactly = 1) {
            mockContactTagGroupEditor.addTags(group, tags)
            mockContactTagGroupEditor.apply()
        }

        verify { mockChannelTagEditor wasNot Called }
        verify { mockChannelTagGroupEditor wasNot Called }
    }

    @Test
    public fun testContactGroupRemove() {
        val tags = setOf("tag1", "tag2", "tag3")
        val group = "group1"
        val arguments = makeArguments(
            listOf(generateArgumentsJson(action = "remove", tags = tags, group = group, type = "contact"))
        )

        action.perform(arguments)

        verify(exactly = 1) {
            mockContactTagGroupEditor.removeTags(group, tags)
            mockContactTagGroupEditor.apply()
        }

        verify { mockChannelTagEditor wasNot Called }
        verify { mockChannelTagGroupEditor wasNot Called }
    }

    @Test
    public fun testContactGroupAddInvalidJson() {
        val arguments = makeArguments(
            listOf(
                ActionValue.wrap(jsonMapOf(
                    "type" to "contact",
                    "action" to "add",
                    "tags" to listOf("tag1", "tag2"),
                )
            )
        ))

        val result = action.perform(arguments)
        assertTrue(result is ActionResult.Error)
    }

    @Test
    public fun testMultipleOperations() {
        val contactTags = setOf("tag1", "tag2", "tag3")
        val channelGroupTags = setOf("tag3", "tag4", "tag5")
        val channelTags = setOf("tag4", "tag5", "tag6")

        val arguments = makeArguments(
            listOf(
                generateArgumentsJson(tags = channelTags),
                generateArgumentsJson(type = "contact", tags = contactTags, group = "contact-group"),
                generateArgumentsJson(type = "contact", action = "remove", tags = contactTags, group = "contact-group"),
                generateArgumentsJson(tags = channelGroupTags, group = "channel-group"),
                generateArgumentsJson(action = "remove", tags = channelGroupTags, group = "channel-group"),
            )
        )

        action.perform(arguments)

        verify(exactly = 1) {
            mockContactTagGroupEditor.addTags("contact-group", contactTags)
            mockContactTagGroupEditor.removeTags("contact-group", contactTags)
            mockContactTagGroupEditor.apply()

            mockChannelTagGroupEditor.addTags("channel-group", channelGroupTags)
            mockChannelTagGroupEditor.removeTags("channel-group", channelGroupTags)
            mockChannelTagGroupEditor.apply()

            mockChannelTagEditor.addTags(channelTags)
            mockChannelTagEditor.apply()
        }
    }

    private fun makeArguments(value: List<JsonSerializable>): ActionArguments {
        return ActionArguments(
            situation = Action.Situation.MANUAL_INVOCATION,
            value = ActionValue.wrap(value)
        )
    }

    private fun generateArgumentsJson(
        type: String = "channel",
        action: String = "add",
        tags: Set<String> = setOf("tag1", "tag2"),
        group: String? = null
    ): JsonMap = jsonMapOf(
        "type" to type,
        "action" to action,
        "tags" to tags,
        "group" to group,
    )
}
