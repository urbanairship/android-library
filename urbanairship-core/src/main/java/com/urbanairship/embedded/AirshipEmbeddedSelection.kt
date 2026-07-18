/* Copyright Airship and Contributors */

package com.urbanairship.embedded

/**
 * Controls which pending embedded content instance is displayed in an `AirshipEmbeddedView`.
 */
public sealed class AirshipEmbeddedSelection {

    /**
     * Display content using priority ordering, with sticky last-displayed behavior.
     *
     * This is the default selection mode. The instance with the lowest numeric priority value
     * is displayed. Once an instance is selected, it remains displayed until dismissed, even
     * if a higher-priority instance arrives, preventing unnecessary thrashing.
     */
    public data object Priority : AirshipEmbeddedSelection()

    /**
     * Display content using the provided [comparator] to sort available embedded instances.
     *
     * The instance that sorts first is displayed. Sticky last-displayed behavior is bypassed.
     *
     * @param comparator the [Comparator] used to sort available embedded instances.
     */
    public data class ByComparator(
        public val comparator: Comparator<AirshipEmbeddedInfo>
    ) : AirshipEmbeddedSelection()

    /**
     * Display the specific embedded content instance whose
     * [AirshipEmbeddedInfo.instanceId] matches [instanceId].
     *
     * If the instance is not currently pending, the placeholder is shown (strict targeting;
     * no substitution is made). This is useful when app-side logic has already selected the
     * desired instance and wants to pin the view to it — for example, across a React Native
     * bridge where a [Comparator] closure cannot be passed.
     *
     * @param instanceId the [AirshipEmbeddedInfo.instanceId] of the instance to display.
     */
    public data class ByInstanceId(
        public val instanceId: String
    ) : AirshipEmbeddedSelection()
}
