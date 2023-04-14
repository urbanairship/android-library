/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.SubscriptionListMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.contacts.Scope
import com.urbanairship.contacts.ScopedSubscriptionListMutation
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AudienceOverridesProviderTest {

    private val clock = TestClock()
    private val provider = AudienceOverridesProvider(clock)

    @Test
    public fun testContactOverrides(): TestResult = runTest {
        val pendingContact = AudienceOverrides.Contact(
            tags = listOf(
                TagGroupsMutation.newAddTagsMutation(
                    "some pending group",
                    setOf("neat")
                )
            ), attributes = listOf(
                AttributeMutation.newRemoveAttributeMutation("some pending attribute", 0),
                AttributeMutation.newRemoveAttributeMutation("some other pending attribute", 0)
            ), subscriptions = listOf(
                ScopedSubscriptionListMutation.newSubscribeMutation("some list", Scope.APP, 0),
                ScopedSubscriptionListMutation.newSubscribeMutation("some other list", Scope.SMS, 0)
            )
        )

        provider.pendingContactOverridesDelegate = {
            assertEquals(it, "some contact")
            pendingContact
        }

        provider.recordContactUpdate(
            "some other contact",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo"))),
        )

        provider.recordContactUpdate(
            "some contact",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))),
        )

        val overrides = provider.contactOverrides("some contact")
        val expected = AudienceOverrides.Contact(
            tags = listOf(
                TagGroupsMutation.newAddTagsMutation("some group", setOf("foo")),
                TagGroupsMutation.newAddTagsMutation("some pending group", setOf("neat"))
            ),
            attributes = pendingContact.attributes,
            subscriptions = pendingContact.subscriptions,
        )
        assertEquals(expected, overrides)
    }

    @Test
    public fun testPullStableContactId(): TestResult = runTest {
        provider.recordContactUpdate(
            "stable contact id",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo"))),
        )

        provider.pendingContactOverridesDelegate = {
            assertEquals("stable contact id", it)
            AudienceOverrides.Contact()
        }

        provider.stableContactIdDelegate = {
            "stable contact id"
        }

        val overrides = provider.contactOverrides(null)
        val expected = AudienceOverrides.Contact(
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo")))
        )
        assertEquals(expected, overrides)
    }

    @Test
    public fun testChannelOverrides(): TestResult = runTest {
        val pendingChannel = AudienceOverrides.Channel(
            tags = listOf(
                TagGroupsMutation.newAddTagsMutation(
                    "some pending group",
                    setOf("neat")
                )
            ), attributes = listOf(
                AttributeMutation.newRemoveAttributeMutation("some pending attribute", 0),
                AttributeMutation.newRemoveAttributeMutation("some other pending attribute", 0)
            ), subscriptions = listOf(
                SubscriptionListMutation.newSubscribeMutation("some list", 0),
                SubscriptionListMutation.newSubscribeMutation("some other list", 0)
            )
        )

        provider.pendingChannelOverridesDelegate = {
            assertEquals(it, "some channel")
            pendingChannel
        }

        provider.recordChannelUpdate(
            "some other channel",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo"))),
        )

        provider.recordChannelUpdate(
            "some channel",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))),
        )

        val overrides = provider.channelOverrides("some channel")
        val expected = AudienceOverrides.Channel(
            tags = listOf(
                TagGroupsMutation.newAddTagsMutation("some group", setOf("foo")),
                TagGroupsMutation.newAddTagsMutation("some pending group", setOf("neat"))
            ),
            attributes = pendingChannel.attributes,
            subscriptions = pendingChannel.subscriptions,
        )
        assertEquals(expected, overrides)
    }

    @Test
    public fun testExpireUpdates(): TestResult = runTest {
        provider.recordContactUpdate(
            "some contact",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo"))),
        )

        clock.currentTimeMillis += 1

        provider.recordChannelUpdate(
            "some channel",
            tags = listOf(TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))),
        )

        // Should have both
        assertEquals(
            AudienceOverrides.Channel(
                tags = listOf(
                    TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo")),
                    TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))
                )
            ),
            provider.channelOverrides("some channel", "some contact")
        )

        // Right before the contact update expires
        clock.currentTimeMillis += 599998

        // Almost expired, should have both
        assertEquals(
            AudienceOverrides.Channel(
                tags = listOf(
                    TagGroupsMutation.newAddTagsMutation("some other group", setOf("foo")),
                    TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))
                )
            ),
            provider.channelOverrides("some channel", "some contact")
        )

        // Expire contact
        clock.currentTimeMillis += 1

        // Contact update expired, should just have channel
        assertEquals(
            AudienceOverrides.Channel(
                tags = listOf(
                    TagGroupsMutation.newAddTagsMutation("some group", setOf("foo"))
                )
            ),
            provider.channelOverrides("some channel", "some contact")
        )

        clock.currentTimeMillis += 1

        // Both expired, should be empty
        assertEquals(
            AudienceOverrides.Channel(), provider.channelOverrides("some channel", "some contact")
        )
    }
}
