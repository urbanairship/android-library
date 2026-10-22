/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui.view

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.MessageCenterTestUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class MessageListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun refreshInvokesOnRefreshedAfterMessagesLoad(): TestResult = runTest(testDispatcher) {
        val message = MessageCenterTestUtils.createMessage("one")
        val inbox: Inbox = mockk(relaxUnitFun = true) {
            every { getMessagesFlow(null) } returns flowOf(listOf(message))
            coEvery { fetchMessages() } returns true
        }
        val viewModel = MessageListViewModel(predicate = null, inbox = inbox)
        advanceUntilIdle()
        var stateWhenRefreshed: MessageListState? = null

        viewModel.refreshInbox { stateWhenRefreshed = viewModel.states.value }
        advanceUntilIdle()

        coVerify { inbox.fetchMessages() }
        val content = stateWhenRefreshed as MessageListState.Content
        assertThat(content.messages).containsExactly(message)
    }

    @Test
    public fun refreshInvokesOnRefreshedOnError(): TestResult = runTest(testDispatcher) {
        val inbox: Inbox = mockk(relaxUnitFun = true) {
            every { getMessagesFlow(null) } returns flow { throw IllegalStateException() }
            coEvery { fetchMessages() } returns false
        }
        val viewModel = MessageListViewModel(predicate = null, inbox = inbox)
        advanceUntilIdle()
        var stateWhenRefreshed: MessageListState? = null

        viewModel.refreshInbox { stateWhenRefreshed = viewModel.states.value }
        advanceUntilIdle()

        assertThat(stateWhenRefreshed).isEqualTo(MessageListState.Error)
    }
}
