package com.urbanairship.actions

public fun interface ActionPredicate {
    public fun apply(arguments: ActionArguments): Boolean
}
