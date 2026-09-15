/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage

import androidx.room.Relation

/**
 * The recording scope of a stored ledger event, projected without its body.
 *
 * Retention decides whether a row is orphaned from its scope columns alone, so
 * it reads these instead of the full [LedgerEventEntity].
 */
internal data class LedgerEventScope(
    val id: Long,
    val scheduleId: String,

    @Relation(parentColumn = "id", entityColumn = "eventId", entity = LedgerSharedIdEntity::class)
    val sharedIds: List<LedgerSharedIdEntity>
)
