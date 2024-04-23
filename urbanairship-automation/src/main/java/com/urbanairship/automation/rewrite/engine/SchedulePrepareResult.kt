package com.urbanairship.automation.rewrite.engine

import androidx.annotation.RestrictTo

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class SchedulePrepareResult {
    internal data class Prepared(val schedule: PreparedSchedule) : SchedulePrepareResult()
    internal data object Cancel : SchedulePrepareResult()
    internal data object Invalidate : SchedulePrepareResult()
    internal data object Skip : SchedulePrepareResult()
    internal data object Penalize : SchedulePrepareResult()
}
