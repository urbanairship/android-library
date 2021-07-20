/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;
import com.urbanairship.util.Clock;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

public class SubscriptionListEditorTest extends BaseTestCase {

    private TestSubscriptionListEditor editor;

    @Before
    public void setUp() {
        TestClock clock = new TestClock();
        clock.currentTimeMillis = 0;

        editor = new TestSubscriptionListEditor(clock);
    }

    @Test
    public void testSubscribeUnsubscribeEmptyListIds() {
        editor.subscribe("")
              .subscribe("   ")
              .unsubscribe("")
              .unsubscribe("   ")
              .apply();

        assert(editor.collapsedMutations.isEmpty());
    }

    @Test
    public void testCollapseMutations() {
        editor.subscribe("foo")
              .subscribe("bar")
              .unsubscribe("foo")
              .unsubscribe("bar")
              .subscribe("baz")
              .apply();

        List<SubscriptionListMutation> expected = Arrays.asList(
                SubscriptionListMutation.newUnsubscribeMutation("foo", 0L),
                SubscriptionListMutation.newUnsubscribeMutation("bar", 0L),
                SubscriptionListMutation.newSubscribeMutation("baz", 0L)
        );

        assertEquals(expected, editor.collapsedMutations);
    }

    @Test
    public void testSubscribeUnsubscribeLists() {
        Set<String> subscribes = new LinkedHashSet<String>(3) {{
            add("one");
            add("two");
            add("three");
        }};

        Set<String> unsubscribes = new LinkedHashSet<String>(3) {{
            add("a");
            add("b");
            add("c");
        }};

        editor.subscribe(subscribes)
              .unsubscribe(unsubscribes)
              .apply();

        List<SubscriptionListMutation> expected = Arrays.asList(
                SubscriptionListMutation.newSubscribeMutation("one", 0L),
                SubscriptionListMutation.newSubscribeMutation("two", 0L),
                SubscriptionListMutation.newSubscribeMutation("three", 0L),
                SubscriptionListMutation.newUnsubscribeMutation("a", 0L),
                SubscriptionListMutation.newUnsubscribeMutation("b", 0L),
                SubscriptionListMutation.newUnsubscribeMutation("c", 0L)
        );

        assertEquals(expected, editor.collapsedMutations);
    }

    private static class TestSubscriptionListEditor extends SubscriptionListEditor {

        TestSubscriptionListEditor(Clock clock) {
            super(clock);
        }

        List<SubscriptionListMutation> collapsedMutations;

        @Override
        protected void onApply(@NonNull List<SubscriptionListMutation> collapsedMutations) {
            this.collapsedMutations = collapsedMutations;
        }
    }
}
