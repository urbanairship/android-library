/* Copyright Airship and Contributors */
package com.urbanairship.permission

import androidx.annotation.VisibleForTesting
import androidx.core.util.ObjectsCompat

/**
 * Permission request result. See [PermissionsManager.requestPermission]
 */
public class PermissionRequestResult @VisibleForTesting internal constructor(
    /**
     * @property permissionStatus The permission status.
     */
    public val permissionStatus: PermissionStatus,
    /**
     * @property isSilentlyDenied If the request failed to display the prompt.
     */
    public val isSilentlyDenied: Boolean
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as PermissionRequestResult
        return isSilentlyDenied == that.isSilentlyDenied && permissionStatus == that.permissionStatus
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(permissionStatus, isSilentlyDenied)
    }

    override fun toString(): String {
        return "PermissionRequestResult{permissionStatus=$permissionStatus, isSilentlyDenied=$isSilentlyDenied}"
    }

    public companion object {

        /**
         * New granted result.
         *
         * @return The result.
         */
        public fun granted(): PermissionRequestResult {
            return PermissionRequestResult(PermissionStatus.GRANTED, false)
        }

        /**
         * New denied result.
         *
         * @param isSilentlyDenied If the permission prompt was not able to be displayed.
         * @return The result.
         */
        public fun denied(isSilentlyDenied: Boolean): PermissionRequestResult {
            return PermissionRequestResult(PermissionStatus.DENIED, isSilentlyDenied)
        }

        /**
         * New not determined result.
         */
        public fun notDetermined(): PermissionRequestResult {
            return PermissionRequestResult(PermissionStatus.NOT_DETERMINED, false)
        }
    }
}
