/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

internal sealed class SchedulePrepareResult {
    internal data class Prepared(val schedule: PreparedSchedule) : SchedulePrepareResult()
    internal data object Cancel : SchedulePrepareResult()
    internal data object Invalidate : SchedulePrepareResult()
    internal data object Skip : SchedulePrepareResult()
    internal data object Penalize : SchedulePrepareResult()
}
