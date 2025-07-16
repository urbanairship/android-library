/* Copyright Airship and Contributors */
package com.urbanairship.base

/**
 * @hide
 */
internal fun interface Extender<T> {
    fun extend(value: T): T
}
