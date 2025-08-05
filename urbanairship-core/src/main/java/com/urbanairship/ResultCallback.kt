package com.urbanairship

/**
 * Result callback interface.
 *
 * @param <T> The type of result.
</T> */
public fun interface ResultCallback<T> {
    public fun onResult(result: T?)
}
