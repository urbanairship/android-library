package com.urbanairship.preferencecenter.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Condition
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.testing.RecyclerViewItemCountAssertion.Companion.hasItemCount
import com.urbanairship.preferencecenter.testing.RecyclerViewMatcher.Companion.withRecyclerView
import com.urbanairship.preferencecenter.testing.ViewModelUtil
import com.urbanairship.preferencecenter.ui.PreferenceCenterFragment.OnDisplayPreferenceCenterListener
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.Action
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScope
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@LooperMode(LooperMode.Mode.LEGACY)
internal class PreferenceCenterFragmentTest {

    companion object {
        private const val ID = "pref-center-id"
        private const val TITLE = "Fake Preferences"
        private const val SUBTITLE = "Manage subscriptions"
        private val SECTION_BREAK_1_ID = UUID.randomUUID().toString()
        private const val SECTION_BREAK_1_LABEL = "App"
        private val SECTION_1_ID = UUID.randomUUID().toString()
        private const val SECTION_1_TITLE = "Section 1"
        private const val SECTION_1_SUBTITLE = "Section 1 Subtitle"
        private val SECTION_2_ID = UUID.randomUUID().toString()
        private const val SECTION_2_TITLE = "Section 2"
        private const val SECTION_2_SUBTITLE = "Section 2 Subtitle"
        private val PREF_1_ID = UUID.randomUUID().toString()
        private const val PREF_1_TITLE = "Preference 1"
        private const val PREF_1_SUBTITLE = "Preference 1 Subtitle"
        private const val PREF_1_SUB_ID = "sub-1"
        private val PREF_2_ID = UUID.randomUUID().toString()
        private const val PREF_2_TITLE = "Preference 2"
        private const val PREF_2_SUB_ID = "sub-2"
        private val PREF_3_ID = UUID.randomUUID().toString()
        private const val PREF_3_TITLE = "Preference 3"
        private const val PREF_3_SUB_ID = "sub-3"
        private val PREF_4_ID = UUID.randomUUID().toString()
        private const val PREF_4_TITLE = "Preference 4"
        private const val PREF_4_SUB_ID = "sub-4"

        private val CONFIG = PreferenceCenterConfig(
            id = ID,
            display = CommonDisplay(TITLE, SUBTITLE),
            sections = listOf(
                Section.SectionBreak(
                    id = SECTION_BREAK_1_ID,
                    display = CommonDisplay(SECTION_BREAK_1_LABEL),
                    conditions = emptyList()
                ),
                Section.Common(
                    id = SECTION_1_ID,
                    display = CommonDisplay(SECTION_1_TITLE, SECTION_1_SUBTITLE),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = PREF_1_ID,
                            subscriptionId = PREF_1_SUB_ID,
                            display = CommonDisplay(PREF_1_TITLE, PREF_1_SUBTITLE),
                            conditions = emptyList()
                        ),
                        Item.ChannelSubscription(
                            id = PREF_2_ID,
                            subscriptionId = PREF_2_SUB_ID,
                            display = CommonDisplay(PREF_2_TITLE),
                            conditions = emptyList()
                        )
                    ),
                    conditions = emptyList()
                ),
                Section.Common(
                    id = SECTION_2_ID,
                    display = CommonDisplay(SECTION_2_TITLE, SECTION_2_SUBTITLE),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = PREF_3_ID,
                            subscriptionId = PREF_3_SUB_ID,
                            display = CommonDisplay(PREF_3_TITLE),
                            conditions = emptyList()
                        ),
                        Item.ChannelSubscription(
                            id = PREF_4_ID,
                            subscriptionId = PREF_4_SUB_ID,
                            display = CommonDisplay(PREF_4_TITLE),
                            conditions = emptyList()
                        )
                    ),
                    conditions = emptyList()
                )
            )
        )

        // 1 description item + 1 section break item + 2 section header items + 2 preferences per section
        private const val ITEM_COUNT = 8
        private val ITEMS = CONFIG.asPrefCenterItems()
        private val STATE_CONTENT = State.Content(
            config = CONFIG,
            listItems = ITEMS,
            conditionState = Condition.State(isOptedIn = true),
            channelSubscriptions = emptySet(),
            contactSubscriptions = emptyMap(),
            title = TITLE,
            subtitle = SUBTITLE
        )
    }

    private val testScope = TestCoroutineScope()

    private val states = MutableStateFlow<State>(State.Loading)

    private val viewModel: PreferenceCenterViewModel = mock {
        on(it.states) doReturn states
    }

    private val onDisplayListener: OnDisplayPreferenceCenterListener = mock {
        on(it.onDisplayPreferenceCenter(any(), any())) doReturn false
    }

    @After
    fun tearDown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testDisplaysLoading() {
        preferenceCenter(initialState = State.Loading) {
            verifyLoading()
        }
    }

    @Test
    fun testDisplaysError() {
        preferenceCenter(initialState = State.Loading) {
            emitState(State.Error())

            verifyError()
        }
    }

    @Test
    fun testDisplaysContent() {
        preferenceCenter(initialState = State.Loading) {

            emitState(STATE_CONTENT)

            // Verify the list
            verifyContentDisplayed(ITEM_COUNT)
            // Description item
            verifyItem(0, title = TITLE, subtitle = SUBTITLE)
            // Section Break 1
            verifySectionBreak(position = 1, label = SECTION_BREAK_1_LABEL)
            // Section 1
            verifyItem(position = 2, title = SECTION_1_TITLE, subtitle = SECTION_1_SUBTITLE)
            verifyChannelSubscriptionItem(position = 3, title = PREF_1_TITLE, subtitle = PREF_1_SUBTITLE)
            verifyChannelSubscriptionItem(position = 4, title = PREF_2_TITLE)
            // Section 2
            verifyItem(position = 5, title = SECTION_2_TITLE, subtitle = SECTION_2_SUBTITLE)
            verifyChannelSubscriptionItem(position = 6, title = PREF_3_TITLE)
            verifyChannelSubscriptionItem(position = 7, title = PREF_4_TITLE)

            // Make sure the onDisplayListener was called
            verify(onDisplayListener).onDisplayPreferenceCenter(TITLE, SUBTITLE)
        }
    }

    @Test
    fun testSetsTogglesForSubscriptions() {
        val content = STATE_CONTENT.copy(channelSubscriptions = setOf(PREF_1_SUB_ID, PREF_3_SUB_ID))
        preferenceCenter(initialState = content) {
            // Sanity check
            verifyContentDisplayed(ITEM_COUNT)

            // Verify items and toggle states
            verifyChannelSubscriptionItem(position = 3, title = PREF_1_TITLE, subtitle = PREF_1_SUBTITLE, isChecked = true)
            verifyChannelSubscriptionItem(position = 4, title = PREF_2_TITLE, isChecked = false)
            verifyChannelSubscriptionItem(position = 6, title = PREF_3_TITLE, isChecked = true)
            verifyChannelSubscriptionItem(position = 7, title = PREF_4_TITLE, isChecked = false)

            // Make sure the ViewModel wasn't notified about checked changes during list setup
            verify(viewModel, never()).handle(any<Action.PreferenceItemChanged>())
        }
    }

    @Test
    fun testChannelSubscriptionItemSubscribe() {
        preferenceCenter(initialState = STATE_CONTENT) {
            // Sanity check
            verifyContentDisplayed(ITEM_COUNT)
            verifyChannelSubscriptionItem(position = 3, title = PREF_1_TITLE, subtitle = PREF_1_SUBTITLE, isChecked = false)

            // Toggle the first subscription pref item
            toggleChannelSubscriptionItem(position = 3)
            // Make sure the ViewModel was notified
            verify(viewModel).handle(argThat { action ->
                action is Action.PreferenceItemChanged && action.item.id == PREF_1_ID && action.isEnabled
            })
        }
    }

    @Test
    fun testChannelSubscriptionItemUnsubscribe() {
        val content = STATE_CONTENT.copy(channelSubscriptions = setOf(PREF_1_SUB_ID))
        preferenceCenter(initialState = content) {
            // Sanity check
            verifyContentDisplayed(ITEM_COUNT)
            verifyChannelSubscriptionItem(position = 3, title = PREF_1_TITLE, subtitle = PREF_1_SUBTITLE, isChecked = true)

            // Toggle the first subscription pref item
            toggleChannelSubscriptionItem(position = 3)
            // Make sure the View notified the ViewModel
            verify(viewModel, times(1)).handle(argThat { action ->
                action is Action.PreferenceItemChanged && action.item.id == PREF_1_ID && action.isEnabled.not()
            })
        }
    }

    /**
     * PreferenceCenterFragment test helper that calls `launchFragmentInContainer` and creates an
     * instance of `TestPreferenceCenterFragment` with the given [args].
     *
     * @param args a `Bundle` of arguments to be passed to the `Fragment`.
     * @param initialState a [PreferenceCenterViewModel.State] that will be emitted to the `Fragment` upon launch.
     * @param block a lambda containing test logic to run via `PreferenceCenterRobot`.
     *
     * @return a `PreferenceCenterRobot` instance.
     */
    private fun preferenceCenter(
        args: Bundle = bundleOf(PreferenceCenterFragment.ARG_ID to ID),
        initialState: State = State.Loading,
        block: PreferenceCenterRobot.() -> Unit
    ) {
        val scenario = launchFragmentInContainer(args, R.style.UrbanAirship_PreferenceCenter_Activity) {
            TestPreferenceCenterFragment(
                mockViewModelFactory = ViewModelUtil.createFor(viewModel),
                mockViewModelScopeProvider = { testScope }
            ).apply {
                setOnDisplayPreferenceCenterListener(onDisplayListener)
            }
        }
        PreferenceCenterRobot(states, initialState, scenario).apply(block)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
internal class PreferenceCenterRobot(
    private val states: MutableStateFlow<State>,
    initialState: State? = null,
    val scenario: FragmentScenario<TestPreferenceCenterFragment>
) {
    companion object {
        private val ID_LIST = R.id.list
        private val ID_ERROR = R.id.error
        private val ID_ERROR_IMAGE = R.id.error_image
        private val ID_ERROR_TEXT = R.id.error_text
        private val ID_LOADING = R.id.loading
        private val ID_PROGRESS = R.id.progress
        private val ID_PREF_TITLE = R.id.ua_pref_title
        private val ID_PREF_DESCRIPTION = R.id.ua_pref_description
        private val ID_PREF_SWITCH = R.id.ua_pref_widget_switch
        private val ID_PREF_CHIP = R.id.ua_pref_chip
    }

    init {
        initialState?.let(::emitState)
    }

    fun emitState(state: State) {
        states.value = state
    }

    fun verifyLoading() {
        listOf(ID_LOADING, ID_PROGRESS).forEach {
            onView(withId(it)).check(matches(isCompletelyDisplayed()))
        }

        listOf(ID_LIST, ID_ERROR).forEach {
            onView(withId(it)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    fun verifyError() {
        listOf(ID_ERROR, ID_ERROR_IMAGE, ID_ERROR_TEXT).forEach {
            onView(withId(it)).check(matches(isCompletelyDisplayed()))
        }

        onView(withId(ID_LOADING)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    fun verifyContentDisplayed(listCount: Int? = null) {
        onView(withId(ID_LIST)).check(matches(isCompletelyDisplayed()))

        listCount?.let {
            onView(withId(ID_LIST)).check(hasItemCount(it))
        }

        listOf(ID_LOADING, ID_ERROR).forEach {
            onView(withId(it)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    fun toggleChannelSubscriptionItem(position: Int) {
        scrollToPosition(position)

        onRecyclerView(position, ID_PREF_SWITCH)
            .check(matches(allOf(isClickable(), isDisplayed())))
            .perform(click())
    }

    fun verifyChannelSubscriptionItem(
        position: Int,
        title: String? = null,
        subtitle: String? = null,
        isChecked: Boolean? = null
    ) {
        verifyItem(position, title, subtitle)
        verifyItem(position, ID_PREF_SWITCH) { matches(isDisplayed()) }

        if (isChecked != null) {
            verifyItem(position, ID_PREF_SWITCH) {
                matches(if (isChecked) isChecked() else isNotChecked())
            }
        }
    }

    fun verifySectionBreak(
        position: Int,
        label: String? = null
    ) {
        verifyItem(position, ID_PREF_CHIP) {
            matches(
                if (label != null) {
                    allOf(withText(label), isDisplayed())
                } else {
                    not(isDisplayed())
                }
            )
        }
    }

    fun verifyItem(
        position: Int,
        title: String? = null,
        subtitle: String? = null
    ) {
        verifyItem(position, ID_PREF_TITLE) {
            matches(
                if (title != null) {
                    allOf(withText(title), isDisplayed())
                } else {
                    not(isDisplayed())
                }
            )
        }

        verifyItem(position, ID_PREF_DESCRIPTION) {
            matches(
                if (subtitle != null) {
                    allOf(withText(subtitle), isDisplayed())
                } else {
                    not(isDisplayed())
                }
            )
        }
    }

    private fun verifyItem(position: Int, targetViewId: Int, check: () -> ViewAssertion) {
        // Scroll to the position to ensure that the item to verify is displayed.
        scrollToPosition(position)

        onRecyclerView(position, targetViewId).check(check())
    }

    private fun scrollToPosition(position: Int) {
        onView(withId(ID_LIST)).perform(scrollToPosition<RecyclerView.ViewHolder>(position))
    }

    private fun onRecyclerView(position: Int, targetViewId: Int): ViewInteraction =
        onView(withRecyclerView(ID_LIST).atPositionOnView(position, targetViewId))
}
