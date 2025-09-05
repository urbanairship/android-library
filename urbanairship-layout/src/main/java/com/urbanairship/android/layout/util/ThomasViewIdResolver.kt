package com.urbanairship.android.layout.util

import android.view.View
import com.urbanairship.android.layout.property.ViewType
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ThomasViewIdResolver() {
    private var idMap: MutableMap<String, Int> = mutableMapOf()
    private val lock: Lock = ReentrantLock()

    fun viewId(): Int {
        return View.generateViewId()
    }

    fun viewId(identifier: String?, viewType: ViewType): Int {
        if (identifier == null) {
            return View.generateViewId()
        }
        val key = key(identifier, viewType)
        return lock.withLock {
            idMap.getOrPut(key) {
                View.generateViewId()
            }
        }
    }

    private fun key(identifier: String, viewType: ViewType): String {
        return "$identifier:$viewType"
    }
}
