package com.urbanairship.iam.adapter

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class CustomDisplayAdapterWrapperTest {
    private val adapterIsReady = MutableStateFlow(true)
    private val displayAdapter: CustomDisplayAdapter.SuspendingAdapter = mockk {
        every { isReady } returns  adapterIsReady
    }

    private val wrapper = CustomDisplayAdapterWrapper(displayAdapter)

    @Test
    public fun testIsReady(): TestResult = runTest {
        adapterIsReady.value = false
        assertFalse(wrapper.isReady.value)

        adapterIsReady.value = true
        assertTrue(wrapper.isReady.value)
    }

}
