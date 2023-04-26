/* Copyright Airship and Contributors */
package com.urbanairship.http

import android.net.Uri
import com.urbanairship.TestRequestSession
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class SuspendingRequestSessionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testRequestSession = TestRequestSession()
    private val suspendingRequestSession = testRequestSession.toSuspendingRequestSession()

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    public fun testExecute(): TestResult = runTest {
        testRequestSession.addResponse(200, "{ \"ok\": true }", mapOf("some header" to "some value"))
        val request = Request(
            url = Uri.parse("some uri"),
            method = "POST",
            body = RequestBody.Json("Some JSON"),
            headers = mapOf("foo" to "bar")
        )

        val result = suspendingRequestSession.execute(request)
        assertNull(result.exception)

        assertEquals(200, result.status)
        assertEquals("{ \"ok\": true }", result.body)
        assertEquals(mapOf("some header" to "some value"), result.headers)
    }

    @Test
    public fun testExecuteParser(): TestResult = runTest {
        testRequestSession.addResponse(200, "{ \"ok\": true }", mapOf("some header" to "some value"))
        val request = Request(
            url = Uri.parse("some uri"),
            method = "POST",
            body = RequestBody.Json("Some JSON"),
            headers = mapOf("foo" to "bar")
        )

        val result = suspendingRequestSession.execute(request) { status: Int, headers: Map<String, String>, body: String? ->
            assertEquals(200, status)
            assertEquals("{ \"ok\": true }", body)
            assertEquals(mapOf("some header" to "some value"), headers)
            "neat"
        }

        assertNull(result.exception)
        assertEquals("neat", result.value)
        assertEquals(200, result.status)
        assertEquals("{ \"ok\": true }", result.body)
        assertEquals(mapOf("some header" to "some value"), result.headers)
    }

    @Test
    public fun testExecuteException(): TestResult = runTest {
        val request = Request(
            url = Uri.parse("some uri"),
            method = "POST",
            body = RequestBody.Json("Some JSON"),
            headers = mapOf("foo" to "bar")
        )

        val result = suspendingRequestSession.execute(request) { status: Int, headers: Map<String, String>, body: String? ->
            assertEquals(200, status)
            assertEquals("{ \"ok\": true }", body)
            assertEquals(mapOf("some header" to "some value"), headers)
            throw IllegalArgumentException("neat")
        }

        assertNotNull(result.exception)

        assertNull(result.status)
        assertNull(result.body)
        assertNull(result.headers)
        assertNull(result.value)
    }

    @Test
    public fun testExecuteParseException(): TestResult = runTest {
        val request = Request(
            url = Uri.parse("some uri"),
            method = "POST",
            body = RequestBody.Json("Some JSON"),
            headers = mapOf("foo" to "bar")
        )

        val result = suspendingRequestSession.execute(request)
        assertNotNull(result.exception)

        assertNull(result.status)
        assertNull(result.body)
        assertNull(result.headers)
        assertNull(result.value)
    }
}
