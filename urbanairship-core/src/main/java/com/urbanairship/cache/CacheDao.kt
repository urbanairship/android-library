package com.urbanairship.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface CacheDao {
    @Insert
    suspend fun addEntry(item: CacheEntity)

    @Query("select * from cacheItems where `key` = :key")
    suspend fun getEntryWithKey(key: String): CacheEntity?

    @Query("delete from cacheItems where `key` = :key")
    suspend fun deleteItemWithKey(key: String)

    @Transaction
    suspend fun updateEntry(item: CacheEntity) {
        deleteItemWithKey(item.key)
        addEntry(item)
    }

    @Query("delete from cacheItems where appVersion != :appVersion or sdkVersion != :sdkVersion or expireOn < :timestamp")
    suspend fun deleteExpired(appVersion: String, sdkVersion: String, timestamp: Long)
}
