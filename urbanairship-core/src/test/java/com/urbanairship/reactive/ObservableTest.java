/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.urbanairship.BaseTestCase;
import com.urbanairship.Predicate;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ObservableTest extends BaseTestCase {

    private List<Object> values;
    private Exception error;
    private Integer nexts;
    private Integer completes;
    private Integer errors;
    private HandlerThread backgroundThread;

    @Before
    public void setUp() {
        initializeValues();
        backgroundThread = new HandlerThread("test");
        backgroundThread.start();
    }

    @After
    public void tearDown() {
        backgroundThread.getLooper().quit();
        backgroundThread.quit();
    }

    public void initializeValues() {
        values = new ArrayList<>();
        nexts = 0;
        completes = 0;
        errors = 0;
    }

    public <T> void performAsserts(final Looper looper, final Exception expectedError, final List<T> expectedValues,
                                   final int nextCount, final int completeCount, final int errorCount) {
        if (looper == null) {
            Assert.assertEquals(values, expectedValues);
            Assert.assertEquals(error, expectedError);
            Assert.assertEquals(nexts.intValue(), nextCount);
            Assert.assertEquals(completes.intValue(), completeCount);
            Assert.assertEquals(errors.intValue(), errorCount);
        } else {

            while (Shadows.shadowOf(looper).getScheduler().areAnyRunnable()) {
                Shadows.shadowOf(looper).runToEndOfTasks();
            }

            Assert.assertEquals(values, expectedValues);
            Assert.assertEquals(error, expectedError);
            Assert.assertEquals(nexts.intValue(), nextCount);
            Assert.assertEquals(completes.intValue(), completeCount);
            Assert.assertEquals(errors.intValue(), errorCount);
        }
    }

    public <T> void subscribeObservable(Observable<T> observable) {
        observable.subscribe(new Subscriber<T>() {
            @Override
            public void onNext(@NonNull T value) {
                values.add(value);
                nexts++;
            }

            @Override
            public void onCompleted() {
                completes++;
            }

            @Override
            public void onError(@NonNull Exception e) {
                error = e;
                errors++;
            }
        });
    }

    public <T> void validateObservable(Observable<T> observable, final Looper looper, final Exception expectedError,
                                       final List<T> expectedValues, final int nextCount, final int completeCount, final int errorCount) {
        subscribeObservable(observable);
        performAsserts(looper, expectedError, expectedValues, nextCount, completeCount, errorCount);
    }

    public <T> void validateObservable(Observable<T> observable, List<T> expectedValues,
                                       int nextCount, int completeCount, int errorCount) {
        validateObservable(observable, null, null, expectedValues, nextCount, completeCount, errorCount);
    }

    public <T> void validateObservable(Observable<T> observable, Looper looper, List<T> expectedValues,
                                       int nextCount, int completeCount, int errorCount) {
        validateObservable(observable, looper, null, expectedValues, nextCount, completeCount, errorCount);
    }

    public <T> void validateObservable(Observable<T> observable, Exception expectedError, List<T> expectedValues,
                                       int nextCount, int completeCount, int errorCount) {
        validateObservable(observable, null, expectedError, expectedValues, nextCount, completeCount, errorCount);
    }

    @Test
    public void testJust() throws Exception {
        Observable<Integer> obs = Observable.just(3);
        validateObservable(obs, Arrays.asList(3), 1, 1, 0);
    }

    @Test
    public void testEmpty() throws Exception {
        Observable<Integer> obs = Observable.empty();
        validateObservable(obs, new ArrayList<Integer>(), 0, 1, 0);
    }

    @Test
    public void testNever() throws Exception {
        Observable<Integer> obs = Observable.never();
        validateObservable(obs, new ArrayList<Integer>(), 0, 0, 0);
    }

    @Test
    public void testError() throws Exception {
        final Exception exception = new Exception("Oh no");
        Observable<Integer> obs = Observable.error(exception);
        validateObservable(obs, exception, new ArrayList<Integer>(), 0, 0, 1);
    }

    @Test
    public void testFromCollection() throws Exception {
        List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5);
        Observable<Integer> obs = Observable.from(ints);
        validateObservable(obs, ints, 5, 1, 0);
    }

    @Test
    public void testMap() throws Exception {
        Observable<Integer> obs = Observable.from(Arrays.asList(1, 2, 3));

        Observable<String> mapped = obs.map(new Function<Integer, String>() {
            @NonNull
            @Override
            public String apply(@NonNull Integer value) {
                return value.toString();
            }
        });

        validateObservable(mapped, Arrays.asList("1", "2", "3"), 3, 1, 0);
    }

    @Test
    public void testFilter() throws Exception {
        List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5, 6);
        Observable<Integer> three = Observable.from(ints);

        Observable<Integer> filtered = three.filter(new Predicate<Integer>() {
            @Override
            public boolean apply(Integer value) {
                return value > 3;
            }
        });

        List<Integer> expectedInts = Arrays.asList(4, 5, 6);

        validateObservable(filtered, expectedInts, 3, 1, 0);
    }

    @Test
    public void testObserveOnMyLooper() throws Exception {
        Observable<Integer> three = Observable.just(3);

        Observable<Integer> myThree = three.observeOn(Schedulers.looper(Looper.myLooper()));

        validateObservable(myThree, Arrays.asList(3), 1, 1, 0);
    }

    @Test
    public void testObserveOnBackgroundLooper() throws Exception {
        Observable<Integer> three = Observable.just(3);
        Observable<Integer> backgroundThree = three.observeOn(Schedulers.looper(backgroundThread.getLooper()));

        validateObservable(backgroundThree, backgroundThread.getLooper(), Arrays.asList(3), 1, 1, 0);
    }

    @Test
    public void testDefer() throws Exception {
        final ArrayList<Integer> ints = new ArrayList<>(Arrays.asList(1, 2, 3));

        Observable<Integer> deferred = Observable.defer(new Supplier<Observable<Integer>>() {
            @NonNull
            @Override
            public Observable<Integer> apply() {
                return Observable.from(ints);
            }
        });

        ints.add(4);

        validateObservable(deferred, Arrays.asList(1, 2, 3, 4), 4, 1, 0);
    }

    @Test
    public void testMerge() throws Exception {
        Subject<Integer> first = Subject.create();
        Subject<Integer> second = Subject.create();

        Observable<Integer> merged = Observable.merge(first, second);

        subscribeObservable(merged);

        first.onNext(1);
        second.onNext(4);
        first.onNext(2);
        second.onNext(5);
        first.onNext(3);
        second.onNext(6);

        first.onCompleted();
        second.onCompleted();

        performAsserts(null, null, Arrays.asList(1, 4, 2, 5, 3, 6), 6, 1, 0);
    }

    @Test
    public void testConcat() throws Exception {
        Subject<Integer> first = Subject.create();
        Subject<Integer> second = Subject.create();

        Observable<Integer> merged = Observable.concat(first, second);

        subscribeObservable(merged);

        first.onNext(1);
        second.onNext(4);
        first.onNext(2);
        second.onNext(5);
        first.onNext(3);
        second.onNext(6);

        first.onCompleted();

        second.onNext(7);
        second.onNext(8);
        second.onNext(9);

        second.onCompleted();

        performAsserts(null, null, Arrays.asList(1, 2, 3, 7, 8, 9), 6, 1, 0);
    }

    @Test
    public void testDefaultIfEmpty() throws Exception {
        Observable<Integer> one = Observable.just(1);
        Observable<Integer> empty = Observable.empty();
        Observable<Integer> defaultOne = one.defaultIfEmpty(2);
        Observable<Integer> defaultEmpty = empty.defaultIfEmpty(2);

        validateObservable(defaultOne, Arrays.asList(1), 1, 1, 0);

        initializeValues();

        validateObservable(defaultEmpty, Arrays.asList(2), 1, 1, 0);
    }

    @Test
    public void testZip() throws Exception {
        Subject<Integer> first = Subject.create();
        Subject<Integer> second = Subject.create();

        Observable<Pair<Integer, Integer>> zipped = Observable.zip(first, second, new BiFunction<Integer, Integer, Pair<Integer, Integer>>() {
            @NonNull
            @Override
            public Pair<Integer, Integer> apply(@NonNull Integer lh, @NonNull Integer rh) {
                return new Pair<>(lh, rh);
            }
        });

        subscribeObservable(zipped);

        first.onNext(1);
        second.onNext(4);
        first.onNext(2);
        second.onNext(5);
        first.onNext(3);
        second.onNext(6);

        first.onCompleted();
        second.onCompleted();

        List<Pair<Integer, Integer>> expected = Arrays.asList(new Pair<>(1, 4), new Pair<>(2, 5), new Pair<>(3, 6));

        performAsserts(null, null, expected, 3, 1, 0);
    }

}
