/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;

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

    @Test
    public void testSetAttributes() {
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
    public void testAddAndRemoveAttributes() {
        editor.setAttribute("expected_key", "expected_value")
              .removeAttribute("expected_key")
              .apply();

        String expected = "[{\"action\":\"set\",\"value\":\"expected_value\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}," +
                "{\"action\":\"remove\",\"key\":\"expected_key\",\"timestamp\":\"1970-01-01T00:00:00\"}]";

        assertEquals(expected, JsonValue.wrapOpt(PendingAttributeMutation.fromAttributeMutations(editor.mutations,0)).toString());
    }

    @Test
    public void testAddOrRemoveKeyTooLargeAttributes() {
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
    public void testAddValueTooLargeAttributes() {
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
