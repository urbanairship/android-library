/* Copyright Airship and Contributors */
package com.urbanairship.wallet

import android.os.Looper
import com.urbanairship.CancelableOperation

/**
 * [CancelableOperation] wrapper around [Callback].
 */
internal class CancelableCallback
/**
 * Callback constructor.
 *
 * @param callback The request callback.
 * @param looper A Looper object whose message queue will be used for the callback,
 * or null to make callbacks on the calling thread or main thread if the current thread
 * does not have a looper associated with it.
 */
    constructor(private var callback: Callback?, looper: Looper?) : CancelableOperation(looper) {

    private var status = 0
    private var pass: Pass? = null

    override fun onRun() {
        val callback = this.callback ?: return
        val pass = this.pass
        if (pass != null) {
            callback.onResult(pass)
        } else {
            callback.onError(status)
        }
    }

    override fun onCancel() {
        this.callback = null
        this.pass = null
    }

    /**
     * Sets the pass request result.
     *
     * @param status The request response status.
     * @param pass The parsed response [Pass].
     */
    fun setResult(status: Int, pass: Pass?) {
        if (isCancelled) {
            return
        }

        this.status = status
        this.pass = pass
    }
}
