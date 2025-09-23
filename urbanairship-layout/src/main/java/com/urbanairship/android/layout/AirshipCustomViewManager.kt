package com.urbanairship.android.layout

import android.content.Context
import android.view.View
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.model.PageRequest
import com.urbanairship.android.layout.scenecontroller.SceneController
import com.urbanairship.json.JsonMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manager for custom views rendered in scenes.
 */
public object AirshipCustomViewManager {

    @VisibleForTesting
    internal val handlers = ConcurrentHashMap<String, AirshipCustomViewHandler>()

    /**
     * Registers a custom view handler.
     *
     * @param name The name of the custom view.
     * @param handler The custom view handler.
     */
    public fun register(name: String, handler: AirshipCustomViewHandler) {
        handlers[name] = handler
    }

    /**
     * Registers a custom view handler.
     *
     * @param name The name of the custom view.
     * @param factory The custom view factory.
     */
    public fun register(name: String, factory: (AirshipCustomViewArguments) -> View) {
        handlers[name] = AirshipCustomViewHandler { _, args -> factory.invoke(args) }
    }

    /**
     * Unregisters a custom view handler.
     *
     * @param name The name of the custom view.
     */
    public fun unregister(name: String) {
        handlers.remove(name)
    }

    /** Unregisters all custom view handlers. */
    @VisibleForTesting
    internal fun unregisterAll() {
        handlers.clear()
    }

    /** Gets a custom view handler by [name], if one has been previously registered. */
    internal fun get(name: String): AirshipCustomViewHandler? {
        return handlers[name]
    }
}

/** Handler interface for Custom Views rendered in Scenes. */
public fun interface AirshipCustomViewHandler {

    /**
     * Called when a custom view should be created.
     *
     * @param context The context.
     * @param args The custom view arguments.
     *
     * @return The custom `View`.
     */
    public fun onCreateView(context: Context, args: AirshipCustomViewArguments): View
}

/**
 * Arguments for the custom view.
 *
 * @property properties The JSON data associated with this custom view.
 */
public class AirshipCustomViewArguments(
    public val name: String,
    public val properties: JsonMap,
    public val sizeInfo: SizeInfo,
    public val sceneController: SceneController
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipCustomViewArguments

        return properties == other.properties
    }

    override fun hashCode(): Int {
        return properties.hashCode()
    }

    public class SizeInfo(
        public val isAutoHeight: Boolean,
        public val isAutoWidth: Boolean,
    ) {
        override fun toString(): String {
            return "SizeInfo(isAutoHeight=$isAutoHeight, isAutoWidth=$isAutoWidth)"
        }
    }
}
