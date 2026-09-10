/* Copyright Airship and Contributors */
package com.urbanairship.ai

/**
 * Identifies an AI use case — one key per evaluable feature.
 *
 * [Subject] is the feature-specific data a provider receives, so registering a provider for the
 * wrong usage is a compile error. Equality is by [rawValue] alone, so an [ModelResolver] can
 * compare the `Usage<*>` it is handed against a feature's key.
 *
 * @param rawValue The usage key.
 */
public class Usage<Subject> public constructor(public val rawValue: String) {
    override fun equals(other: Any?): Boolean = other is Usage<*> && rawValue == other.rawValue
    override fun hashCode(): Int = rawValue.hashCode()
    override fun toString(): String = rawValue
}
