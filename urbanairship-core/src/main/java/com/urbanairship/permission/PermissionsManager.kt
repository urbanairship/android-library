/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.arch.core.util.Function
import androidx.core.util.Consumer
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleActivityListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Airship permission manager.
 *
 * Airship will provide the default handling for notifications. Other [Permission] needs to
 * be handled by the application by setting a delegate with [PermissionDelegate].
 */
public class PermissionsManager internal constructor(
    private var context: Context,
    activityMonitor: ActivityMonitor
) {

    private val permissionDelegateMap: MutableMap<Permission, PermissionDelegate?> = mutableMapOf()
    private val airshipEnablers: MutableList<Consumer<Permission>> = CopyOnWriteArrayList()
    private val permissionStatusMap: MutableMap<Permission, PermissionStatus> = mutableMapOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val onPermissionStatusChangedListeners: MutableList<OnPermissionStatusChangedListener> =
        CopyOnWriteArrayList()
    private val pendingRequestResults: MutableMap<PermissionDelegate, PendingResult<PermissionRequestResult?>> =
        mutableMapOf()
    private val pendingCheckResults: MutableMap<PermissionDelegate, PendingResult<PermissionStatus?>> =
        mutableMapOf()


    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(context: Context): this(context, GlobalActivityMonitor.shared(context))

    init {
        activityMonitor.addActivityListener(object : SimpleActivityListener() {
            override fun onActivityResumed(activity: Activity) {
                updatePermissions()
            }
        })
    }

    @MainThread
    private fun updatePermissions() {
        for (permission in configuredPermissions) {
            checkPermissionStatus(
                permission
            ) { status: PermissionStatus ->
                updatePermissionStatus(permission, status)
            }
        }
    }

    @MainThread
    private fun updatePermissionStatus(permission: Permission, status: PermissionStatus) {
        val previous = permissionStatusMap[permission]
        if (previous != null && previous != status) {
            for (listener in this.onPermissionStatusChangedListeners) {
                listener.onPermissionStatusChanged(permission, status)
            }
        }
        permissionStatusMap[permission] = status
    }

    /**
     * Gets the set of permissions that have a delegate.
     *
     * @return The set of configured permissions.
     */
    public val configuredPermissions: Set<Permission>
        get() {
            synchronized(permissionDelegateMap) {
                return permissionDelegateMap.keys
            }
        }

    /**
     * Sets a delegate to handle permissions.
     *
     * @param permission The permission.
     * @param delegate The delegate.
     */
    public fun setPermissionDelegate(permission: Permission, delegate: PermissionDelegate?) {
        synchronized(permissionDelegateMap) {
            permissionDelegateMap[permission] = delegate
            checkPermissionStatus(permission)
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addAirshipEnabler(onEnable: Consumer<Permission>) {
        airshipEnablers.add(onEnable)
    }

    /**
     * Adds a permission status changed listener.
     *
     * @param listener The listener to remove.
     */
    public fun addOnPermissionStatusChangedListener(listener: OnPermissionStatusChangedListener) {
        onPermissionStatusChangedListeners.add(listener)
    }

    /**
     * Removes a permission status changed listener.
     *
     * @param listener The listener to remove.
     */
    public fun removeOnPermissionStatusChangedListener(listener: OnPermissionStatusChangedListener) {
        onPermissionStatusChangedListeners.remove(listener)
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @param callback The callback with the result.
     */
    public fun checkPermissionStatus(
        permission: Permission, callback: Consumer<PermissionStatus>
    ) {
        checkPermissionStatus(permission).addResultCallback {
            callback.accept(it)
        }
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @return A pending result.
     */
    public fun checkPermissionStatus(permission: Permission): PendingResult<PermissionStatus?> {
        UALog.d { "Checking permission for $permission" }

        synchronized(pendingCheckResults) {
            return pendingOrCall(permission, pendingCheckResults) { delegate: PermissionDelegate? ->
                val pendingResult = PendingResult<PermissionStatus?>()
                if (delegate == null) {
                    UALog.d { "No delegate for permission $permission" }
                    pendingResult.result = PermissionStatus.NOT_DETERMINED
                    return@pendingOrCall pendingResult
                }

                synchronized(pendingCheckResults) {
                    pendingCheckResults.put(delegate, pendingResult)
                }

                mainHandler.post {
                    delegate.checkPermissionStatus(context, Consumer { status: PermissionStatus ->
                        UALog.d { "Check permission $permission status result: $status" }
                        updatePermissionStatus(permission, status)
                        pendingResult.result = status
                        synchronized(pendingCheckResults) {
                            pendingCheckResults.remove(delegate)
                        }
                    })
                }
                pendingResult
            }
        }
    }

    /**
     * Requests a permission.
     *
     * @param permission The permission.
     * @param callback The callback.
     */
    public fun requestPermission(
        permission: Permission, callback: Consumer<PermissionRequestResult?>
    ) {
        requestPermission(permission, false, callback)
    }

    /**
     * Requests a permission.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling [com.urbanairship.PrivacyManager.FEATURE_PUSH] and
     * [com.urbanairship.push.PushManager.setUserNotificationsEnabled] if the push permission
     * is granted.
     * @param callback The callback.
     */
    public fun requestPermission(
        permission: Permission,
        enableAirshipUsageOnGrant: Boolean,
        callback: Consumer<PermissionRequestResult?>
    ) {
        requestPermission(
            permission, enableAirshipUsageOnGrant
        ).addResultCallback { t: PermissionRequestResult? -> callback.accept(t) }
    }

    /**
     * Requests a permission. If a delegate is not set to handle the permission [PermissionStatus.NOT_DETERMINED] will
     * be returned.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling [com.urbanairship.PrivacyManager.FEATURE_PUSH] and
     * [com.urbanairship.push.PushManager.setUserNotificationsEnabled] if the push permission
     * is granted.
     * @return A pending result.
     */
    /**
     * Requests a permission. If a delegate is not set to handle the permission [PermissionStatus.NOT_DETERMINED] will
     * be returned.
     *
     * @param permission The permission.
     * @return A pending result.
     */
    @JvmOverloads
    public fun requestPermission(
        permission: Permission, enableAirshipUsageOnGrant: Boolean = false
    ): PendingResult<PermissionRequestResult?> {
        UALog.d { "Requesting permission for $permission" }

        synchronized(pendingRequestResults) {
            val result =
                pendingOrCall(permission, pendingRequestResults) { delegate: PermissionDelegate? ->
                    val pendingResult = PendingResult<PermissionRequestResult?>()
                    if (delegate == null) {
                        UALog.d { "No delegate for permission $permission" }
                        pendingResult.result = PermissionRequestResult.notDetermined()
                        return@pendingOrCall pendingResult
                    }

                    synchronized(pendingRequestResults) {
                        pendingRequestResults.put(delegate, pendingResult)
                    }

                    mainHandler.post {
                        delegate.requestPermission(context,
                            Consumer { requestResult: PermissionRequestResult ->
                                UALog.d { "Permission $permission request result: $requestResult" }
                                updatePermissionStatus(permission, requestResult.permissionStatus)
                                pendingResult.result = requestResult
                                synchronized(pendingRequestResults) {
                                    pendingRequestResults.remove(delegate)
                                }
                            })
                    }
                    pendingResult
                }
            if (enableAirshipUsageOnGrant) {
                result.addResultCallback { requestResult: PermissionRequestResult? ->
                    if (requestResult != null && requestResult.permissionStatus == PermissionStatus.GRANTED) {
                        for (enabler in this.airshipEnablers) {
                            enabler.accept(permission)
                        }
                    }
                }
            }
            return result
        }
    }

    private fun getDelegate(permission: Permission): PermissionDelegate? {
        synchronized(permissionDelegateMap) {
            return permissionDelegateMap[permission]
        }
    }

    private fun <T> pendingOrCall(
        permission: Permission,
        pending: Map<PermissionDelegate, PendingResult<T>>,
        delegateFunction: Function<PermissionDelegate?, PendingResult<T>>
    ): PendingResult<T> {
        val delegate = getDelegate(permission)
        if (delegate != null) {
            val result = pending[delegate]
            if (result != null) {
                return result
            }
        }
        return delegateFunction.apply(delegate)
    }
}
