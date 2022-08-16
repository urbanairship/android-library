package com.urbanairship.android.layout

import androidx.annotation.MainThread
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.BaseModel

internal class ModelProvider @JvmOverloads constructor(
    private val store: ModelStore = ModelStore(),
    private val factory: ModelFactory = DefaultModelFactory(),
) {
    private val baseEnvironment = ModelEnvironment(this, emptyMap())

    @MainThread
    @JvmOverloads
    @Throws(ModelFactoryException::class)
    fun create(info: ViewInfo, environment: ModelEnvironment? = null): BaseModel =
        factory
            .create(info, baseEnvironment.extend(environment))
            // TODO: .also { store[it.viewId] = it } ?

    @MainThread
    fun get(viewId: Int): BaseModel? = store[viewId]

    fun clear() = store.clear()
}

internal data class ModelEnvironment(
    val modelProvider: ModelProvider,
    val state: Map<Int, Map<String, String>>
) {
    fun extend(other: ModelEnvironment?): ModelEnvironment {
        return other?.let {
            copy(
                state = this.state + other.state
            )
        } ?: this
    }
}

internal class ModelStore {

    private val models = mutableMapOf<Int, BaseModel>()

    operator fun get(viewId: Int): BaseModel? = models[viewId]

    operator fun set(viewId: Int, model: BaseModel) {
        models[viewId] = model
    }

    operator fun contains(viewId: Int): Boolean = models.containsKey(viewId)

    operator fun contains(model: BaseModel): Boolean = contains(model.viewId)

    fun clear() {
        // for (model in models.values) {
        // TODO:  model.clear() ?
        // }
        models.clear()
    }
}
