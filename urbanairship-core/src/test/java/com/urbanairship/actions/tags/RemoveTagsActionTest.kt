/* Copyright Airship and Contributors */
package com.urbanairship.actions.tags

import com.urbanairship.channel.TagEditor
import com.urbanairship.channel.TagGroupsEditor
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class RemoveTagsActionTest {

    private val mockChannelTagEditor = spyk<TagEditor>()
    private val mockChannelTagGroupEditor = spyk<TagGroupsEditor>()
    private val mockContactTagGroupEditor = spyk<TagGroupsEditor>()

    public var action: RemoveTagsAction = RemoveTagsAction(
        channelTagEditor = { mockChannelTagEditor },
        channelTagGroupEditor = { mockChannelTagGroupEditor },
        contactTagGroupEditor = { mockContactTagGroupEditor },
    )

    @Test
    public fun testApplyChannelTags() {
        val tags = setOf("foo", "bar")
        action.applyChannelTags(tags)
        verify {
            mockChannelTagEditor.removeTags(tags)
            mockChannelTagEditor.apply()
        }
    }

    @Test
    public fun testApplyChannelTagGroups() {
        val tags = mapOf(
            "group1" to setOf("tag1", "tag2", "tag3"), "group2" to setOf("foo", "bar")
        )

        action.applyChannelTagGroups(tags)
        verify {
            tags.forEach {
                mockChannelTagGroupEditor.removeTags(it.key, it.value)
            }
            mockChannelTagGroupEditor.apply()
        }
    }

    @Test
    public fun testApplyContactTagGroups() {
        val tags = mapOf(
            "group1" to setOf("tag1", "tag2", "tag3"), "group2" to setOf("foo", "bar")
        )

        action.applyContactTagGroups(tags)
        verify {
            tags.forEach {
                mockContactTagGroupEditor.removeTags(it.key, it.value)
            }
            mockContactTagGroupEditor.apply()
        }
    }
}
