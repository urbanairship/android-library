package com.urbanairship.channel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.http.RequestResult
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonListOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class ChannelBatchUpdateManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferenceStore = PreferenceStore.inMemoryStore(context)

    private val pendingAudienceDelegate = slot<(String) -> AudienceOverrides.Channel>()
    private val mockAudienceOverridesProvider = mockk<AudienceOverridesProvider> {
        every {
            this@mockk.pendingChannelOverridesDelegate = capture(pendingAudienceDelegate)
        } just runs
    }
    private val mockApiClient = mockk<ChannelBatchUpdateApiClient>()

    private val testDispatcher = StandardTestDispatcher()

    private val manager = ChannelBatchUpdateManager(
        preferenceStore,
        mockApiClient,
        mockAudienceOverridesProvider
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testAddUpdate(): TestResult = runTest {
        manager.addUpdate(
            tags = listOf(TagGroupsMutation.newSetTagsMutation("some group", setOf("tag")))
        )

        manager.addUpdate(
            subscriptions = listOf(SubscriptionListMutation.newSubscribeMutation("some list", 100))
        )

        manager.addUpdate(
            attributes = listOf(AttributeMutation.newRemoveAttributeMutation("some attribute", 100))
        )

        val expectedPending = AudienceOverrides.Channel(
            listOf(TagGroupsMutation.newSetTagsMutation("some group", setOf("tag"))),
            listOf(AttributeMutation.newRemoveAttributeMutation("some attribute", 100)),
            listOf(SubscriptionListMutation.newSubscribeMutation("some list", 100))
        )

        assertEquals(expectedPending, pendingAudienceDelegate.captured.invoke("anything"))
    }

    @Test
    public fun testClearPending(): TestResult = runTest {
        manager.addUpdate(
            tags = listOf(TagGroupsMutation.newSetTagsMutation("some group", setOf("tag"))),
            subscriptions = listOf(SubscriptionListMutation.newSubscribeMutation("some list", 100)),
            attributes = listOf(AttributeMutation.newRemoveAttributeMutation("some attribute", 100))
        )
        assertTrue(manager.hasPending)

        manager.clearPending()
        assertEquals(AudienceOverrides.Channel(), pendingAudienceDelegate.captured.invoke("anything"))
        assertFalse(manager.hasPending)
    }

    @Test
    public fun testClearPendingNoUpdates(): TestResult = runTest {
        assertEquals(AudienceOverrides.Channel(), pendingAudienceDelegate.captured.invoke("anything"))
        manager.clearPending()
        assertEquals(AudienceOverrides.Channel(), pendingAudienceDelegate.captured.invoke("anything"))
    }

    @Test
    public fun testMigrate(): TestResult = runTest {
        val attributeKey = SyncPrefKey.json("com.urbanairship.push.ATTRIBUTE_DATA_STORE")
        val subscriptionKey = SyncPrefKey.json("com.urbanairship.push.PENDING_SUBSCRIPTION_MUTATIONS")
        val tagsKey = SyncPrefKey.json("com.urbanairship.push.PENDING_TAG_GROUP_MUTATIONS")

        // Attributes are stored as a list of lists
        preferenceStore.put(
            attributeKey,
            jsonListOf(
                listOf(
                    AttributeMutation.newRemoveAttributeMutation("some attribute", 100),
                    AttributeMutation.newRemoveAttributeMutation("some other attribute", 100)
                ),
                listOf(
                    AttributeMutation.newSetAttributeMutation("some attribute", JsonValue.wrapOpt("neat"), 100)
                )
            ).toJsonValue()
        )

        // Subscriptions are stored as a list of lists
        preferenceStore.put(
            subscriptionKey,
            jsonListOf(
                listOf(
                    SubscriptionListMutation.newSubscribeMutation("some list", 100),
                    SubscriptionListMutation.newUnsubscribeMutation("some other list", 100)
                ),
                listOf(
                    SubscriptionListMutation.newSubscribeMutation("some other list", 100)
                )
            ).toJsonValue()
        )

        // Tags are stored as a list of mutations
        preferenceStore.put(
            tagsKey,
            jsonListOf(
                TagGroupsMutation.newSetTagsMutation("some group", setOf("tag")),
                TagGroupsMutation.newSetTagsMutation("some other group", setOf("tag"))
            ).toJsonValue()
        )

        manager.migrateData()

        // Verify its deleted
        assertFalse(preferenceStore.isSet(tagsKey))
        assertFalse(preferenceStore.isSet(subscriptionKey))
        assertFalse(preferenceStore.isSet(attributeKey))

        // Check expected
        val expectedPending = AudienceOverrides.Channel(
            listOf(
                TagGroupsMutation.newSetTagsMutation("some group", setOf("tag")),
                TagGroupsMutation.newSetTagsMutation("some other group", setOf("tag"))
            ),
            listOf(
                AttributeMutation.newRemoveAttributeMutation("some attribute", 100),
                AttributeMutation.newRemoveAttributeMutation("some other attribute", 100),
                AttributeMutation.newSetAttributeMutation("some attribute", JsonValue.wrapOpt("neat"), 100)
            ),
            listOf(
                SubscriptionListMutation.newSubscribeMutation("some list", 100),
                SubscriptionListMutation.newUnsubscribeMutation("some other list", 100),
                SubscriptionListMutation.newSubscribeMutation("some other list", 100)
            )
        )

        assertEquals(expectedPending, pendingAudienceDelegate.captured.invoke("anything"))
    }

    @Test
    public fun testUploadNoUpdates(): TestResult = runTest {
        manager.clearPending()
        assertTrue(manager.uploadPending("some channel"))
    }

    @Test
    public fun testUpload(): TestResult = runTest {
        manager.addUpdate(
            tags = listOf(
                TagGroupsMutation.newAddTagsMutation("some group", setOf("tag")),
                TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
            )
        )

        manager.addUpdate(
            attributes = listOf(
                AttributeMutation.newRemoveAttributeMutation("some attribute", 100),
                AttributeMutation.newRemoveAttributeMutation("some other attribute", 100),
                AttributeMutation.newSetAttributeMutation("some attribute", JsonValue.wrapOpt("neat"), 100)
            )
        )

        manager.addUpdate(
            subscriptions = listOf(
                SubscriptionListMutation.newSubscribeMutation("some list", 100),
                SubscriptionListMutation.newUnsubscribeMutation("some other list", 100),
                SubscriptionListMutation.newSubscribeMutation("some other list", 100)
            )
        )

        manager.addUpdate(
            liveUpdates = listOf(
                LiveUpdateMutation.Remove("some event", 100, 100),
                LiveUpdateMutation.Set("some other event", 100, 100)
            )
        )

        coEvery { mockApiClient.update(any(), any(), any(), any(), any()) } returns RequestResult(
            status = 200,
            value = null,
            body = null,
            headers = null
        )

        coEvery { mockAudienceOverridesProvider.recordChannelUpdate(any(), any(), any(), any()) } just runs

        assertTrue(manager.uploadPending("some channel id"))

        coVerify {
            mockApiClient.update(
                channelId = "some channel id",
                tags = listOf(
                    TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
                ),
                attributes = listOf(
                    AttributeMutation.newRemoveAttributeMutation("some other attribute", 100),
                    AttributeMutation.newSetAttributeMutation("some attribute", JsonValue.wrapOpt("neat"), 100)
                ),
                subscriptions = listOf(
                    SubscriptionListMutation.newSubscribeMutation("some list", 100),
                    SubscriptionListMutation.newSubscribeMutation("some other list", 100)
                ),
                liveUpdates = listOf(
                    LiveUpdateMutation.Remove("some event", 100, 100),
                    LiveUpdateMutation.Set("some other event", 100, 100)
                )
            )
        }

        coVerify {
            mockAudienceOverridesProvider.recordChannelUpdate(
                channelId = "some channel id",
                tags = listOf(
                    TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
                ),
                attributes = listOf(
                    AttributeMutation.newRemoveAttributeMutation("some other attribute", 100),
                    AttributeMutation.newSetAttributeMutation("some attribute", JsonValue.wrapOpt("neat"), 100)
                ),
                subscriptions = listOf(
                    SubscriptionListMutation.newSubscribeMutation("some list", 100),
                    SubscriptionListMutation.newSubscribeMutation("some other list", 100)
                ),
            )
        }

        assertFalse(manager.hasPending)
    }

    @Test
    public fun testUploadFailsServerError(): TestResult = runTest {
        manager.addUpdate(
            tags = listOf(
                TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
            )
        )

        coEvery { mockApiClient.update(any(), any(), any(), any(), any()) } returns RequestResult(
            status = 500,
            value = null,
            body = null,
            headers = null
        )

        assertFalse(manager.uploadPending("some channel id"))

        coVerify {
            mockApiClient.update(
                channelId = "some channel id",
                tags = listOf(
                    TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
                ),
                attributes = emptyList(),
                subscriptions = emptyList(),
                liveUpdates = emptyList(),
            )
        }

        assertTrue(manager.hasPending)
    }

    @Test
    public fun testUploadClientError(): TestResult = runTest {
        manager.addUpdate(
            tags = listOf(
                TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
            )
        )

        coEvery { mockApiClient.update(any(), any(), any(), any(), any()) } returns RequestResult(
            status = 400,
            value = null,
            body = null,
            headers = null
        )

        // We treat a client error as success since we pop the pending changes
        assertTrue(manager.uploadPending("some channel id"))

        coVerify {
            mockApiClient.update(
                channelId = "some channel id",
                tags = listOf(
                    TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
                ),
                attributes = emptyList(),
                subscriptions = emptyList(),
                liveUpdates = emptyList(),
            )
        }

        assertFalse(manager.hasPending)
    }

    @Test
    public fun testUploadException(): TestResult = runTest {
        manager.addUpdate(
            tags = listOf(
                TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
            )
        )

        coEvery { mockApiClient.update(any(), any(), any(), any(), any()) } returns RequestResult(
            exception = IllegalArgumentException()
        )

        assertFalse(manager.uploadPending("some channel id"))

        coVerify {
            mockApiClient.update(
                channelId = "some channel id",
                tags = listOf(
                    TagGroupsMutation.newRemoveTagsMutation("some group", setOf("tag"))
                ),
                attributes = emptyList(),
                subscriptions = emptyList(),
                liveUpdates = emptyList(),
            )
        }

        assertTrue(manager.hasPending)
    }
}
