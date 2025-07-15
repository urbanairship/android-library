/* Copyright Airship and Contributors */
package com.urbanairship.app

/**
 * A convenience class to extend when you only want to listen for a subset
 * of of application events.
 */
public open class SimpleApplicationListener public constructor() : ApplicationListener {

    override fun onForeground(time: Long) { }

    override fun onBackground(time: Long) { }
}
