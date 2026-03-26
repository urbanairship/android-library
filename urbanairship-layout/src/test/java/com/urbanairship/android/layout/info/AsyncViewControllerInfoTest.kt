package com.urbanairship.android.layout.info

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.property.ViewType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AsyncViewControllerInfoTest {

    @Test
    public fun parsesMinimalContentRequestAndDefaultRetry() {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "async-1",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "content",
                    "url": "https://example.com/view.json"
                }
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as AsyncViewControllerInfo

        assertEquals(ViewType.ASYNC_VIEW_CONTROLLER, info.type)
        assertEquals("async-1", info.identifier)
        assertEquals(ViewType.EMPTY_VIEW, info.placeholder.type)
        assertEquals(AsyncViewControllerInfo.Request.Type.CONTENT, info.request.type)
        assertNull(info.request.auth)
        assertEquals("https://example.com/view.json", info.request.url)
        assertEquals(3, info.retryPolicy.maxRetries)
        assertEquals(1.seconds, info.retryPolicy.initialBackoff)
        assertEquals(10.seconds, info.retryPolicy.maxBackoff)
    }

    @Test
    public fun parsesOptionalAuth() {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "id",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "content",
                    "auth": "channel",
                    "url": "https://example.com/a"
                }
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as AsyncViewControllerInfo

        assertEquals(AsyncViewControllerInfo.Request.Auth.CHANNEL, info.request.auth)
    }

    @Test
    public fun parsesRetryOverrides() {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "id",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "content",
                    "url": "https://example.com/a"
                },
                "retry": {
                    "max_retries": 0,
                    "initial_backoff_seconds": 2,
                    "max_backoff_seconds": 30
                }
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as AsyncViewControllerInfo

        assertEquals(0, info.retryPolicy.maxRetries)
        assertEquals(2.seconds, info.retryPolicy.initialBackoff)
        assertEquals(30.seconds, info.retryPolicy.maxBackoff)
    }

    @Test
    public fun requestTypeAndAuthAreCaseInsensitive() {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "id",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "CONTENT",
                    "auth": "App",
                    "url": "https://example.com/a"
                }
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as AsyncViewControllerInfo

        assertEquals(AsyncViewControllerInfo.Request.Type.CONTENT, info.request.type)
        assertEquals(AsyncViewControllerInfo.Request.Auth.APP, info.request.auth)
    }

    @Test(expected = JsonException::class)
    public fun unknownRequestTypeThrows() {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "id",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "unknown_kind",
                    "url": "https://example.com/a"
                }
            }
        """

        ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap())
    }

    @Test(expected = JsonException::class)
    public fun unknownAuthThrows() {
        val json = """
            {
                "type": "async_view_controller",
                "identifier": "id",
                "placeholder": { "type": "empty_view" },
                "request": {
                    "type": "content",
                    "auth": "nope",
                    "url": "https://example.com/a"
                }
            }
        """

        ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap())
    }
}
