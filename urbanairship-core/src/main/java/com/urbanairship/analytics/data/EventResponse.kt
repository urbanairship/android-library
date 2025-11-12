/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

/**
 * Model object containing response information from a request.
 *
 * @hide
 */
internal class EventResponse(private val headers: Map<String, String>) {

    /**
     * The maximum total size.
     */
    val maxTotalSize: Int
        get() {
            return headers["X-UA-Max-Total"]
                ?.let {
                    (it.toInt() * 1024).coerceIn(MIN_TOTAL_DB_SIZE_BYTES, MAX_TOTAL_DB_SIZE_BYTES)
                }
                ?: MIN_TOTAL_DB_SIZE_BYTES
        }

    /**
     * The maximum batch size.
     */
    val maxBatchSize: Int
        get() {
            return headers["X-UA-Max-Batch"]
                ?.let {
                    (it.toInt() * 1024).coerceIn(MIN_BATCH_SIZE_BYTES, MAX_BATCH_SIZE_BYTES)
                }
                ?: MIN_BATCH_SIZE_BYTES
        }

    /**
     * The minimum batch interval.
     */
    val minBatchInterval: Int
        get() {
            return headers["X-UA-Min-Batch-Interval"]?.toInt()
                ?.coerceIn(MIN_BATCH_INTERVAL_MS, MAX_BATCH_INTERVAL_MS)
                ?: MIN_BATCH_INTERVAL_MS
        }

    internal companion object {

        const val MAX_TOTAL_DB_SIZE_BYTES: Int = 5 * 1024 * 1024 // 5 MB
        const val MIN_TOTAL_DB_SIZE_BYTES: Int = 10 * 1024 // 10 KB

        const val MAX_BATCH_SIZE_BYTES: Int = 500 * 1024 // 500 KB
        const val MIN_BATCH_SIZE_BYTES: Int = 10 * 1024 // 10 KB

        const val MIN_BATCH_INTERVAL_MS: Int = 60 * 1000 // 60 seconds
        const val MAX_BATCH_INTERVAL_MS: Int = 7 * 24 * 3600 * 1000 // 7 days
    }
}
