/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utils class to generate a border radius array.
 */
public abstract class BorderRadius {

    @IntDef(flag = true,
            value = { TOP_LEFT,
                      TOP_RIGHT,
                      BOTTOM_RIGHT,
                      BOTTOM_LEFT,
                      ALL,
                      LEFT,
                      RIGHT,
                      BOTTOM,
                      TOP })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BorderRadiusFlag {}

    /**
     * Top left border radius flag.
     */
    public static final int TOP_LEFT = 1;

    /**
     * Top right border radius flag.
     */
    public static final int TOP_RIGHT = 1 << 1;

    /**
     * Bottom Right border radius flag.
     */
    public static final int BOTTOM_RIGHT = 1 << 2;

    /**
     * Bottom left border radius flag.
     */
    public static final int BOTTOM_LEFT = 1 << 3;

    /**
     * Flag for all 4 corners.
     */
    public static final int ALL = TOP_LEFT | TOP_RIGHT | BOTTOM_RIGHT | BOTTOM_LEFT;

    /**
     * Flag for the left corners.
     */
    public static final int LEFT = TOP_LEFT | BOTTOM_LEFT;

    /**
     * Flag for the right corners.
     */
    public static final int RIGHT = TOP_RIGHT | BOTTOM_RIGHT;

    /**
     * Flag for the top corners.
     */
    public static final int TOP = TOP_LEFT | TOP_RIGHT;

    /**
     * Flag for the bottom corners.
     */
    public static final int BOTTOM = BOTTOM_LEFT | BOTTOM_RIGHT;


    /**
     * Creates the border radius array.
     *
     * @param pixels The border radius in pixels.
     * @param borderRadiusFlag THe border radius flag.
     * @return The corner radius array.
     */
    public static float[] createRadiiArray(float pixels, @BorderRadiusFlag int borderRadiusFlag) {
        float[] radii = new float[8];

        // topLeftX, topLeftY, topRightX, topRightY, bottomRightX, bottomRightY, bottomLeftX, bottomLeftY

        if ((borderRadiusFlag & TOP_LEFT) == TOP_LEFT) {
            radii[0] = pixels;
            radii[1] = pixels;
        }

        if ((borderRadiusFlag & TOP_RIGHT) == TOP_RIGHT) {
            radii[2] = pixels;
            radii[3] = pixels;
        }

        if ((borderRadiusFlag & BOTTOM_RIGHT) == BOTTOM_RIGHT) {
            radii[4] = pixels;
            radii[5] = pixels;
        }

        if ((borderRadiusFlag & BOTTOM_LEFT) == BOTTOM_LEFT) {
            radii[6] = pixels;
            radii[7] = pixels;
        }

        return radii;
    }

}
