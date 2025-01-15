/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import android.content.SharedPreferences
import com.urbanairship.debug.ui.events.EventDao
import com.urbanairship.debug.ui.events.EventDatabase
import com.urbanairship.debug.ui.events.EventRepository
import com.urbanairship.debug.ui.push.PushDao
import com.urbanairship.debug.ui.push.PushDatabase
import com.urbanairship.debug.ui.push.PushRepository

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

        const val PREFERENCES_KEY = "com.urbanairship.debug"
    }

    fun getEventRepository(): EventRepository

    fun getPushRepository(): PushRepository

    fun getEventDao(): EventDao

    fun getPushDao(): PushDao

    val sharedPreferences: SharedPreferences
}

internal class DefaultServiceLocator(val context: Context) : ServiceLocator {

    private val eventDatabase: EventDatabase by lazy {
        EventDatabase.create(context)
    }

    private val pushDatabase: PushDatabase by lazy {
        PushDatabase.create(context)
    }

    override fun getEventRepository(): EventRepository {
        return EventRepository(eventDatabase.eventDao())
    }

    override fun getPushRepository(): PushRepository {
        return PushRepository(pushDatabase.pushDao())
    }

    override fun getEventDao(): EventDao = eventDatabase.eventDao()

    override fun getPushDao(): PushDao = pushDatabase.pushDao()

    override val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(ServiceLocator.PREFERENCES_KEY, Context.MODE_PRIVATE)
    }
}
