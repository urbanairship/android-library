/* Copyright Airship and Contributors */
package com.urbanairship.modules.location

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.modules.Module

/**
 * Location module loader.
 *
 * @hide
 */
public class LocationModule public constructor(
    component: AirshipComponent,
    @JvmField val locationClient: AirshipLocationClient
) : Module(setOf(component))
