/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

public class ScopedSubscriptionListEditorTest extends BaseTestCase {

    private List<ScopedSubscriptionListMutation> result;
    private TestClock clock = new TestClock();
    ScopedSubscriptionListEditor editor = new ScopedSubscriptionListEditor(clock) {
        @Override
        protected void onApply(@NonNull List<ScopedSubscriptionListMutation> mutations) {
            result = mutations;
        }
    };

    @Test
    public void testSubscribe() {
        editor.subscribe("some list", Scope.SMS).apply();
        List<ScopedSubscriptionListMutation> expected = Collections.singletonList(ScopedSubscriptionListMutation.newSubscribeMutation("some list", Scope.SMS, clock.currentTimeMillis));
        assertEquals(expected, result);
    }

    @Test
    public void testUnsubscribe() {
        editor.unsubscribe("some list", Scope.APP).apply();
        List<ScopedSubscriptionListMutation> expected = Collections.singletonList(ScopedSubscriptionListMutation.newUnsubscribeMutation("some list", Scope.APP, clock.currentTimeMillis));
        assertEquals(expected, result);
    }

    @Test
    public void testMutateSingle() {
        editor.mutate("some list", setOf(Scope.APP), false)
              .mutate("some other list", setOf(Scope.WEB), true)
              .apply();

        List<ScopedSubscriptionListMutation> expected = Arrays.asList(
                ScopedSubscriptionListMutation.newUnsubscribeMutation("some list", Scope.APP, clock.currentTimeMillis),
                ScopedSubscriptionListMutation.newSubscribeMutation("some other list", Scope.WEB, clock.currentTimeMillis)
        );

        assertEquals(expected, result);
    }

    @Test
    public void testMutateMultiple() {
        editor.mutate("some list", setOf(Scope.APP, Scope.SMS), false)
              .mutate("some other list", setOf(Scope.WEB, Scope.EMAIL), true)
              .apply();

        List<ScopedSubscriptionListMutation> expected = Arrays.asList(
            ScopedSubscriptionListMutation.newUnsubscribeMutation("some list", Scope.APP, clock.currentTimeMillis),
            ScopedSubscriptionListMutation.newUnsubscribeMutation("some list", Scope.SMS, clock.currentTimeMillis),
            ScopedSubscriptionListMutation.newSubscribeMutation("some other list", Scope.WEB, clock.currentTimeMillis),
            ScopedSubscriptionListMutation.newSubscribeMutation("some other list", Scope.EMAIL, clock.currentTimeMillis)
        );

        assertEquals(expected, result);
    }

    @Test
    public void testCollapse() {
        editor.mutate("some list", setOf(Scope.APP), false)
              .subscribe("some list", Scope.APP)
              .apply();

        List<ScopedSubscriptionListMutation> expected = Collections.singletonList(ScopedSubscriptionListMutation.newSubscribeMutation("some list", Scope.APP, clock.currentTimeMillis));
        assertEquals(expected, result);
    }

    @NonNull
    private static Set<Scope> setOf(Scope... s) {
        // Using LinkedHashSet to ensure predictable ordering of elements.
        return new LinkedHashSet<>(Arrays.asList(s));
    }
}
