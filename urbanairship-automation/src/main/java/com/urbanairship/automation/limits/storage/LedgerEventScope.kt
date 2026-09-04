/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

/**
 * The recording scope of a stored ledger event, projected without its body.
 *
 * Retention decides whether a row is orphaned from its scope columns alone, so
 * it reads these instead of the full [LedgerEventEntity].
 */
internal data class LedgerEventScope(
    val id: Int,
    val scheduleId: String,
    val sharedId: String?
)
