package com.urbanairship.android.layout

import android.content.Context
import android.view.View
import com.urbanairship.json.emptyJsonMap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class AirshipCustomViewManagerTest {

    private val mockContext: Context = mockk(relaxUnitFun = true)

    private val mockView: View = mockk(relaxUnitFun = true)

    private val mockHandler: AirshipCustomViewHandler = mockk(relaxUnitFun = true) {
        every { onCreateView(any(), any()) } returns mockView
    }

    private val mockArgs: AirshipCustomViewArguments = mockk(relaxUnitFun = true) {
        every { properties } returns emptyJsonMap()
    }

    @Before
    public fun setUp() {
        AirshipCustomViewManager.unregisterAll()
    }

    @Test
    public fun testRegisterHandler() {
        assertEquals(0, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.register("test", mockHandler)

        assertEquals(1, AirshipCustomViewManager.handlers.size)

        val handler = AirshipCustomViewManager.get("test")
        assertNotNull(handler)

        assertEquals(mockHandler, handler)
        assertEquals(mockView, handler?.onCreateView(mockContext, mockArgs))
    }

    @Test
    public fun testRegisterFactory() {
        assertEquals(0, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.register("test") { mockView }

        assertEquals(1, AirshipCustomViewManager.handlers.size)

        val handler = AirshipCustomViewManager.get("test")
        assertNotNull(handler)
        assertEquals(mockView, handler?.onCreateView(mockContext, mockArgs))
    }

    @Test
    public fun testUnregisterHandler() {
        assertEquals(0, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.register("test", mockHandler)
        AirshipCustomViewManager.register("test2") { mockView }

        assertEquals(2, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.unregister("test")

        assertEquals(1, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.unregister("test2")

        assertEquals(0, AirshipCustomViewManager.handlers.size)
    }

    @Test
    public fun testUnregisterAllHandlers() {
        assertEquals(0, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.register("test", mockHandler)
        AirshipCustomViewManager.register("test2") { mockView }

        assertEquals(2, AirshipCustomViewManager.handlers.size)

        AirshipCustomViewManager.unregisterAll()

        assertEquals(0, AirshipCustomViewManager.handlers.size)
    }
}
