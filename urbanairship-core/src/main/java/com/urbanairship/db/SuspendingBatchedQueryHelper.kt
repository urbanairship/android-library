package com.urbanairship.db

import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import kotlin.math.ceil
import kotlin.math.min

/**
 * Helper for running large queries in smaller batches to avoid 'too many SQL variables' exceptions.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object SuspendingBatchedQueryHelper {

    /**
     * The maximum number of host parameters in a statement.
     * See: https://sqlite.org/limits.html#max_variable_number
     */
    private const val MAX_STATEMENT_PARAMETERS = 999

    /**
     * Splits up the given `items` into batches smaller than the SQL variable limit and invokes the
     * `callback` for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the
     * batches are reflected in the items list, and vice-versa.
     *
     * @param items The list of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
    </T> */
    public suspend fun <T> runBatched(items: List<T>, callback: suspend (List<T>) -> Unit) {
        runBatched(MAX_STATEMENT_PARAMETERS, items, callback)
    }

    /**
     * Splits up the given `items` into batches smaller than the SQL variable limit and invokes the
     * `callback` for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the
     * batches are reflected in the items list, and vice-versa.
     *
     * @param items The set of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
    </T> */
    public suspend fun <T> runBatched(items: Set<T>, callback: suspend (Set<T>) -> Unit) {
        runBatched(MAX_STATEMENT_PARAMETERS, items, callback)
    }

    /**
     * Collects a list of `items` resulting from running batched queries.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the
     * batches are reflected in the items list, and vice-versa.
     *
     * @param items The list of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists and return the result of the query.
     * @param <T> The list type to split into batches.
     * @param <R> The result type of the batched query.
     */
    public suspend fun <T, R> collectBatched(
        items: List<T>,
        callback: suspend (List<T>) -> List<R>?
    ): List<R> {
        val result = mutableListOf<R>()

        runBatched(MAX_STATEMENT_PARAMETERS, items) { batch ->
            callback(batch)?.let(result::addAll)
        }

        return result.toList()
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
    public suspend fun <T> runBatched(
        @IntRange(from = 1) batchSize: Int, items: List<T>, callback: suspend (List<T>) -> Unit
    ) {
        require(batchSize != 0) { "Failed to run batched! 'batchSize' must be greater than zero." }
        val numBatches = ceil(items.size.toDouble() / batchSize).toInt()
        for (i in 0 until numBatches) {
            val start = i * batchSize
            val end = (start + min((items.size - start).toDouble(), batchSize.toDouble())).toInt()
            callback(items.subList(start, end))
        }
    }

    /**
     * Splits up the given `items` into batches of the given size and invokes the `callback` for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the batches
     * are reflected in the items list, and vice-versa.
     *
     * @param batchSize The size of each batch.
     * @param items The set of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
    </T> */
    @VisibleForTesting
    public suspend fun <T> runBatched(
        @IntRange(from = 1) batchSize: Int, items: Set<T>, callback: suspend (Set<T>) -> Unit
    ) {
        require(batchSize != 0) { "Failed to run batched! 'batchSize' must be greater than zero." }
        val numBatches = ceil(items.size.toDouble() / batchSize).toInt()
        for (i in 0 until numBatches) {
            val start = i * batchSize
            val end = (start + min((items.size - start).toDouble(), batchSize.toDouble())).toInt()
            val subSet = mutableSetOf<T>()
            for (j in start until end) {
                subSet.add(items.elementAt(j))
            }
            callback(subSet)
        }
    }
}
