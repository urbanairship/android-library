/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class PagerAdapterTest {

    private val model: com.urbanairship.android.layout.model.PagerModel = mockk(relaxed = true)
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true)

    private lateinit var adapter: PagerAdapter
    private lateinit var observer: RecordingObserver

    @Before
    public fun setup() {
        adapter = PagerAdapter(model, viewEnvironment)
        observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)
    }

    @Test
    public fun testTailChangeDoesNotRebindVisiblePage() {
        val page0 = mockk<AnyModel>(relaxed = true)
        val page1 = mockk<AnyModel>(relaxed = true)
        val page2 = mockk<AnyModel>(relaxed = true)

        adapter.setItems(listOf(page0, page1))
        observer.reset()

        // Branching re-evaluation: page 0 keeps its identity, tail flips from page1 to page2.
        adapter.setItems(listOf(page0, page2))

        assertFalse("Should not trigger a full dataset reset", observer.fullChangeCount > 0)
        assertFalse(
            "Visible page at position 0 must not be touched",
            observer.changedPositions.contains(0) ||
                observer.removedPositions.contains(0) ||
                observer.insertedPositions.contains(0)
        )
        assertEquals(listOf(page0, page2), adapter.currentItems())
    }

    @Test
    public fun testIdenticalListIsNoOp() {
        val page0 = mockk<AnyModel>(relaxed = true)
        val page1 = mockk<AnyModel>(relaxed = true)
        val items = listOf(page0, page1)

        adapter.setItems(items)
        observer.reset()

        adapter.setItems(listOf(page0, page1))

        assertFalse("Identical list should emit no events", observer.hasAnyEvent)
        assertEquals(items, adapter.currentItems())
    }

    @Test
    public fun testAppendedTailPageIsInserted() {
        val page0 = mockk<AnyModel>(relaxed = true)
        val page1 = mockk<AnyModel>(relaxed = true)

        adapter.setItems(listOf(page0))
        observer.reset()

        adapter.setItems(listOf(page0, page1))

        assertFalse("Should not trigger a full dataset reset", observer.fullChangeCount > 0)
        assertTrue("New tail page should be inserted", observer.insertedPositions.contains(1))
        assertFalse("Existing page 0 must not be touched", observer.changedPositions.contains(0))
        assertEquals(listOf(page0, page1), adapter.currentItems())
    }

    @Test
    public fun testRemovedTailPageIsRemoved() {
        val page0 = mockk<AnyModel>(relaxed = true)
        val page1 = mockk<AnyModel>(relaxed = true)

        adapter.setItems(listOf(page0, page1))
        observer.reset()

        adapter.setItems(listOf(page0))

        assertFalse("Should not trigger a full dataset reset", observer.fullChangeCount > 0)
        assertTrue("Removed tail page should be reported", observer.removedPositions.contains(1))
        assertFalse("Existing page 0 must not be touched", observer.changedPositions.contains(0))
        assertEquals(listOf(page0), adapter.currentItems())
    }

    private fun PagerAdapter.currentItems(): List<AnyModel> =
        (0 until itemCount).map { getItemAtPosition(it) }

    private class RecordingObserver : RecyclerView.AdapterDataObserver() {
        var fullChangeCount = 0
        val changedPositions = mutableSetOf<Int>()
        val insertedPositions = mutableSetOf<Int>()
        val removedPositions = mutableSetOf<Int>()

        val hasAnyEvent: Boolean
            get() = fullChangeCount > 0 ||
                changedPositions.isNotEmpty() ||
                insertedPositions.isNotEmpty() ||
                removedPositions.isNotEmpty()

        fun reset() {
            fullChangeCount = 0
            changedPositions.clear()
            insertedPositions.clear()
            removedPositions.clear()
        }

        override fun onChanged() {
            fullChangeCount++
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            for (i in positionStart until positionStart + itemCount) {
                changedPositions.add(i)
            }
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            onItemRangeChanged(positionStart, itemCount)
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            for (i in positionStart until positionStart + itemCount) {
                insertedPositions.add(i)
            }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            for (i in positionStart until positionStart + itemCount) {
                removedPositions.add(i)
            }
        }
    }
}
