/* Copyright Airship and Contributors */
package com.urbanairship.app

/**
 * Activity listener that forwards application events to a list of listeners.
 */
public class ForwardingApplicationListener public constructor() : ApplicationListener {

    private val listeners = mutableListOf<ApplicationListener>()

    /**
     * Adds a listener.
     *
     * @param listener The added listener.
     */
    public fun addListener(listener: ApplicationListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a listener.
     *
     * @param listener The removed listener.
     */
    public fun removeListener(listener: ApplicationListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    override fun onForeground(time: Long) {
        listeners.toList().forEach { it.onForeground(time) }
    }

    override fun onBackground(time: Long) {
        listeners.toList().forEach { it.onBackground(time) }
    }
}
