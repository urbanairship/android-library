/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Attributes editor tests.
 */
public class AttributeEditorTest extends BaseTestCase {
    private TestAttributeEditor editor;

    @Before
    public void setUp() {
        editor = new TestAttributeEditor();
    }


    public void testNanAndInfiniteNumberAttributes() throws NumberFormatException {
        List<AttributeMutation> mutations = new ArrayList<>();

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", Double.NaN));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", Double.POSITIVE_INFINITY));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", Double.NEGATIVE_INFINITY));

        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", Float.NaN));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", Float.POSITIVE_INFINITY));
        mutations.add(AttributeMutation.newSetAttributeMutation("expected_key", Float.NEGATIVE_INFINITY));
    }

    @Test
    public void testSetNumberAttributes() {
        int expectedInt = 11;
        long expectedLong = 11;
        float expectedFloat = 11.11f;
        double expectedDouble = 11.11;

        editor.setAttribute("expected_key0", expectedInt)
              .setAttribute("expected_key1", expectedLong)
              .setAttribute("expected_key2", expectedFloat)
              .setAttribute("expected_key3", expectedDouble)
              .apply();


        String expected =
                "[{\"action\":\"set\",\"value\":" + expectedInt +",\"key\":\"expected_key0\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"set\",\"value\":" + expectedLong + ",\"key\":\"expected_key1\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"set\",\"value\":" +  JsonValue.wrap(expectedFloat) +",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"set\",\"value\":" + expectedDouble + ",\"key\":\"expected_key3\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expected, JsonValue.wrapOpt(PendingAttributeMutation.fromAttributeMutations(editor.mutations, 0)).toString());
    }

    @Test(expected = NumberFormatException.class)
    public void testSetNaNFloat() {
        editor.setAttribute("expected_key", Float.NaN)
              .apply();
    }

    @Test(expected = NumberFormatException.class)
    public void testSetNaNDouble() {
        editor.setAttribute("expected_key", Double.NaN)
              .apply();
    }

    @Test(expected = NumberFormatException.class)
    public void testSetInfiniteDouble() {
        editor.setAttribute("expected_key", Double.POSITIVE_INFINITY)
              .apply();
    }

    @Test(expected = NumberFormatException.class)
    public void testSetNegativeInfiniteDouble() {
        editor.setAttribute("expected_key", Double.NEGATIVE_INFINITY)
              .apply();
    }

    @Test(expected = NumberFormatException.class)
    public void testSetInfiniteFloat() {
        editor.setAttribute("expected_key", Float.POSITIVE_INFINITY)
              .apply();
    }

    @Test(expected = NumberFormatException.class)
    public void testSetNegativeInfiniteFloat() {
        editor.setAttribute("expected_key", Float.NEGATIVE_INFINITY)
              .apply();
    }

    @Test
    public void testSetStringAttributes() {
        editor.setAttribute("expected_key", "expected_value")
              .setAttribute("expected_key2", "expected_value2")
              .apply();


        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"set\",\"value\":\"expected_value2\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expected, JsonValue.wrapOpt(PendingAttributeMutation.fromAttributeMutations(editor.mutations, 0)).toString());
    }


    @Test
    public void testRemoveAttributes() {
        editor.removeAttribute("expected_key")
              .removeAttribute("expected_key2")
              .apply();

        String expected = "[{\"action\":\"remove\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"remove\",\"key\":\"expected_key2\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expected, JsonValue.wrapOpt(PendingAttributeMutation.fromAttributeMutations(editor.mutations, 0)).toString());
    }

    @Test
    public void testAddAndRemoveStringAttributes() {
        editor.setAttribute("expected_key", "expected_value")
              .removeAttribute("expected_key")
              .apply();

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"remove\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expected, JsonValue.wrapOpt(PendingAttributeMutation.fromAttributeMutations(editor.mutations,0)).toString());
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

        @Override
        protected void onApply(@NonNull List<AttributeMutation> mutations) {
            this.mutations = mutations;
        }
    }
}
