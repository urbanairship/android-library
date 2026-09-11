/* Copyright Airship and Contributors */
package com.urbanairship.devapp.ai

import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.ModelSelector
import com.urbanairship.devapp.BuildConfig

/**
 * Bring-your-own-model testing for the AI features: routes every usage to [OpenAIModel] when a
 * key is configured, and supplies profile context for usages the app hasn't registered a
 * provider for.
 *
 * Drop a key in `local.properties` as `openai.apiKey=sk-...`, or export `OPENAI_API_KEY`
 * before building. With no key nothing is registered, so `$ai.current.text_input_inference`
 * stays false and a scene takes its non-AI path.
 */
internal object DevAI {

    fun register() {
        Airship.ai.setDefaultContextProvider { defaultContext() }

        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isEmpty()) {
            UALog.i { "No OpenAI key configured; AI evaluations will be skipped." }
            return
        }

        val model = OpenAIModel(apiKey)
        Airship.ai.setModelResolver { ModelSelector.Custom(model) }

        Airship.ai.setEvaluationObserver { record ->
            UALog.i { "AI evaluation [${record.usage}] ${record.outcome} in ${record.duration}" }
        }
    }

    /** Fallback context for any usage without a provider of its own. */
    private suspend fun defaultContext(): EvaluationContext {
        val items = mutableListOf(
            EvaluationContext.Item("User interests: cats"),
            EvaluationContext.Item("Customer since: 2019"),
            EvaluationContext.Item("Rental history: 15ft (2022), 20ft (2024)")
        )

        Airship.contact.namedUserId?.let {
            items.add(EvaluationContext.Item("Named user: $it"))
        }

        Airship.channel.tags.takeIf { it.isNotEmpty() }?.let { tags ->
            items.add(EvaluationContext.Item("Channel tags: ${tags.sorted().joinToString(", ")}"))
        }

        Airship.channel.id?.let {
            items.add(EvaluationContext.Item("Channel ID: $it"))
        }

        return EvaluationContext(items)
    }
}
