package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CustomDisplayAdapterWrapperTest {
    private val displayAdapter: CustomDisplayAdapterInterface = mockk()
    private val wrapper = CustomDisplayAdapterWrapper(displayAdapter)

    @Test
    public fun testIsReady(): TestResult = runTest {
        coEvery { displayAdapter.getIsReady() } returns true
        assertTrue(wrapper.getIsReady())

        coEvery { displayAdapter.getIsReady() } returns false
        assertFalse(wrapper.getIsReady())
    }

    @Test
    public fun testWaitForReady(): TestResult = runTest {
        coEvery { displayAdapter.getIsReady() } returns false

        coEvery { displayAdapter.waitForReady() } answers { }

        wrapper.waitForReady()

        coVerify { displayAdapter.waitForReady() }
    }
}
