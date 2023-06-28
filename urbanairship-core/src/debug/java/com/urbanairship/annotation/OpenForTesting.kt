package com.urbanairship.annotation

/**
 *  Allows classes to be made `open` for mocking purposes, while remaining final in release builds.
 *  @hide
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class OpenClass

/**
 * Allows classes to be extendable in debug builds.
 * @hide
 */
@OpenClass
@Target(AnnotationTarget.CLASS)
public annotation class OpenForTesting
