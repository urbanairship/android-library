/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;

import com.urbanairship.BaseTestCase;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SubjectTest extends BaseTestCase {

    private Map<String, Boolean> resultMap;

    @Before
    public void setUp() {
        resultMap = new HashMap<>();
        resultMap.put("next", false);
        resultMap.put("complete", false);
        resultMap.put("error", false);
    }

    @Test
    public void testSubject() throws Exception {
        Subject<Integer> subject = Subject.create();
        subject.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(@NonNull Integer value) {
                Assert.assertEquals(value.intValue(), 3);
                resultMap.put("next", true);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }

            @Override
            public void onError(@NonNull Exception e) {
                resultMap.put("error", true);
            }
        });

        subject.onNext(3);
        subject.onCompleted();

        Assert.assertTrue(resultMap.get("next"));
        Assert.assertTrue(resultMap.get("complete"));
        Assert.assertFalse(resultMap.get("error"));
    }

    @Test
    public void testSubjectError() throws Exception {
        final Exception exception = new Exception("Oh no");

        Subject<Integer> subject = Subject.create();
        subject.subscribe(new Subscriber<Integer>() {
            @Override
            public void onNext(@NonNull Integer value) {
                resultMap.put("next", true);
            }

            @Override
            public void onCompleted() {
                resultMap.put("complete", true);
            }

            @Override
            public void onError(@NonNull Exception e) {
                Assert.assertEquals(e, exception);
                resultMap.put("error", true);
            }
        });

        subject.onError(exception);

        Assert.assertFalse(resultMap.get("next"));
        Assert.assertFalse(resultMap.get("complete"));
        Assert.assertTrue(resultMap.get("error"));
    }

}
