/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import java.util.List;
import java.util.function.Consumer;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Helper for running large queries in smaller batches to avoid 'too many SQL variables' exceptions.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class BatchedQueryHelper {
    /**
     * The maximum number of host parameters in a statement.
     * See: https://sqlite.org/limits.html#max_variable_number
     */
    private static final int MAX_STATEMENT_PARAMETERS = 999;

    private BatchedQueryHelper() {}

    /**
     * Splits up the given {@code items} into batches smaller than the SQL variable limit and invokes the
     * {@code callback} for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the batches
     * are reflected in the items list, and vice-versa.
     *
     * @param items The list of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
     */
    public static <T> void runBatched(@NonNull List<T> items, @NonNull Consumer<List<T>> callback) {
        runBatched(MAX_STATEMENT_PARAMETERS, items, callback);
    }

    /**
     * Splits up the given {@code items} into batches of the given size and invokes the {@code callback} for each batch.
     *
     * Note that the batch lists are backed by the full items list, so non-structural changes in the batches
     * are reflected in the items list, and vice-versa.
     *
     * @param batchSize The size of each batch.
     * @param items The list of items to be split into batches.
     * @param callback The callback that will receive batched sub-lists.
     * @param <T> The list type.
     */
    @VisibleForTesting
    static <T> void runBatched(
        @IntRange(from = 1) int batchSize,
        @NonNull List<T> items,
        @NonNull Consumer<List<T>> callback
    ) {
        if (batchSize == 0) {
            throw new IllegalArgumentException("Failed to run batched! 'batchSize' must be greater than zero.");
        }
        int numBatches = (int) Math.ceil((double) items.size() / batchSize);
        for (int i = 0; i < numBatches; i++) {
            int start = i * batchSize;
            int end = start + Math.min(items.size() - start, batchSize);
            callback.accept(items.subList(start, end));
        }
    }
}
