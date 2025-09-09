/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import com.urbanairship.json.jsonMapOf
import org.junit.Assert
import org.junit.Test

public class FieldTest {

    @Test
    public fun testFullField() {
        val name = "name"
        val label = "label"
        val value = 5

        val field = Field.newBuilder()
            .setLabel(label)
            .setName(name)
            .setValue(value)
            .build()

        val expectedJsonValue = jsonMapOf(
            "label" to label,
            "value" to value
        ).toJsonValue()

        Assert.assertEquals(name, field.name)
        Assert.assertEquals(expectedJsonValue, field.toJsonValue())
    }

    @Test
    public fun testFieldVariations() {
        Field.newBuilder().setName("name").setValue(5).build()

        Field.newBuilder().setName("name").setLabel("label").build()
    }

    @Test(expected = IllegalStateException::class)
    public fun testFieldOnlyLabel() {
        Field.newBuilder().setLabel("label").build()
    }
}
