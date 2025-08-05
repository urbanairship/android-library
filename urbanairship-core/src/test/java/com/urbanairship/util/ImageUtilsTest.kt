package com.urbanairship.util

import com.urbanairship.util.ImageUtils.calculateTargetSize
import org.junit.Assert
import org.junit.Test

/**
 * [ImageUtils] tests.
 */
class ImageUtilsTest {

    @Test
    fun testCalculateTargetSize() {
        // Returns requested dimensions if both are non-zero
        Assert.assertEquals(ImageUtils.Size(10, 10), calculateTargetSize(1, 1, 10, 10, -1, -1))
        Assert.assertEquals(ImageUtils.Size(20, 20), calculateTargetSize(1, 2, 20, 20, -1, -1))

        // Requested height = 0
        Assert.assertEquals(ImageUtils.Size(10, 20), calculateTargetSize(5, 10, 10, 0, -1, -1))
        Assert.assertEquals(ImageUtils.Size(25, 100), calculateTargetSize(250, 1000, 25, 0, -1, -1))

        // Requested width = 0
        Assert.assertEquals(ImageUtils.Size(20, 10), calculateTargetSize(10, 5, 0, 10, -1, -1))
        Assert.assertEquals(ImageUtils.Size(100, 25), calculateTargetSize(1000, 250, 0, 25, -1, -1))
    }

    @Test
    fun testFallbackDimensions() {
        // Requested height = 0, no fallback
        Assert.assertEquals(ImageUtils.Size(10, 20), calculateTargetSize(5, 10, 10, 0, -1, -1))
        // Requested height = 0, with fallback
        Assert.assertEquals(
            ImageUtils.Size(25, 999), calculateTargetSize(250, 1000, 25, 0, 999, 999)
        )

        // Requested width = 0, no fallback
        Assert.assertEquals(ImageUtils.Size(20, 10), calculateTargetSize(10, 5, 0, 10, -1, -1))
        // Requested width = 0, with fallback
        Assert.assertEquals(
            ImageUtils.Size(999, 25), calculateTargetSize(1000, 250, 0, 25, 999, 999)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCalculateTargetSizeZeroWidth() {
        Assert.assertEquals(ImageUtils.Size(1, 1), calculateTargetSize(0, 1, 1, 1, -1, -1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCalculateTargetSizeZeroHeight() {
        Assert.assertEquals(ImageUtils.Size(1, 1), calculateTargetSize(1, 0, 1, 1, -1, -1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCalculateTargetSizeZeroReqWidthAndReqHeight() {
        Assert.assertEquals(ImageUtils.Size(1, 1), calculateTargetSize(1, 1, 0, 0, -1, -1))
    }
}
