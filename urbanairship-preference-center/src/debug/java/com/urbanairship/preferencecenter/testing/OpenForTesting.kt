package com.urbanairship.preferencecenter.testing

/** Allows classes to be made `open` for mocking purposes, while remaining final in release builds. */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class OpenClass

/** Allows classes to be extendable in debug builds. */
@OpenClass
@Target(AnnotationTarget.CLASS)
annotation class OpenForTesting
