package com.urbanairship.android.layout.testing

/** Allows classes to be made `open` for mocking purposes, while remaining final in release builds. */
@Target(AnnotationTarget.ANNOTATION_CLASS)
internal annotation class OpenClass

/** Allows classes to be extendable in debug builds. */
@OpenClass
@Target(AnnotationTarget.CLASS)
internal annotation class OpenForTesting
