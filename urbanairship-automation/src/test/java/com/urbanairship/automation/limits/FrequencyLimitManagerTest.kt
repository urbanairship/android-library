package com.urbanairship.automation.limits

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.limits.storage.FrequencyLimitDatabase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class FrequencyLimitManagerTest {
    private val clock = TestClock().apply { currentTimeMillis = 0 }
    private val db = FrequencyLimitDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val store = db.dao

    @After
    public fun after() {
        db.close()
    }

    @Test
    public fun testGetCheckerNoLimits(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))
        val checker = manager.getFrequencyChecker(listOf()).getOrThrow()
        assertNull(checker)
    }

    @Test
    public fun testSingleChecker(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val constraint = FrequencyConstraint(
            identifier = "foo",
            range = 10,
            count = 2U
        )

        manager.setConstraints(listOf(constraint))

        val constraints = store.getAllConstraints() ?: listOf()
        assertEquals(1, constraints.size)

        val checker = requireNotNull(manager.getFrequencyChecker(listOf("foo")).getOrThrow())

        assertNotNull(checker)
        assertFalse(checker.isOverLimit())
        assertTrue(checker.checkAndIncrement())

        clock.currentTimeMillis += 1
        assertFalse(checker.isOverLimit())
        assertTrue(checker.checkAndIncrement())

        // We should now be over the limit
        assertTrue(checker.isOverLimit())
        assertFalse(checker.checkAndIncrement())

        // After the range has passed we should no longer be over the limit
        clock.currentTimeMillis = 11
        assertFalse(checker.isOverLimit())

        // One more increment should push us back over the limit
        assertTrue(checker.checkAndIncrement())
        assertTrue(checker.isOverLimit())

        manager.writePendingInQueue()
        val occurrences = store.getOccurrences("foo")?.map { it.timeStamp } ?: listOf()
        assertEquals(3, occurrences.size)
        assertTrue(setOf(0L, 1L, 11L).all { occurrences.contains(it) })
    }

    @Test
    public fun testMultipleCheckers(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val constraint = FrequencyConstraint(
            identifier = "foo",
            range = 10,
            count = 2U
        )

        manager.setConstraints(listOf(constraint))

        val checker1 = requireNotNull(manager.getFrequencyChecker(listOf("foo")).getOrThrow())
        val checker2 = requireNotNull(manager.getFrequencyChecker(listOf("foo")).getOrThrow())

        val constraints = store.getAllConstraints()
        assertEquals(1, constraints?.size)

        assertFalse(checker1.isOverLimit())
        assertFalse(checker2.isOverLimit())
        assertTrue(checker1.checkAndIncrement())

        clock.currentTimeMillis += 1
        assertTrue(checker2.checkAndIncrement())

        // We should now be over the limit
        assertTrue(checker1.isOverLimit())
        assertTrue(checker2.isOverLimit())

        // After the range has passed we should no longer be over the limit
        clock.currentTimeMillis = 11
        assertFalse(checker1.isOverLimit())
        assertFalse(checker2.isOverLimit())

        // The first check and increment should succeed, and the next should put us back over the limit again
        assertTrue(checker1.checkAndIncrement())

        clock.currentTimeMillis = 1
        assertFalse(checker2.checkAndIncrement())

        manager.writePendingInQueue()
        val occurrences = store.getOccurrences("foo")?.map { it.timeStamp }?.toSet() ?: emptySet()
        assertEquals(3, occurrences.size)
        assertTrue(setOf(0L, 1, 11).all { occurrences.contains(it) })
    }

    @Test
    public fun testMultipleConstraints(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val constraint1 = FrequencyConstraint("foo", 10, 2U)
        val constraint2 = FrequencyConstraint("bar", 2, 1U)

        manager.setConstraints(listOf(constraint1, constraint2))

        val checker = requireNotNull(manager.getFrequencyChecker(listOf("foo", "bar")).getOrThrow())

        assertFalse(checker.isOverLimit())
        assertTrue(checker.checkAndIncrement())

        clock.currentTimeMillis = 1

        // We should now be violating constraint 2
        assertTrue(checker.isOverLimit())
        assertFalse(checker.checkAndIncrement())

        clock.currentTimeMillis = 3
        // We should no longer be violating constraint 2
        assertFalse(checker.isOverLimit())
        assertTrue(checker.checkAndIncrement())

        // We should now be violating constraint 1
        clock.currentTimeMillis = 9
        assertTrue(checker.isOverLimit())
        assertFalse(checker.checkAndIncrement())

        // We should now be violating neither constraint
        clock.currentTimeMillis = 11
        assertFalse(checker.isOverLimit())

        // One more increment should hit the limit
        assertTrue(checker.checkAndIncrement())
        assertTrue(checker.isOverLimit())

        manager.writePendingInQueue()
    }

    @Test
    public fun testConstraintRemovedMidCheck(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val constraint1 = FrequencyConstraint("foo", 10, 2U)
        val constraint2 = FrequencyConstraint("bar", 20, 2U)

        manager.setConstraints(listOf(constraint1, constraint2))

        val checker = requireNotNull(manager.getFrequencyChecker(listOf("foo", "bar")).getOrThrow())
        manager.setConstraints(listOf(FrequencyConstraint("bar", 10, 10U)))

        assertTrue(checker.checkAndIncrement())

        clock.currentTimeMillis = 1
        assertTrue(checker.checkAndIncrement())

        clock.currentTimeMillis = 1
        assertTrue(checker.checkAndIncrement())

        manager.writePendingInQueue()
        assertEquals(3, store.getOccurrences("bar")?.size)

        // Foo should not exist
        assertNull(store.getConstraint("foo"))


    }

    @Test
    public fun testUpdateConstraintRangeClearsOccurrences(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))

        manager.setConstraints(listOf(FrequencyConstraint("foo", 10, 2U)))

        val checker = requireNotNull(manager.getFrequencyChecker(listOf("foo")).getOrThrow())
        checker.checkAndIncrement()

        manager.setConstraints(listOf(FrequencyConstraint("foo", 20, 2U)))
        assertEquals(0, store.getOccurrences("foo")?.size)
    }

    @Test
    public fun testUpdateConstraintCountDoesNotClearCount(): TestResult = runTest {
        val manager = FrequencyLimitManager(dao = store, clock = clock, dispatcher = UnconfinedTestDispatcher(testScheduler))

        manager.setConstraints(listOf(FrequencyConstraint("foo", 10, 2U)))

        val checker = requireNotNull(manager.getFrequencyChecker(listOf("foo")).getOrThrow())
        assertTrue(checker.checkAndIncrement())

        manager.setConstraints(listOf(FrequencyConstraint("foo", 10, 3U)))

        assertEquals(1, store.getOccurrences("foo")?.size)
    }
}
