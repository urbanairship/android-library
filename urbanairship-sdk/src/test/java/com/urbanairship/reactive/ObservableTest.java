/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.Predicate;

import junit.framework.Assert;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;

import static com.urbanairship.reactive.Observable.merge;

public class ObservableTest extends BaseTestCase {

    private Map<String, Integer> resultMap;
    private List<Object> values;
    private Exception error;
    private Integer nexts;
    private Integer completes;
    private Integer errors;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Before
    public void setUp() {
        values = new ArrayList<>();
        nexts = 0;
        completes = 0;
        errors = 0;

        backgroundThread = new HandlerThread("test");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @After
    public void tearDown() {
        backgroundThread.getLooper().quit();
        backgroundThread.quit();
    }

    public <T> void validateObservable(Observable<T> observable, Looper looper, Exception expectedError,
                                       List<T> expectedValues, int nextCount, int completeCount, int errorCount) {
        observable.subscribe(new Subscriber<T>() {
            @Override
            public void onNext(T value) {
                values.add(value);
                nexts++;
            }

            @Override
            public void onCompleted() {
                completes++;
            }

            @Override
            public void onError(Exception e) {
                error = e;
                errors++;
            }
        });

        if (looper == null) {
            Assert.assertEquals(values, expectedValues);
            Assert.assertEquals(error, expectedError);
            Assert.assertEquals(nexts.intValue(), nextCount);
            Assert.assertEquals(completes.intValue(), completeCount);
            Assert.assertEquals(errors.intValue(), errorCount);
        } else {
            ShadowLooper shadowLooper = Shadows.shadowOf(looper);
            while(shadowLooper.getScheduler().areAnyRunnable()) {
                shadowLooper.runToEndOfTasks();
            }

            Assert.assertEquals(values, expectedValues);
            Assert.assertEquals(error, expectedError);
            Assert.assertEquals(nexts.intValue(), nextCount);
            Assert.assertEquals(completes.intValue(), completeCount);
            Assert.assertEquals(errors.intValue(), errorCount);
        }
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
    public void testFromCollection() throws  Exception {
        List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5);
        Observable<Integer> obs = Observable.from(ints);
        validateObservable(obs, ints, 5, 1, 0);
    }

    @Test
    public void testMap() throws Exception {
        Observable<Integer> obs = Observable.from(Arrays.asList(1, 2, 3));

        Observable<String> mapped = obs.map(new Function<Integer, String>() {
            @Override
            public String apply(Integer value) {
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
    public void testMerge() throws  Exception {
        Observable<Integer> first = Observable.from(Arrays.asList(1, 2, 3));
        Observable<Integer> second = Observable.from(Arrays.asList(4, 5, 6));
        Observable<Integer> merged = Observable.merge(first, second);

        validateObservable(merged, Arrays.asList(1, 2, 3, 4, 5, 6), 6, 1, 0);
    }
}
