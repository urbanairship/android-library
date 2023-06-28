package com.urbanairship.android.layout.util

/**
 * Marks declarations in the Layout module that are **delicate** &mdash;
 * they have limited use-cases and must be used with care in general code.
 * Delicate APIs should only be used in very specific situations, and users
 * should make sure they understand the implications of using such APIs.
 */
@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is a delicate API and its use requires care." +
            " Make sure you fully read and understand documentation of the declaration that is marked as a delicate API."
)
internal annotation class DelicateLayoutApi(val description: String = "")
