package com.urbanairship.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.urbanairship.db.RetryingSQLiteOpenHelper

@Database(entities = [CacheEntity::class], version = 1)
internal abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        private const val DB_NAME = "ua_items_cache-%s.db"

        fun persistent(context: Context, appKey: String): CacheDatabase =
            try {
                val retryingOpenHelperFactory =
                    RetryingSQLiteOpenHelper.Factory(FrameworkSQLiteOpenHelperFactory(), true)

                Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, DB_NAME.format(appKey))
                    .openHelperFactory(retryingOpenHelperFactory)
                    .fallbackToDestructiveMigration()
                    .build()
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw ex
            }

        fun inMemory(context: Context): CacheDatabase =
            try {
                Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw ex
            }
    }
}
