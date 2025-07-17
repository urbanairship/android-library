/* Copyright Airship and Contributors */
package com.urbanairship.javascript

/**
 * Executes JavaScript.
 * @hide
 */
public fun interface JavaScriptExecutor {

    /**
     * Called to execute JavaScript.
     *
     * @param javaScript The JavaScript.
     */
    public fun executeJavaScript(javaScript: String)
}
