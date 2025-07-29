/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.R
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActionButtonGroupsParserTest {

    private var context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Test loading a resources that is not found returns an empty map.
     */
    @Test
    fun testLoadXmlMissingResource() {
        val groups = ActionButtonGroupsParser.fromXml(context, -1)
        Assert.assertTrue(groups.isEmpty())
    }

    /**
     * Test loading groups.
     */
    @Test
    fun testDefaultGroups() {
        val groups = ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_buttons)

        // We provide 37 groups
        Assert.assertEquals(37, groups.size.toLong())

        // Verify a random group
        val yesNoGroup = groups["ua_yes_no_foreground"]
        Assert.assertEquals(2L, yesNoGroup?.notificationActionButtons?.size?.toLong())

        val yes = yesNoGroup?.notificationActionButtons?.get(0)
        Assert.assertEquals("yes", yes?.id)
        Assert.assertEquals("Yes", yes?.getLabel(context))
        Assert.assertEquals(R.drawable.ua_ic_notification_button_accept.toLong(), yes?.icon?.toLong())
        Assert.assertTrue(yes?.isForegroundAction == true)

        val no = yesNoGroup?.notificationActionButtons?.get(1)
        Assert.assertEquals("no", no?.id)
        Assert.assertEquals("No", no?.getLabel(context))
        Assert.assertEquals(R.drawable.ua_ic_notification_button_decline.toLong(), no?.icon?.toLong())
        Assert.assertFalse(no?.isForegroundAction == true)
    }
}
