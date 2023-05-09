package com.urbanairship.preferencecenter.testing

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import com.google.common.truth.Truth.assertThat

internal class RecyclerViewItemCountAssertion private constructor(
    private val expectedCount: Int
) : ViewAssertion {

    companion object {
        fun isEmpty(): RecyclerViewItemCountAssertion =
            RecyclerViewItemCountAssertion(0)

        fun hasItemCount(expectedCount: Int): RecyclerViewItemCountAssertion =
            RecyclerViewItemCountAssertion(expectedCount)
    }

    override fun check(view: View?, noViewFoundException: NoMatchingViewException?) {
        if (noViewFoundException != null) {
            throw noViewFoundException
        }

        val recyclerView: RecyclerView = view as RecyclerView
        val adapter = recyclerView.adapter!!
        assertThat(adapter.itemCount).isEqualTo(expectedCount)
    }
}
