/* Copyright Airship and Contributors */
package com.urbanairship.http

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RequestRedactionTest {

    @Test
    public fun testBasicAuthRedactsPassword() {
        val rendered = RequestAuth.BasicAuth(USER, PASSWORD).toString()

        assertFalse("Password must not be rendered", rendered.contains(PASSWORD))
        assertTrue("User is useful for debugging and is not a secret", rendered.contains(USER))
    }

    @Test
    public fun testBearerTokenRedactsToken() {
        val rendered = RequestAuth.BearerToken(TOKEN).toString()

        assertFalse("Token must not be rendered", rendered.contains(TOKEN))
    }

    @Test
    public fun testRequestDoesNotRenderAuthSecret() {
        val rendered = request(auth = RequestAuth.BasicAuth(USER, PASSWORD)).toString()

        assertFalse("Password must not reach the log", rendered.contains(PASSWORD))
    }

    @Test
    public fun testRequestDoesNotRenderBearerToken() {
        val rendered = request(auth = RequestAuth.BearerToken(TOKEN)).toString()

        assertFalse("Token must not reach the log", rendered.contains(TOKEN))
    }

    @Test
    public fun testRequestDoesNotRenderBody() {
        val body = RequestBody.Json(jsonMapOf("email" to EMAIL))
        val rendered = request(body = body).toString()

        assertFalse("Body content must not reach the log", rendered.contains(EMAIL))
        assertTrue("Body type is useful for debugging", rendered.contains("Json"))
    }

    @Test
    public fun testRequestRendersHeaderNamesWithoutValues() {
        val rendered = request(headers = mapOf(HEADER_NAME to HEADER_VALUE)).toString()

        assertTrue("Header names are useful for debugging", rendered.contains(HEADER_NAME))
        assertFalse("Header values must not reach the log", rendered.contains(HEADER_VALUE))
    }

    @Test
    public fun testRequestRendersUrlAndMethod() {
        val rendered = request().toString()

        assertTrue(rendered.contains(URL))
        assertTrue(rendered.contains("POST"))
    }

    private fun request(
        auth: RequestAuth? = null,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap()
    ) = Request(
        url = URL.toUri(),
        method = "POST",
        auth = auth,
        body = body,
        headers = headers
    )

    private companion object {
        const val URL = "https://example.com/api"
        const val USER = "some-user-id"
        const val PASSWORD = "super-secret-password"
        const val TOKEN = "super-secret-token"
        const val EMAIL = "someone@example.com"
        const val HEADER_NAME = "X-UA-Appkey"
        const val HEADER_VALUE = "some-app-key"
    }
}
