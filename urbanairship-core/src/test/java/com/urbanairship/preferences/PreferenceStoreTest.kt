/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PreferenceStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PreferenceStore.inMemoryStore(context)

    @Test
    public fun stringRoundTrip() {
        val key = SyncPrefKey.string("test.string")
        store.put(key, "hi")
        assertEquals("hi", store.get(key))
    }

    @Test
    public fun booleanRoundTrip() {
        val key = SyncPrefKey.boolean("test.bool")
        store.put(key, true)
        assertEquals(true, store.get(key))

        store.put(key, false)
        assertEquals(false, store.get(key))
    }

    @Test
    public fun intRoundTrip() {
        val key = SyncPrefKey.int("test.int")
        store.put(key, 42)
        assertEquals(42, store.get(key))
    }

    @Test
    public fun longRoundTrip() {
        val key = SyncPrefKey.long("test.long")
        store.put(key, 9_000_000_000L)
        assertEquals(9_000_000_000L, store.get(key))
    }

    @Test
    public fun jsonRoundTrip() {
        val key = SyncPrefKey.json("test.json")
        val value = jsonMapOf("a" to 1, "b" to "two").toJsonValue()

        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    @Test
    public fun jsonSerializableRoundTrip() {
        val key = SyncPrefKey.jsonSerializable("test.payload", Payload::fromJson)
        val value = Payload(id = "abc", count = 7)

        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    @Test
    public fun customRoundTrip() {
        val key = SyncPrefKey.custom(
            name = "test.custom",
            serialize = { v: Pair<String, Int> -> "${v.first}|${v.second}" },
            deserialize = { stored ->
                val parts = stored.split("|", limit = 2)
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return@custom null) else null
            }
        )

        store.put(key, "foo" to 3)
        assertEquals("foo" to 3, store.get(key))
    }

    @Test
    public fun unsetKeyReturnsNull() {
        val key = SyncPrefKey.boolean("test.never.set")
        assertNull(store.get(key))
    }

    @Test
    public fun putNullRemoves() {
        val key = SyncPrefKey.string("test.removable")
        store.put(key, "value")
        assertTrue(store.isSet(key))

        store.put(key, null)
        assertFalse(store.isSet(key))
        assertNull(store.get(key))
    }

    @Test
    public fun explicitRemove() {
        val key = SyncPrefKey.int("test.explicit.remove")
        store.put(key, 5)
        assertTrue(store.isSet(key))

        store.remove(key)
        assertFalse(store.isSet(key))
        assertNull(store.get(key))
    }

    @Test
    public fun isSetReflectsState() {
        val key = SyncPrefKey.boolean("test.is.set")
        assertFalse(store.isSet(key))

        store.put(key, false)
        assertTrue(store.isSet(key))
    }

    @Test
    public fun corruptStoredValueDeserializesToNull() {
        // Write a non-integer string under an int key directly via the underlying store.
        val key = SyncPrefKey.int("test.corrupt")
        store.sync.put(key.name, "not-a-number")

        assertTrue(store.isSet(key))
        assertNull(store.get(key))
    }

    @Test
    public fun corruptJsonDeserializesToNull() {
        val key = SyncPrefKey.json("test.bad.json")
        store.sync.put(key.name, "{not valid json")

        assertNull(store.get(key))
    }

    @Test
    public fun throwingDeserializerYieldsNull() {
        val key = SyncPrefKey.custom<String>(
            name = "test.throws.on.read",
            serialize = { it },
            deserialize = { error("nope") }
        )

        store.sync.put(key.name, "anything")
        assertNull(store.get(key))
    }

    @Test
    public fun throwingSerializerDropsWrite() {
        val key = SyncPrefKey.custom<String>(
            name = "test.throws.on.write",
            serialize = { error("nope") },
            deserialize = { it }
        )

        store.put(key, "anything")
        assertFalse(store.isSet(key))
    }

    @Test
    public fun asyncRoundTrip(): Unit = runTest {
        val key = AsyncPrefKey.string("test.async.string")

        store.put(key, "async-value")
        assertEquals("async-value", store.get(key))

        store.remove(key)
        assertFalse(store.isSet(key))
    }

    @Test
    public fun asyncJsonSerializableRoundTrip(): Unit = runTest {
        val key = AsyncPrefKey.jsonSerializable("test.async.payload", Payload::fromJson)
        val value = Payload(id = "async", count = 99)

        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    private data class Payload(val id: String, val count: Int) : com.urbanairship.json.JsonSerializable {
        override fun toJsonValue(): JsonValue = jsonMapOf("id" to id, "count" to count).toJsonValue()

        companion object {
            fun fromJson(value: JsonValue): Payload {
                val map = value.requireMap()
                return Payload(
                    id = map.require("id").requireString(),
                    count = map.require("count").getInt(0)
                )
            }
        }
    }
}
