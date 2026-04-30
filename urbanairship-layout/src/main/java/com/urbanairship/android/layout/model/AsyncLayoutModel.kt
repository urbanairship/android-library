package com.urbanairship.android.layout.model

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.android.layout.ModelFactory
import com.urbanairship.android.layout.assets.AssetCacheManager
import com.urbanairship.android.layout.assets.DefaultAssetFileManager
import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.AsyncViewControllerInfo
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.util.CachedImage
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.android.layout.util.NonExtendableImageCache
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.android.layout.view.AsyncLayoutView
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.util.UAHttpStatusUtil
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Model for asynchronously fetched layout content. */
internal class AsyncLayoutModel(
    viewInfo: AsyncViewControllerInfo,
    private val asyncState: SharedState<State.AsyncView>,
    environment: ModelEnvironment,
    properties: ModelProperties,
    private val factory: ModelFactory,
    private val requestSession: RequestSession = Airship.runtimeConfig.requestSession,
    private val contactIdFetcher: () -> String? = { Airship.contact.lastContactId },
    private val channelIdFetcher: () -> String? = { Airship.channel.id }
) : BaseModel<AsyncLayoutView, AsyncViewControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    private val _state = MutableStateFlow<ContentToDisplay?>(null)
    internal val state: Flow<ContentToDisplay> = _state.asStateFlow().filterNotNull()

    private var viewEnvironment: ViewEnvironment? = null
    private var itemProperties: ItemProperties? = null
    private var cacheManager: AssetCacheManager? = null

    init {
        environment.layoutEvents
            .filter { it is LayoutEvent.AsyncViewReload || it is LayoutEvent.Finish }
            .onEach { event ->
                when (event) {
                    is LayoutEvent.AsyncViewReload -> {
                        val viewEnvironment = viewEnvironment ?: return@onEach
                        loadContent(viewEnvironment, itemProperties)
                    }
                    is LayoutEvent.Finish -> { cacheManager?.clearCache(viewInfo.identifier) }
                    else -> {}
                }
            }
            .launchIn(modelScope)
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ): AsyncLayoutView {

        this.viewEnvironment = viewEnvironment
        this.itemProperties = itemProperties

        if (this.cacheManager == null) {
            this.cacheManager = AssetCacheManager(
                context = context,
                fileManager = DefaultAssetFileManager(
                    context = context,
                    rootFolder = ASSETS_ROOT
                )
            )
        }

        pushViewToDisplay(
            info = viewInfo.placeholder,
            viewEnvironment = viewEnvironment,
            itemProperties = itemProperties
        )

        modelScope.launch {
            loadContent(viewEnvironment, itemProperties)
        }

        return AsyncLayoutView(context, this).apply {
            id = viewId
        }
    }

    private suspend fun loadContent(
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) {
        val request = makeRequest(viewInfo.request) ?: run {
            onError(State.AsyncView.Error.ErrorData.Client)
            return
        }

        asyncState.update { current ->
            State.AsyncView.Loading(
                identifier = current.identifier
            )
        }

        val retry = viewInfo.retryPolicy
        for (attempt in 0 until retry.maxRetries + 1) {
            delay(calculateBackoff(attempt, retry))

            val response = requestSession.execute(request) { status, _, body ->
                if (!UAHttpStatusUtil.inSuccessRange(status)) {
                    onError(State.AsyncView.Error.ErrorData.Server(status))
                    null
                } else {
                    try {
                        val json = JsonValue.parseString(body)
                        ViewInfo.viewInfoFromJson(json.requireMap())
                    } catch (ex: Exception) {
                        onError(State.AsyncView.Error.ErrorData.Client)
                        throw ex
                    }
                }
            }

            if (!response.isSuccessful) {
                response.exception?.let {
                    UALog.e(it) { "Failed to load layout" }
                }
                val shouldRetry =
                    response.status?.let(UAHttpStatusUtil::inServerErrorRange) ?: false
                if (!shouldRetry) {
                    break
                }

                continue
            }

            val layout = response.value ?: break
            val imageCache = viewEnvironment.imageCache()
            if (imageCache != null) {
                cacheAssets(layout)?.let(imageCache::tryAddChild)
            }
            reportLayoutLoaded()
            pushViewToDisplay(layout, viewEnvironment, itemProperties)
            break
        }
    }

    private suspend fun cacheAssets(layout: ViewInfo): ImageCache? {
        val assets = UrlInfo.from(layout).mapNotNull {
            when(it.type) {
                UrlInfo.UrlType.IMAGE -> it.url
                else -> null
            }
        }

        if (assets.isEmpty()) {
            return null
        }

        val manager = cacheManager ?: run {
            UALog.d { "Failed to cache assets, no assets cache provided" }
            return null
        }

        val storage = manager.cacheAsset(viewInfo.identifier, assets).getOrNull() ?: return null

        return NonExtendableImageCache { url ->
            storage.cacheUri(url)?.path?.let {
                val size = storage.getMediaSize(url)

                CachedImage(
                    path = it,
                    size =  if (size.width > 0 && size.height > 0) {
                        size
                    } else {
                        null
                    }
                )
            }
        }
    }

    /**
     * Calculates exponential backoff duration for retry attempts.
     * Formula: initialBackoff * 2^(attempt-1), capped at maxBackoff
     */
    private fun calculateBackoff(attempt: Int, retryPolicy: AsyncViewControllerInfo.RetryPolicy): Duration {
        if (attempt == 0) {
            return 0.seconds
        }

        // Exponential backoff: initialBackoff * 2^(attempt-1)
        val exponentialBackoff = retryPolicy.initialBackoff * (1 shl (attempt - 1))
        return minOf(exponentialBackoff, retryPolicy.maxBackoff)
    }

    private fun makeRequest(info: AsyncViewControllerInfo.Request): Request? {
        try {
            val uri = info.url.toUri()
            val auth = when(info.auth) {
                AsyncViewControllerInfo.Request.Auth.APP -> RequestAuth.GeneratedAppToken
                AsyncViewControllerInfo.Request.Auth.CHANNEL -> {
                    val token = channelIdFetcher() ?: return null
                    RequestAuth.ChannelTokenAuth(token)
                }
                AsyncViewControllerInfo.Request.Auth.CONTACT -> {
                    val token = contactIdFetcher() ?: return null
                    RequestAuth.ContactTokenAuth(token)
                }
                null -> null
            }

            return Request(
                url = uri,
                auth = auth,
                method = "GET"
            )
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to generate layout request"}
            return null
        }
    }

    private fun pushViewToDisplay(
        info: ViewInfo,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?,
    ) {
        val model = runCatching {
            factory.create(
                info = info,
                environment = environment,
                parentLayoutState = environment.layoutState
            )
        }.getOrElse {
            UALog.e(it) { "Failed to inflate async layout JSON" }
            asyncState.update { current ->
                State.AsyncView.Error(
                    identifier = current.identifier,
                    data = State.AsyncView.Error.ErrorData.Client
                )
            }
            onError(State.AsyncView.Error.ErrorData.Client)
            return@pushViewToDisplay
        }

        _state.update { ContentToDisplay(model, viewEnvironment, itemProperties) }
    }

    private fun onError(data: State.AsyncView.Error.ErrorData) {
        asyncState.update { current ->
            State.AsyncView.Error(
                identifier = current.identifier,
                data = data
            )
        }
    }

    private fun reportLayoutLoaded(data: JsonValue = JsonValue.NULL) {
        asyncState.update { current ->
            State.AsyncView.Loaded(
                identifier = current.identifier,
                data = data
            )
        }
    }

    internal data class ContentToDisplay(
        val model: AnyModel,
        val viewEnvironment: ViewEnvironment,
        val itemProperties: ItemProperties?
    )

    private companion object {
        private const val ASSETS_ROOT = "com.airship.layout.assets"
    }
}
