/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Clock;
import com.urbanairship.util.DateUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Attributes editor tests.
 */
public class AttributeEditorTest extends BaseTestCase {
    private TestAttributeEditor editor;
    private TestClock clock = new TestClock();
    @Before
    public void setUp() {
        clock = new TestClock();
        editor = new TestAttributeEditor(clock);
    }

    @Test(expected = NumberFormatException.class)
    public void testDoubleNaN() throws NumberFormatException {
        editor.setAttribute("key", Double.NaN);
    }

    @Test(expected = NumberFormatException.class)
    public void testDoublePositiveInfinity() throws NumberFormatException {
        editor.setAttribute("key", Double.POSITIVE_INFINITY);
    }

    @Test(expected = NumberFormatException.class)
    public void testDoubleNegativeInfinity() throws NumberFormatException {
        editor.setAttribute("key", Double.NEGATIVE_INFINITY);
    }

    @Test(expected = NumberFormatException.class)
    public void testFloatNaN() throws NumberFormatException {
        editor.setAttribute("key", Float.NaN);
    }

    @Test(expected = NumberFormatException.class)
    public void testFloatPositiveInfinity() throws NumberFormatException {
        editor.setAttribute("key", Float.POSITIVE_INFINITY);
    }

    @Test(expected = NumberFormatException.class)
    public void testFloatNegativeInfinity() throws NumberFormatException {
        editor.setAttribute("key", Float.NEGATIVE_INFINITY);
    }

    @Test
    public void testAttributes() {
        clock.currentTimeMillis = 10000;
        editor.setAttribute("string", "expected_value")
              .setAttribute("long", 100L)
              .setAttribute("double", 30.13)
              .setAttribute("float", 131.2003f)
              .setAttribute("date", new Date(1561803000000L))
              .removeAttribute("remove")
              .apply();

        List<AttributeMutation> expected = new ArrayList<>();
        expected.add(AttributeMutation.newSetAttributeMutation("string", JsonValue.wrapOpt("expected_value"), 10000));
        expected.add(AttributeMutation.newSetAttributeMutation("long", JsonValue.wrapOpt(100L), 10000));
        expected.add(AttributeMutation.newSetAttributeMutation("double", JsonValue.wrapOpt(30.13), 10000));
        expected.add(AttributeMutation.newSetAttributeMutation("float", JsonValue.wrapOpt(131.2003f), 10000));
        expected.add(AttributeMutation.newSetAttributeMutation("date", JsonValue.wrapOpt(DateUtils.createIso8601TimeStamp(1561803000000L)), 10000));
        expected.add(AttributeMutation.newRemoveAttributeMutation("remove", 10000));

        assertEquals(expected, editor.mutations);
    }


    @Test
    public void testAddOrRemoveKeyTooLargeStringAttributes() {
        String tooLong = String.format("%1$" + 1025 + "s", "").replace(' ', '0');
        assertEquals(tooLong.length(), 1025 );

        editor.setAttribute(tooLong, "expected_value")
              .apply();

        assertNull(editor.mutations);

        editor.removeAttribute(tooLong)
              .apply();

        assertNull(editor.mutations);
    }

    @Test
    public void testAddValueTooLargeStringAttributes() {
        String tooLong = String.format("%1$" + 1025 + "s", "").replace(' ', '0');
        assertEquals(tooLong.length(), 1025 );

        editor.setAttribute("expected_key", tooLong)
              .apply();

        assertNull(editor.mutations);
    }

    private static class TestAttributeEditor extends AttributeEditor {
        List<AttributeMutation> mutations;

        TestAttributeEditor(Clock clock) {
            super(clock);
        }

        @Override
        protected void onApply(@NonNull List<AttributeMutation> mutations) {
            this.mutations = mutations;
        }
    }
}
