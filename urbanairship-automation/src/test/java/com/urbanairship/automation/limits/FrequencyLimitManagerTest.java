/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits;

import android.content.Context;

import com.urbanairship.TestClock;
import com.urbanairship.automation.limits.storage.FrequencyLimitDao;
import com.urbanairship.automation.limits.storage.FrequencyLimitDatabase;
import com.urbanairship.automation.limits.storage.OccurrenceEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FrequencyLimitManagerTest {

    private FrequencyLimitDao dao;
    private TestClock clock;
    private FrequencyLimitManager limitManager;
    private ExecutorService executor;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        FrequencyLimitDatabase database = Room.inMemoryDatabaseBuilder(context, FrequencyLimitDatabase.class)
                                              .allowMainThreadQueries()
                                              .build();

        this.dao = database.getDao();
        this.clock = new TestClock();
        this.executor = Executors.newSingleThreadExecutor();
        this.limitManager = new FrequencyLimitManager(dao, clock, executor);
    }

    @Test
    public void testGetCheckerNoLimits() throws ExecutionException, InterruptedException {
        FrequencyChecker checker = limitManager.getFrequencyChecker(Collections.<String>emptyList()).get();
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());
        assertTrue(dao.getConstraints().isEmpty());
    }

    @Test
    public void testSingleChecker() throws ExecutionException, InterruptedException {
        FrequencyConstraint constraint = FrequencyConstraint.newBuilder()
                                                            .setCount(2)
                                                            .setRange(TimeUnit.MILLISECONDS, 10)
                                                            .setId("some-id")
                                                            .build();

        limitManager.updateConstraints(Collections.singletonList(constraint));

        clock.currentTimeMillis = 0;
        FrequencyChecker checker = limitManager.getFrequencyChecker(ids("some-id")).get();
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());

        clock.currentTimeMillis = 1;
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());

        assertTrue(checker.isOverLimit());
        assertFalse(checker.checkAndIncrement());

        clock.currentTimeMillis = 11;
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());
        assertTrue(checker.isOverLimit());

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<OccurrenceEntity> occurrenceEntityList = dao.getOccurrences("some-id");
        assertEquals(3, occurrenceEntityList.size());
        assertEquals(0, occurrenceEntityList.get(0).timeStamp);
        assertEquals(1, occurrenceEntityList.get(1).timeStamp);
        assertEquals(11, occurrenceEntityList.get(2).timeStamp);
    }

    @Test
    public void testMultipleCheckers() throws ExecutionException, InterruptedException {
        FrequencyConstraint constraint = FrequencyConstraint.newBuilder()
                                                            .setCount(2)
                                                            .setRange(TimeUnit.MILLISECONDS, 10)
                                                            .setId("some-id")
                                                            .build();

        limitManager.updateConstraints(Collections.singletonList(constraint));
        clock.currentTimeMillis = 0;

        FrequencyChecker checker1 = limitManager.getFrequencyChecker(ids("some-id")).get();
        FrequencyChecker checker2 = limitManager.getFrequencyChecker(ids("some-id")).get();

        assertFalse(checker1.isOverLimit());
        assertFalse(checker2.isOverLimit());

        assertTrue(checker1.checkAndIncrement());

        clock.currentTimeMillis = 1;
        assertTrue(checker2.checkAndIncrement());

        assertTrue(checker1.isOverLimit());
        assertTrue(checker2.isOverLimit());

        clock.currentTimeMillis = 11;
        assertFalse(checker1.isOverLimit());
        assertFalse(checker2.isOverLimit());

        assertTrue(checker2.checkAndIncrement());
        assertFalse(checker1.checkAndIncrement());

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<OccurrenceEntity> occurrenceEntityList = dao.getOccurrences("some-id");
        assertEquals(3, occurrenceEntityList.size());
        assertEquals(0, occurrenceEntityList.get(0).timeStamp);
        assertEquals(1, occurrenceEntityList.get(1).timeStamp);
        assertEquals(11, occurrenceEntityList.get(2).timeStamp);
    }

    @Test
    public void testMultipleConstraints() throws ExecutionException, InterruptedException {
        this.limitManager = new FrequencyLimitManager(dao, clock, new Executor() {
            @Override
            public void execute(@NonNull Runnable command) {
                command.run();
            }
        });

        List<FrequencyConstraint> constraints = new ArrayList<>();

        constraints.add(FrequencyConstraint.newBuilder()
                                           .setCount(2)
                                           .setRange(TimeUnit.MILLISECONDS, 10)
                                           .setId("foo")
                                           .build());

        constraints.add(FrequencyConstraint.newBuilder()
                                           .setCount(1)
                                           .setRange(TimeUnit.MILLISECONDS, 2)
                                           .setId("bar")
                                           .build());

        limitManager.updateConstraints(constraints);

        clock.currentTimeMillis = 0;
        FrequencyChecker checker = limitManager.getFrequencyChecker(ids("foo", "bar")).get();
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());

        clock.currentTimeMillis = 1;
        assertTrue(checker.isOverLimit());
        assertFalse(checker.checkAndIncrement());

        clock.currentTimeMillis = 3;
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());

        clock.currentTimeMillis = 9;
        assertTrue(checker.isOverLimit());
        assertFalse(checker.checkAndIncrement());

        clock.currentTimeMillis = 11;
        assertFalse(checker.isOverLimit());
        assertTrue(checker.checkAndIncrement());
        assertTrue(checker.isOverLimit());

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<OccurrenceEntity> fooList = dao.getOccurrences("foo");
        assertEquals(3, fooList.size());
        assertEquals(0, fooList.get(0).timeStamp);
        assertEquals(3, fooList.get(1).timeStamp);
        assertEquals(11, fooList.get(2).timeStamp);

        List<OccurrenceEntity> barList = dao.getOccurrences("bar");
        assertEquals(3, barList.size());
        assertEquals(0, barList.get(0).timeStamp);
        assertEquals(3, barList.get(1).timeStamp);
        assertEquals(11, barList.get(2).timeStamp);
    }

    @Test
    public void testConstraintRemovedMidCheck() throws ExecutionException, InterruptedException {
        List<FrequencyConstraint> constraints = new ArrayList<>();

        constraints.add(FrequencyConstraint.newBuilder()
                                           .setCount(2)
                                           .setRange(TimeUnit.MILLISECONDS, 10)
                                           .setId("foo")
                                           .build());

        constraints.add(FrequencyConstraint.newBuilder()
                                           .setCount(2)
                                           .setRange(TimeUnit.MILLISECONDS, 20)
                                           .setId("bar")
                                           .build());

        limitManager.updateConstraints(constraints);


        FrequencyChecker fooBarChecker = limitManager.getFrequencyChecker(ids("foo", "bar")).get();

        limitManager.updateConstraints(Collections.singletonList(FrequencyConstraint.newBuilder()
                                                                                    .setCount(2)
                                                                                    .setRange(TimeUnit.MILLISECONDS, 10)
                                                                                    .setId("bar")
                                                                                    .build()));
        clock.currentTimeMillis = 0;
        assertTrue(fooBarChecker.checkAndIncrement());
        assertTrue(fooBarChecker.checkAndIncrement());
        assertFalse(fooBarChecker.checkAndIncrement());

        assertTrue(dao.getOccurrences("foo").isEmpty());

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<OccurrenceEntity> barList = dao.getOccurrences("bar");
        assertEquals(2, barList.size());
        assertEquals(0, barList.get(0).timeStamp);
        assertEquals(0, barList.get(1).timeStamp);
    }

    @Test
    public void testUpdateConstraintRangeClearsCount() throws ExecutionException, InterruptedException {
        limitManager.updateConstraints(Collections.singletonList(FrequencyConstraint.newBuilder()
                                                                                    .setCount(2)
                                                                                    .setRange(TimeUnit.MILLISECONDS, 10)
                                                                                    .setId("foo")
                                                                                    .build()));

        FrequencyChecker checker = limitManager.getFrequencyChecker(ids("foo")).get();

        clock.currentTimeMillis = 100;
        assertTrue(checker.checkAndIncrement());

        // Update the range
        limitManager.updateConstraints(Collections.singletonList(FrequencyConstraint.newBuilder()
                                                                                    .setCount(1)
                                                                                    .setRange(TimeUnit.MILLISECONDS, 11)
                                                                                    .setId("foo")
                                                                                    .build()));

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertTrue(dao.getOccurrences("foo").isEmpty());
    }

    @Test
    public void testUpdateConstraint() throws ExecutionException, InterruptedException {
        limitManager.updateConstraints(Collections.singletonList(FrequencyConstraint.newBuilder()
                                                                                    .setCount(2)
                                                                                    .setRange(TimeUnit.MILLISECONDS, 10)
                                                                                    .setId("foo")
                                                                                    .build()));

        FrequencyChecker checker = limitManager.getFrequencyChecker(ids("foo")).get();

        clock.currentTimeMillis = 100;
        assertTrue(checker.checkAndIncrement());

        // Update the count
        limitManager.updateConstraints(Collections.singletonList(FrequencyConstraint.newBuilder()
                                                                                    .setCount(5)
                                                                                    .setRange(TimeUnit.MILLISECONDS, 10)
                                                                                    .setId("foo")
                                                                                    .build()));

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, dao.getOccurrences("foo").size());
    }

    private static Collection<String> ids(String... ids) {
        return Arrays.asList(ids);
    }
}
