/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.urbanairship.BaseTestCase;
import com.urbanairship.Predicate;

import junit.framework.Assert;

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

    private Map<String, Boolean> resultMap;
    private List<Object> values;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Before
    public void setUp() {
        resultMap = new HashMap<>();
        resultMap.put("next", false);
        resultMap.put("complete", false);
        resultMap.put("error", false);

        values = new ArrayList<>();

        backgroundThread = new HandlerThread("test");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @After
    public void tearDown() {
        backgroundThread.getLooper().quit();
        backgroundThread.quit();
    }

    @Test
    public void testJust() throws Exception {
        Observable<Integer> obs = Observable.just(3);
        obs.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                Assert.assertEquals(value.intValue(), 3);
                resultMap.put("next", true);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }
        });

        Assert.assertTrue(resultMap.get("next"));
        Assert.assertTrue(resultMap.get("complete"));
        Assert.assertFalse(resultMap.get("error"));
    }

    @Test
    public void testEmpty() throws Exception {
        Observable<Integer> obs = Observable.empty();
        obs.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                resultMap.put("next", true);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }
        });

        Assert.assertFalse(resultMap.get("next"));
        Assert.assertTrue(resultMap.get("complete"));
    }

    @Test
    public void testNever() throws Exception {
        Observable<Integer> obs = Observable.never();
        obs.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                resultMap.put("next", true);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }
        });

        Assert.assertFalse(resultMap.get("next"));
        Assert.assertFalse(resultMap.get("complete"));
    }

    @Test
    public void testError() throws Exception {
        final Exception exception = new Exception("Oh no");
        Observable<Integer> obs = Observable.error(exception);
        obs.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                resultMap.put("next", true);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }

            @Override
            public void onError(Exception e) {
                Assert.assertEquals(e, exception);
                resultMap.put("error", true);
            }
        });

        Assert.assertFalse(resultMap.get("next"));
        Assert.assertFalse(resultMap.get("complete"));
        Assert.assertTrue(resultMap.get("error"));
    }

    @Test
    public void testFromCollection() throws  Exception {
        List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5);
        Observable<Integer> obs = Observable.from(ints);

        obs.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                resultMap.put("next", true);
                values.add(value);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }

            @Override
            public void onError(Exception e) {
                resultMap.put("error", true);
            }
        });

        Assert.assertTrue(resultMap.get("next"));
        Assert.assertTrue(resultMap.get("complete"));
        Assert.assertFalse(resultMap.get("error"));

        Assert.assertEquals(values, ints);
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

        mapped.subscribe(new Subscriber<String>() {
            @Override
            public void onNext(String value) {
                resultMap.put("next", true);
                values.add(value);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }

            @Override
            public void onError(Exception e) {
                super.onError(e);
            }
        });

        Assert.assertTrue(resultMap.get("next"));
        Assert.assertTrue(resultMap.get("complete"));
        Assert.assertFalse(resultMap.get("error"));
        Assert.assertEquals(values, Arrays.asList("1", "2", "3"));
    }

    @Test
    public void testFilter() throws Exception {
        Observable<Integer> three = Observable.from(Arrays.asList(1,2,3,4,5,6));

        Observable<Integer> filtered = three.filter(new Predicate<Integer>() {
            @Override
            public boolean apply(Integer value) {
                return value > 3;
            }
        });

        filtered.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                values.add(value);
            }
        });

        Assert.assertEquals(values, Arrays.asList(4, 5, 6));
    }

    @Test
    public void testObserveOn() throws Exception {
        Observable<Integer> three = Observable.just(3);

        Observable<Integer> immediateThree = three.observeOn(Schedulers.looper(Looper.myLooper()));

        immediateThree.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                Assert.assertEquals(value.intValue(), 3);
                resultMap.put("next", true);
            }
        });

        Assert.assertTrue(resultMap.get("next"));

        ShadowLooper shadowLooper = Shadows.shadowOf(backgroundThread.getLooper());
        Observable<Integer> runLoopThree = three.observeOn(Schedulers.looper(backgroundThread.getLooper()));

        resultMap.put("next", false);

        runLoopThree.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                Assert.assertEquals(value.intValue(), 3);
                resultMap.put("next", true);
            }
        });

        while (shadowLooper.getScheduler().areAnyRunnable()) {
            shadowLooper.runToEndOfTasks();
        }

        Assert.assertTrue(resultMap.get("next"));
    }

    @Test
    public void testMerge() throws  Exception {
        Observable<Integer> first = Observable.from(Arrays.asList(1, 2, 3));
        Observable<Integer> second = Observable.from(Arrays.asList(4, 5, 6));

        merge(first, second).subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(Integer value) {
                values.add(value);
            }

            @Override
            public void onCompleted() {
                Assert.assertFalse(resultMap.get("complete"));
                resultMap.put("complete", true);
                super.onCompleted();
            }
        });

        Assert.assertEquals(values, Arrays.asList(1, 2, 3, 4, 5, 6));
        Assert.assertTrue(resultMap.get("complete"));
    }
}
