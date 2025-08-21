/* Copyright Airship and Contributors */
package com.urbanairship.actions

import com.urbanairship.actions.ActionResult.Companion.newEmptyResult

/**
 * Test action that tracks what methods were called
 */
public open class TestAction public constructor(
    private val acceptsArguments: Boolean = true,
    private val result: ActionResult = newEmptyResult()
) : Action() {

    public var onStartCalled: Boolean = false
    public var onFinishCalled: Boolean = false
    public var performCalled: Boolean = false

    public var runArgs: ActionArguments? = null

    override fun perform(arguments: ActionArguments): ActionResult {
        this.performCalled = true
        this.runArgs = arguments
        return result
    }

    override fun onStart(arguments: ActionArguments) {
        onStartCalled = true
    }

    override fun onFinish(arguments: ActionArguments, result: ActionResult) {
        onFinishCalled = true
    }

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return acceptsArguments
    }
}
