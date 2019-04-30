/* Copyright Airship and Contributors */

package com.urbanairship.app;

/**
 * A convenience class to extend when you only want to listen for a subset
 * of of application events.
 */
public class SimpleApplicationListener implements ApplicationListener {

    @Override
    public void onForeground(long time) {
    }

    @Override
    public void onBackground(long time) {
    }

}