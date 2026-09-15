/* Copyright Airship and Contributors */

package com.urbanairship.embedded

/**
 * Decides whether a pending embedded instance is eligible to be displayed.
 *
 * Eligibility, not ordering: which of the survivors is displayed is
 * [AirshipEmbeddedSelection]'s job. Applied before the selection, so an excluded instance is
 * never displayed even when the selection names it, never becomes a page in a group or
 * carousel, and is never offered to the model as a candidate.
 *
 * Prefer this over [AirshipEmbeddedSelection.ByInstanceId] when the intent is to exclude
 * specific instances rather than to enumerate the acceptable ones.
 *
 * @param info the instance to consider.
 * @return true to keep it.
 */
public typealias AirshipEmbeddedFilter = (info: AirshipEmbeddedInfo) -> Boolean
