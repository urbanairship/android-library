/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import com.urbanairship.automation.AutomationSchedule

/**
 * Stub [LedgerLimitEvaluatorInterface] returning a canned answer, so engine
 * tests can drive the limit-gated transitions without a backing ledger store.
 */
internal class TestLedgerLimitEvaluator(
    var overLimit: Boolean = false
) : LedgerLimitEvaluatorInterface {

    /** IDs of the schedules evaluated so far, in order. */
    val evaluated: MutableList<String> = mutableListOf()

    override suspend fun isOverLimit(schedule: AutomationSchedule): Boolean {
        evaluated.add(schedule.identifier)
        return overLimit
    }
}
