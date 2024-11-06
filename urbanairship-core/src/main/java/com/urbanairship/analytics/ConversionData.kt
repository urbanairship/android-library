package com.urbanairship.analytics

import androidx.annotation.RestrictTo

/**
 * Conversion data
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class ConversionData(
    public val conversionSendId: String? = null,
    public val conversionMetadata: String? = null,
    public val lastReceivedMetadata: String? = null
)
