/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import com.urbanairship.debug.event.EventRepository
import com.urbanairship.debug.event.persistence.EventDao
import com.urbanairship.debug.event.persistence.EventDatabase

/**
 * Service locator. Eventually we will want to use a proper DI framework.
 * @hide
 */
internal interface ServiceLocator {

    companion object {

        @Volatile
        private var instance: ServiceLocator? = null

        fun shared(context: Context): ServiceLocator {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = DefaultServiceLocator(context)
                    }
                }
            }

            return instance!!
        }
    }

    fun getEventRepository(): EventRepository

    fun getEventDao(): EventDao
}

internal class DefaultServiceLocator(val context: Context) : ServiceLocator {

    private val eventDatabase: EventDatabase by lazy {
        EventDatabase.create(context)
    }

    override fun getEventRepository(): EventRepository {
        return EventRepository(eventDatabase.eventDao())
    }

    override fun getEventDao(): EventDao = eventDatabase.eventDao()

}
