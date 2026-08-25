/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.annotation.RestrictTo
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

/**
 * Interop between [Instant] and [kotlin.time.Duration].
 *
 * [Instant] only speaks `java.time.Duration`, so without these every bit of time arithmetic
 * has to round-trip through epoch millis. These keep call sites in the same units the rest
 * of the SDK uses.
 *
 * @hide
 */

/**
 * The [Duration] elapsed from [other] to this instant. Negative if [other] is later.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public operator fun Instant.minus(other: Instant): Duration =
    JavaDuration.between(other, this).toKotlinDuration()

/**
 * This instant advanced by [duration].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public operator fun Instant.plus(duration: Duration): Instant =
    plus(duration.toJavaDuration())

/**
 * This instant moved back by [duration].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public operator fun Instant.minus(duration: Duration): Instant =
    minus(duration.toJavaDuration())
