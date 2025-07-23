/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.util.Consumer
import kotlin.math.ceil
import kotlin.math.min

/**
 * Helper for running large queries in smaller batches to avoid 'too many SQL variables' exceptions.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object BatchedQueryHelper {

    /**
     * The maximum number of host parameters in a statement.
     * See: https://sqlite.org/limits.html#max_variable_number
     */
    private const val MAX_STATEMENT_PARAMETERS = 999

    /**
     * Splits up the given `items` into batches smaller than the SQL variable limit and invokes the
     * `callback` for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the batches
     * are reflected in the items list, and vice-versa.
     *
     * @param items The list of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
    </T> */
    public fun <T> runBatched(items: List<T>, callback: Consumer<List<T>>) {
        runBatched(MAX_STATEMENT_PARAMETERS, items, callback)
    }

    /**
     * Splits up the given `items` into batches of the given size and invokes the `callback` for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the batches
     * are reflected in the items list, and vice-versa.
     *
     * @param batchSize The size of each batch.
     * @param items The list of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
    </T> */
    @VisibleForTesting
    public fun <T> runBatched(
        @IntRange(from = 1) batchSize: Int, items: List<T>, callback: Consumer<List<T>>
    ) {
        require(batchSize != 0) { "Failed to run batched! 'batchSize' must be greater than zero." }
        val numBatches = ceil(items.size.toDouble() / batchSize).toInt()
        for (i in 0..<numBatches) {
            val start = i * batchSize
            val end = (start + min((items.size - start).toDouble(), batchSize.toDouble())).toInt()
            callback.accept(items.subList(start, end))
        }
    }
}
