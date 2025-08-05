/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Parcel
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.UAirship
import com.urbanairship.actions.ActionValue
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushMessageTest {

    private val clock = TestClock()

    /**
     * Test when the message expired.
     */
    @Test
    fun testIsExpired() {
        // Set expiration in the past (Sun, 09 Sep 2001 01:46:40 GMT)
        val pushMessage = PushMessage(
            bundleOf(PushMessage.EXTRA_EXPIRATION to 1000000.toString())
        )
        Assert.assertTrue("Message should have expired.", pushMessage.isExpired)
    }

    /**
     * Test when message does not have an expiration.
     */
    @Test
    fun testNoExpiration() {
        val pushMessage = PushMessage(bundleOf())
        Assert.assertFalse("Message should not have an expiration.", pushMessage.isExpired)
    }

    /**
     * Test when the message has not expired.
     */
    @Test
    fun testNotExpired() {
        clock.currentTimeMillis = 1

        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_EXPIRATION to 1.toString()), // Set expiration in the future
            clock = clock
        )
        Assert.assertFalse("Message has not expired.", pushMessage.isExpired)
    }

    /**
     * Test the message is ping.
     */
    @Test
    fun testIsPing() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PING to "testPing")
        )
        Assert.assertTrue("The message is ping.", pushMessage.isPing)
    }

    /**
     * Test the message is not ping.
     */
    @Test
    fun testIsNotPing() {
        val pushMessage = PushMessage(bundleOf())
        Assert.assertFalse("The message is not ping.", pushMessage.isPing)
    }

    /**
     * Test get the message's canonical push ID.
     */
    @Test
    fun testGetCanonicalPushId() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PUSH_ID to "testPushID")
        )
        Assert.assertEquals("The push ID should match.", "testPushID", pushMessage.canonicalPushId)
    }

    /**
     * Test get the rich push message ID.
     */
    @Test
    fun testGetRichPushMessageId() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_RICH_PUSH_ID to "testRichPushID")
        )
        Assert.assertEquals(
            "The rich push ID should match.", "testRichPushID", pushMessage.richPushMessageId
        )
    }

    /**
     * Test get the notification alert.
     */
    @Test
    fun testGetAlert() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_ALERT to "Test Push Alert!")
        )
        Assert.assertEquals(
            "The notification alert should match.", "Test Push Alert!", pushMessage.alert
        )
    }

    /**
     * Test get push send ID.
     */
    @Test
    fun testGetSendId() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_SEND_ID to "testSendID")
        )
        Assert.assertEquals("The push send ID should match.", "testSendID", pushMessage.sendId)
    }

    /**
     * Test get push metadata.
     */
    @Test
    fun testGetMetadata() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_METADATA to "testMetadata")
        )
        Assert.assertEquals(
            "The push send metadata should match.", "testMetadata", pushMessage.metadata
        )
    }

    /**
     * Test get push bundle.
     */
    @Test
    fun testGetPushBundle() {
        val extras = bundleOf(
            PushMessage.EXTRA_ALERT to "Test Push Alert!",
            PushMessage.EXTRA_PUSH_ID to "testPushID"
        )

        val pushMessage = PushMessage(extras)
        Assert.assertEquals("The push bundle should match.", extras, pushMessage.getPushBundle())
    }

    /**
     * Test get notification title.
     */
    @Test
    fun testGetTitle() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_TITLE to "Test Title")
        )
        Assert.assertEquals("The notification title should match.", "Test Title", pushMessage.title)
    }

    /**
     * Test get notification summary.
     */
    @Test
    fun testGetSummary() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_SUMMARY to "Test Summary")
        )
        Assert.assertEquals(
            "The notification summary should match.", "Test Summary", pushMessage.summary
        )
    }

    /**
     * Test get wearable payload.
     */
    @Test
    fun testGetWearablePayload() {
        val wearable = """
            {
              "wearable": {
                "background_image": "http://example.com/background.jpg",
                "extra_pages": [
                  {
                    "alert": "Page 1 title, alert",
                    "title": "Page 1 title"
                  }
                ]
              }
            }
        """.trimIndent()

        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_WEARABLE to wearable)
        )
        Assert.assertEquals(
            "The wearable payload should match.", wearable, pushMessage.wearablePayload
        )
    }

    /**
     * Test get notification style payload.
     */
    @Test
    fun testGetStylePayload() {
        val bigTextStyle =
            "\"type\":\"big_text\", \"big_text\":\"big text\", \"title\":\"big text title\", \"summary\":\"big text summary\""

        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_STYLE to bigTextStyle)
        )
        Assert.assertEquals(
            "The style payload should match.", bigTextStyle, pushMessage.stylePayload
        )
    }

    /**
     * Test isLocalOnly.
     */
    @Test
    fun testIsLocalOnly() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_LOCAL_ONLY to "true")
        )
        Assert.assertTrue("IsLocalOnly should return true", pushMessage.isLocalOnly)
    }

    /**
     * Test getPriority at the MAX_PRIORITY.
     */
    @Test
    fun testGetPriorityMax() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PRIORITY to PushMessage.MAX_PRIORITY.toString())
        )
        Assert.assertEquals(
            "Should constrain to the max.",
            pushMessage.priority,
            PushMessage.MAX_PRIORITY
        )
    }

    /**
     * Test getPriority above the MAX_PRIORITY.
     */
    @Test
    fun testGetPriorityAboveMax() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PRIORITY to (PushMessage.MAX_PRIORITY + 1).toString())
        )
        Assert.assertEquals(
            "Should constrain to the max.",
            pushMessage.priority,
            PushMessage.MAX_PRIORITY
        )
    }

    /**
     * Test getPriority below the MAX_PRIORITY.
     */
    @Test
    fun testGetPriorityBelowMax() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PRIORITY to (PushMessage.MAX_PRIORITY - 1).toString())
        )
        Assert.assertEquals(
            "Should allow values between the min and max.",
            pushMessage.priority,
            PushMessage.MAX_PRIORITY - 1
        )
    }

    /**
     * Test getPriority at the MIN_PRIORITY.
     */
    @Test
    fun testGetPriorityMin() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PRIORITY to PushMessage.MIN_PRIORITY.toString())
        )
        Assert.assertEquals(
            "Should constrain to the min.",
            pushMessage.priority,
            PushMessage.MIN_PRIORITY
        )
    }

    /**
     * Test getPriority above the MIN_PRIORITY.
     */
    @Test
    fun testGetPriorityAboveMin() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PRIORITY to (PushMessage.MIN_PRIORITY + 1).toString())
        )
        Assert.assertEquals(
            "Should allow values between the min and max.",
            pushMessage.priority,
            PushMessage.MIN_PRIORITY + 1
        )
    }

    /**
     * Test getPriority below the MIN_PRIORITY.
     */
    @Test
    fun testGetPriorityBelowMin() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PRIORITY to (PushMessage.MIN_PRIORITY - 1).toString())
        )
        Assert.assertEquals(
            "Should constrain to the min.",
            pushMessage.priority,
            PushMessage.MIN_PRIORITY
        )
    }

    /**
     * Test getVisibility at the MAX_VISIBILITY.
     */
    @Test
    fun testGetVisibilityMax() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_VISIBILITY to PushMessage.MAX_VISIBILITY.toString())
        )
        Assert.assertEquals(
            "Should constrain to the max.",
            pushMessage.visibility,
            PushMessage.MAX_VISIBILITY
        )
    }

    /**
     * Test getVisibility above MAX_VISIBILITY.
     */
    @Test
    fun testGetVisibilityAboveMax() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_VISIBILITY to (PushMessage.MAX_VISIBILITY + 1).toString())
        )
        Assert.assertEquals(
            "Should constrain to the max.",
            pushMessage.visibility,
            PushMessage.MAX_VISIBILITY
        )
    }

    /**
     * Test getVisibility below MAX_VISIBILITY.
     */
    @Test
    fun testGetVisibilityBelowMax() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_VISIBILITY to (PushMessage.MAX_VISIBILITY - 1).toString())
        )
        Assert.assertEquals(
            "Should allow values between the min and max.",
            pushMessage.visibility,
            PushMessage.MAX_VISIBILITY - 1
        )
    }

    /**
     * Test getVisibility at the MIN_VISIBILITY.
     */
    @Test
    fun testGetVisibilityMin() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_VISIBILITY to PushMessage.MIN_VISIBILITY.toString())
        )
        Assert.assertEquals(
            "Should constrain to the min.",
            pushMessage.visibility,
            PushMessage.MIN_VISIBILITY
        )
    }

    /**
     * Test getVisibility above MIN_VISIBILITY.
     */
    @Test
    fun testGetVisibilityAboveMin() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_VISIBILITY to (PushMessage.MIN_VISIBILITY + 1).toString())
        )
        Assert.assertEquals(
            "Should allow values between the min and max.",
            pushMessage.visibility,
            PushMessage.MIN_VISIBILITY + 1
        )
    }

    /**
     * Test getVisibility below MIN_VISIBILITY.
     */
    @Test
    fun testGetVisibilityBelowMin() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_VISIBILITY to (PushMessage.MIN_VISIBILITY - 1).toString())
        )
        Assert.assertEquals(
            "Should constrain to the min.",
            pushMessage.visibility,
            PushMessage.MIN_VISIBILITY
        )
    }

    /**
     * Test getCategory.
     */
    @Test
    fun testGetCategory() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_CATEGORY to "promo")
        )
        Assert.assertEquals("The category should match.", pushMessage.category, "promo")
    }

    /**
     * Test get public notification payload.
     */
    @Test
    fun testGetPublicNotificationPayload() {
        val publicNotification =
            "\"title\":\"test title\", \"alert\":\"test alert\", \"summary\":\"test summary\""

        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_PUBLIC_NOTIFICATION to publicNotification)
        )
        Assert.assertEquals(
            "The public notification payload should match.",
            publicNotification,
            pushMessage.publicNotificationPayload
        )
    }

    /**
     * Test saving and reading a push message from a parcel.
     */
    @Test
    fun testParcelable() {
        val message = PushMessage(
            pushBundle = bundleOf(
                PushMessage.EXTRA_ALERT to "Test Push Alert!",
                "a random extra" to "value"
            )
        )

        // Write the push message to a parcel
        val parcel = Parcel.obtain()
        message.writeToParcel(parcel, 0)

        // Reset the parcel so we can read it
        parcel.setDataPosition(0)

        // Create the message from the parcel
        val fromParcel = PushMessage.CREATOR.createFromParcel(parcel)
        Assert.assertEquals("value", fromParcel.getPushBundle().getString("a random extra"))
        Assert.assertEquals("Test Push Alert!", fromParcel.alert)
    }

    /**
     * Test get actions returns a Map of action names to action values.
     */
    @Test
    fun testGetActions() {
        val actions = mapOf(
            "action_name" to ActionValue.wrap("action_value"),
            "oh" to ActionValue.wrap("hi")
        )

        val message = PushMessage(
            pushBundle = bundleOf(
                PushMessage.EXTRA_ACTIONS to JsonValue.wrap(actions).toString()
            )
        )

        Assert.assertEquals(actions, message.actions)
    }

    /**
     * Test get actions returns an empty map if its unable to parse the actions payload.
     */
    @Test
    fun testGetActionsInvalidPayload() {
        val message = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_ACTIONS to "}}what{{")
        )

        Assert.assertTrue(message.actions.isEmpty())
    }

    /**
     * Test get actions appends a MessageCenterAction if it contains a message ID and does
     * not already define a inbox action.
     */
    @Test
    fun testGetActionAppendsInboxAction() {
        val actions = mutableMapOf(
            "action_name" to ActionValue.wrap("action_value"),
            "oh" to ActionValue.wrap("hi")
        )

        val message = PushMessage(
            pushBundle = bundleOf(
                PushMessage.EXTRA_ACTIONS to JsonValue.wrap(actions).toString(),
                PushMessage.EXTRA_RICH_PUSH_ID to "message ID"
            )
        )

        actions["^mc"] = ActionValue.wrap("message ID")
        Assert.assertEquals(actions, message.actions)
    }

    /**
     * Test get notification sound.
     */
    @Test
    fun testGetSound() {
        val context = spyk(UAirship.applicationContext)
        val resources: Resources = mockk {
            every { getIdentifier("test_sound", any(), any()) } returns 5
        }
        every { context.resources } returns resources

        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_SOUND to "test_sound")
        )

        val expected = Uri.parse("android.resource://" + context.packageName + "/" + 5)
        Assert.assertEquals(
            "The sound should match.", expected, pushMessage.getSound(context)
        )
    }

    /**
     * Test get notification sound is null when not found.
     */
    @Test
    fun testGetSoundNull() {
        val context = UAirship.applicationContext
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_SOUND to "test_sound")
        )

        Assert.assertNull(
            "The sound should be null.", pushMessage.getSound(context)
        )
    }

    /**
     * Test get the notification icon.
     */
    @Test
    fun testGetIcon() {
        val context = spyk(UAirship.applicationContext)
        val resources: Resources = mockk {
            every { getIdentifier("icon", any(), any()) } returns 5
        }
        every { context.resources } returns resources

        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_ICON to "icon")
        )

        Assert.assertEquals(
            "The notification icon resource should match",
            5,
            pushMessage.getIcon(context, 1).toLong()
        )
    }

    /**
     * Test get notification icon color.
     */
    @Test
    fun testGetIconColor() {
        val pushMessage = PushMessage(
            pushBundle = bundleOf(PushMessage.EXTRA_ICON_COLOR to "red")
        )
        Assert.assertEquals(
            "The notification icon color should match.",
            -65536,
            pushMessage.getIconColor(0).toLong()
        )
    }

    /**
     * Test fromIntent creates a PushMessage instance if a bundle exists under PushManager.EXTRA_PUSH_MESSAGE_BUNDLE.
     */
    @Test
    fun testFromIntent() {
        val bundle = bundleOf()
        val intent = Intent()
        intent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, bundle)

        val message = PushMessage.fromIntent(intent)
        Assert.assertNotNull(message)
        Assert.assertEquals(bundle, message?.getPushBundle())
    }

    /**
     * Test fromIntent returns null if its unable to find a bundle extra under PushManager.EXTRA_PUSH_MESSAGE_BUNDLE.
     */
    @Test
    fun testFromIntentInvalid() {
        Assert.assertNull(PushMessage.fromIntent(null))
        Assert.assertNull(PushMessage.fromIntent(Intent()))
        Assert.assertNull(
            PushMessage.fromIntent(
                Intent().putExtra(
                    PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, "not a bundle"
                )
            )
        )
    }

    /**
     * Test that if the push contains any key that starts with "com.urbanairship" containsAirshipKeys
     * returns true.
     */
    @Test
    fun testContainsAirshipKeys() {
        val bundle = bundleOf("cool" to "story")
        var message = PushMessage(bundle)
        Assert.assertFalse(message.containsAirshipKeys())

        bundle.putString("com.urbanairship.whatever", "value")
        message = PushMessage(bundle)
        Assert.assertTrue(message.containsAirshipKeys())
    }

    @Test
    fun testIsAirshipPush() {
        val bundle = bundleOf("cool" to "story")

        var message = PushMessage(bundle)
        Assert.assertFalse(message.isAirshipPush)

        bundle.putString(PushMessage.EXTRA_SEND_ID, "value")
        message = PushMessage(bundle)
        Assert.assertTrue(message.isAirshipPush)
    }

    @Test
    fun testIsAccengageVisiblePush() {
        val bundle = bundleOf("cool" to "story")

        var message = PushMessage(bundle)
        Assert.assertFalse(message.containsAirshipKeys())

        bundle.putString("a4scontent", "value")
        message = PushMessage(bundle)
        Assert.assertTrue(message.isAccengageVisiblePush)
    }

    @Test
    fun testIsAccengagePush() {
        val bundle = bundleOf("cool" to "story")

        var message = PushMessage(bundle)
        Assert.assertFalse(message.containsAirshipKeys())

        bundle.putString("a4sid", "value")
        message = PushMessage(bundle)
        Assert.assertTrue(message.isAccengagePush)
    }
}
