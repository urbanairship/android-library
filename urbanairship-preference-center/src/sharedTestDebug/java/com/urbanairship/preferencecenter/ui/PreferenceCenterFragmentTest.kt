package com.urbanairship.preferencecenter.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.urbanairship.preferencecenter.R
import com.urbanairship.preferencecenter.data.CommonDisplay
import com.urbanairship.preferencecenter.data.Item
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.Section
import com.urbanairship.preferencecenter.testing.RecyclerViewItemCountAssertion.Companion.hasItemCount
import com.urbanairship.preferencecenter.testing.RecyclerViewMatcher.Companion.withRecyclerView
import com.urbanairship.preferencecenter.testing.ViewModelUtil
import com.urbanairship.preferencecenter.ui.PreferenceCenterViewModel.State
import kotlinx.coroutines.flow.MutableStateFlow
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
internal class PreferenceCenterFragmentTest {

    companion object {
        private const val ID = "pref-center-id"
        private const val TITLE = "Fake Preferences"
        private const val SECTION_1_TITLE = "Section 1"
        private const val SECTION_1_SUBTITLE = "Section 1 Subtitle"
        private const val SECTION_2_TITLE = "Section 2"
        private const val SECTION_2_SUBTITLE = "Section 2 Subtitle"
        private const val PREF_1_TITLE = "Preference 1"
        private const val PREF_1_SUBTITLE = "Preference 1 Subtitle"
        private const val PREF_2_TITLE = "Preference 2"
        private const val PREF_3_TITLE = "Preference 3"
        private const val PREF_4_TITLE = "Preference 4"

        private val CONFIG = PreferenceCenterConfig(
            id = ID,
            display = CommonDisplay(TITLE),
            sections = listOf(
                Section.Common(
                    id = "section-id-1",
                    display = CommonDisplay(SECTION_1_TITLE, SECTION_1_SUBTITLE),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "sub-1",
                            subscriptionId = "sub-1-id",
                            display = CommonDisplay(PREF_1_TITLE, PREF_1_SUBTITLE)
                        ),
                        Item.ChannelSubscription(
                            id = "sub-2",
                            subscriptionId = "sub-2-id",
                            display = CommonDisplay(PREF_2_TITLE)
                        )
                    )
                ),
                Section.Common(
                    id = "section-id-2",
                    display = CommonDisplay(SECTION_2_TITLE, SECTION_2_SUBTITLE),
                    items = listOf(
                        Item.ChannelSubscription(
                            id = "sub-3",
                            subscriptionId = "sub-3-id",
                            display = CommonDisplay(PREF_3_TITLE)
                        ),
                        Item.ChannelSubscription(
                            id = "sub-4",
                            subscriptionId = "sub-4-id",
                            display = CommonDisplay(PREF_4_TITLE)
                        )
                    )
                )
            )
        )

        private const val ITEM_COUNT = 6 // 2 section header items + 2 preferences per section

        private val STATE_CONTENT = State.Content(TITLE, CONFIG.asPrefCenterItems())
    }

    private val states = MutableStateFlow<State>(State.Loading)

    private val viewModel: PreferenceCenterViewModel = mock {
        on(it.states) doReturn states
    }

    @Test
    fun testDisplaysContent() {
        preferenceCenter(initialState = STATE_CONTENT) {

            verifyActivityTitle(TITLE)

            verifyListCount(ITEM_COUNT)

            verifyItem(position = 0, title = SECTION_1_TITLE, subtitle = SECTION_1_SUBTITLE)

            verifyItem(position = 1, title = PREF_1_TITLE, subtitle = PREF_1_SUBTITLE)

            verifyItem(position = 2, title = PREF_2_TITLE)

            verifyItem(position = 3, title = SECTION_2_TITLE, subtitle = SECTION_2_SUBTITLE)

            verifyItem(position = 4, title = PREF_3_TITLE)

            verifyItem(position = 5, title = PREF_4_TITLE)
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
    ): PreferenceCenterRobot {
        val scenario = launchFragmentInContainer(args, R.style.Theme_MaterialComponents_Light) {
            TestPreferenceCenterFragment(mockViewModelFactory = ViewModelUtil.createFor(viewModel))
        }
        return PreferenceCenterRobot(states, initialState, scenario).apply(block)
    }
}

@Suppress("MemberVisibilityCanBePrivate")
private class PreferenceCenterRobot(
    val states: MutableStateFlow<State>,
    initialState: State? = null,
    val scenario: FragmentScenario<TestPreferenceCenterFragment>
) {
    companion object {
        const val ID_LIST = android.R.id.list
        val ID_PREF_TITLE = R.id.ua_pref_title
        val ID_PREF_DESCRIPTION = R.id.ua_pref_description
    }

    init {
        initialState?.let(::moveToViewState)
    }

    fun moveToViewState(state: State) {
        states.value = state
    }

    fun verifyActivityTitle(title: String) {
        scenario.onFragment { fragment ->
            assertThat(fragment.activity?.title).isEqualTo(title)
        }
    }

    fun verifyListCount(count: Int) {
        onView(withId(ID_LIST)).check(hasItemCount(count))
    }

    fun verifyItem(
        position: Int,
        title: String? = null,
        subtitle: String? = null
    ) {
        verifyItem(position, ID_PREF_TITLE) {
            matches(
                if (title != null) {
                    withText(title)
                } else {
                    not(isDisplayed())
                }
            )
        }

        verifyItem(position, ID_PREF_DESCRIPTION) {
            matches(
                if (subtitle != null) {
                    withText(subtitle)
                } else {
                    not(isDisplayed())
                }
            )
        }
    }

    fun verifyItem(position: Int, targetViewId: Int, check: () -> ViewAssertion) {
        // Scroll to the position to ensure that the item to verify is displayed.
        onView(withId(ID_LIST)).perform(scrollToPosition<RecyclerView.ViewHolder>(position))

        onView(withRecyclerView(ID_LIST).atPositionOnView(position, targetViewId)).check(check())
    }
}
