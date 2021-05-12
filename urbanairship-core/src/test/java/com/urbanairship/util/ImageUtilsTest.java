package com.urbanairship.util;

import com.urbanairship.BaseTestCase;
import com.urbanairship.util.ImageUtils.Size;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link ImageUtils} tests.
 */
public class ImageUtilsTest extends BaseTestCase {

    @Test
    public void testCalculateTargetSize() {
        // Returns requested dimensions if both are non-zero
        assertEquals(new Size(10, 10), ImageUtils.calculateTargetSize(1, 1, 10, 10));
        assertEquals(new Size(20, 20), ImageUtils.calculateTargetSize(1, 2, 20, 20));

        // Requested height = 0
        assertEquals(new Size(10, 20), ImageUtils.calculateTargetSize(5, 10, 10, 0));
        assertEquals(new Size(25, 100), ImageUtils.calculateTargetSize(250, 1000, 25, 0));

        // Requested width = 0
        assertEquals(new Size(20, 10), ImageUtils.calculateTargetSize(10, 5, 0, 10));
        assertEquals(new Size(100, 25), ImageUtils.calculateTargetSize(1000, 250, 0, 25));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateTargetSizeZeroWidth() {
        assertEquals(new Size(1, 1), ImageUtils.calculateTargetSize(0, 1, 1, 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateTargetSizeZeroHeight() {
        assertEquals(new Size(1, 1), ImageUtils.calculateTargetSize(1, 0, 1, 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateTargetSizeZeroReqWidthAndReqHeight() {
        assertEquals(new Size(1, 1), ImageUtils.calculateTargetSize(1, 1, 0, 0));
    }
}
