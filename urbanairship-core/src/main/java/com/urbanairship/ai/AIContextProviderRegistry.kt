/* Copyright Airship and Contributors */
package com.urbanairship.ai

import com.urbanairship.UALog
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/**
 * Holds the registered context providers, keyed by usage raw value, plus the fallback default
 * provider.
 */
internal class AIContextProviderRegistry {

    private fun interface AnyProvider {
        suspend fun fetch(subject: Any?): AIContext
    }

    private val providers = ConcurrentHashMap<String, AnyProvider>()

    @Volatile
    private var defaultProvider: AnyProvider? = null

    fun <Subject> setContextProvider(
        usage: AIUsage<Subject>,
        provider: AIContextProvider<Subject>?
    ) {
        if (provider == null) {
            providers.remove(usage.rawValue)
        } else {
            providers[usage.rawValue] = AnyProvider { subject ->
                @Suppress("UNCHECKED_CAST")
                provider.provideContext(subject as Subject)
            }
        }
    }

    fun setDefaultContextProvider(provider: AIDefaultContextProvider?) {
        defaultProvider = provider?.let { fallback -> AnyProvider { fallback.provideContext() } }
    }

    /**
     * Fetches the context registered for a usage, or [AIContext.EMPTY] when none is.
     *
     * A usage-specific provider wins outright; the default provider is only a fallback for
     * usages that have none. The two are never combined.
     *
     * The provider is app code on the path to displaying a feature, so a throw degrades to
     * empty context rather than taking that display down with it.
     */
    suspend fun fetchContext(usage: String, subject: Any?): AIContext {
        val provider = providers[usage] ?: defaultProvider ?: return AIContext.EMPTY
        return try {
            provider.fetch(subject)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClassCastException) {
            // Two features sharing a usage key with different subject types.
            UALog.e(e) { "AI context provider for $usage received an unexpected subject type" }
            AIContext.EMPTY
        } catch (e: Exception) {
            UALog.e(e) { "AI context provider for $usage failed" }
            AIContext.EMPTY
        }
    }
}
