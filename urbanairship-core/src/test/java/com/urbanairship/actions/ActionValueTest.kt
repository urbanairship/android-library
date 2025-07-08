/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActionValueTest {

    /**
     * Test saving and reading a ActionValue from a parcel.
     */
    @Test
    public fun testParcelable() {
        val jsonValue = JsonValue.wrap(arrayOf<Any>("value", 1, "another-value"))
        val actionValue = ActionValue(jsonValue)

        // Write the push message to a parcel
        val parcel = Parcel.obtain()
        actionValue.writeToParcel(parcel, 0)

        // Reset the parcel so we can read it
        parcel.setDataPosition(0)

        // Create the message from the parcel
        val fromParcel = ActionValue.CREATOR.createFromParcel(parcel)

        // Validate the data
        assertEquals(actionValue, fromParcel)
    }
}
