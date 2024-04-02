package com.urbanairship.automation.rewrite.engine

internal sealed class SchedulePrepareResult {
    data class Prepared(val schedule: PreparedSchedule) : SchedulePrepareResult()
    data object Cancel : SchedulePrepareResult()
    data object Invalidate : SchedulePrepareResult()
    data object Skip : SchedulePrepareResult()
    data object Penalize : SchedulePrepareResult()
}
