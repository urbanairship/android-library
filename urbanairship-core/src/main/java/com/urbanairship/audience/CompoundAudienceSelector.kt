/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireList

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class CompoundAudienceSelector: JsonSerializable {

    /**
     * Atomic selector. Defines an actual audience selector.
     * @audience The audience selector.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class Atomic(val audience: AudienceSelector): CompoundAudienceSelector()

    /**
     * NOT selector. Negates the result.
     * @param selector The compound selector.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class Not(val selector: CompoundAudienceSelector): CompoundAudienceSelector()

    /**
     * AND selector. All selectors have to evaluate true to match.
     * @param selectors The list of compound selectors to evaluate. If empty, evaluates to `true`.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class And(val selectors: List<CompoundAudienceSelector>): CompoundAudienceSelector()

    /**
     * OR selector. At least once selector has to evaluate true to match.
     * @param selectors The list of compound selectors to evaluate. If empty, evaluates to `false`.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class Or(val selectors: List<CompoundAudienceSelector>): CompoundAudienceSelector()

    private enum class SelectorType(val jsonValue: String): JsonSerializable {
        ATOMIC("atomic"),
        NOT("not"),
        AND("and"),
        OR("or");

        companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): SelectorType {
                val content = value.requireString()
                return entries.firstOrNull { it.jsonValue == content }
                    ?: throw JsonException("Invalid button layout $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)
    }

    public companion object {
        private const val TYPE = "type"
        private const val AUDIENCE = "audience"
        private const val SELECTOR = "selector"
        private const val SELECTORS = "selectors"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): CompoundAudienceSelector {
            val content = value.requireMap()
            val type = SelectorType.fromJson(content.require(TYPE))

            return when(type) {
                SelectorType.ATOMIC -> Atomic(AudienceSelector.fromJson(content.require(AUDIENCE)))
                SelectorType.NOT -> Not(fromJson(content.require(SELECTOR)))
                SelectorType.AND -> And(content.requireList(SELECTORS).map(::fromJson))
                SelectorType.OR -> Or(content.requireList(SELECTORS).map(::fromJson))
            }
        }

        /*
        * Combines old and new selector into a CompoundAudienceSelector
        * - Parameters:
        *     - compoundAudienceSelector: An optional `CompoundAudienceSelector`.
        *     - deviceAudience: An optional `AudienceSelector`.
        * - Returns: A `CompoundAudienceSelector` if either provided selector
        *  is non null, otherwise nil.
        */
        public fun combine(
            compoundAudienceSelector: CompoundAudienceSelector?,
            deviceAudience: AudienceSelector?
        ): CompoundAudienceSelector? {
            if (compoundAudienceSelector != null && deviceAudience != null) {
                return And(listOf(Atomic(deviceAudience), compoundAudienceSelector))
            } else if (compoundAudienceSelector != null) {
                return compoundAudienceSelector
            } else if (deviceAudience != null) {
                return Atomic(deviceAudience)
            } else {
                return null
            }
        }
    }

    override fun toJsonValue(): JsonValue {
        return when(this) {
            is And -> jsonMapOf(
                TYPE to SelectorType.AND,
                SELECTORS to selectors
            )
            is Atomic -> jsonMapOf(
                TYPE to SelectorType.ATOMIC,
                AUDIENCE to audience
            )
            is Not -> jsonMapOf(
                TYPE to SelectorType.NOT,
                SELECTOR to selector
            )
            is Or -> jsonMapOf(
                TYPE to SelectorType.OR,
                SELECTORS to selectors
            )
        }
            .toJsonValue()
    }

    public suspend fun evaluate(
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider,
        hashChecker: HashChecker
    ): AirshipDeviceAudienceResult {
        return when(this) {
            is Atomic -> audience.evaluate(newEvaluationDate, infoProvider, hashChecker)
            is Not -> selector.evaluate(newEvaluationDate, infoProvider, hashChecker).negate()
            is And -> {
                if (selectors.isEmpty()) {
                    return AirshipDeviceAudienceResult.match
                }

                val results = mutableListOf<AirshipDeviceAudienceResult>()
                for (selector in selectors) {
                    val partialResult = selector.evaluate(newEvaluationDate, infoProvider, hashChecker)
                    results.add(partialResult)
                    if (!partialResult.isMatch) {
                        break
                    }
                }

                return AirshipDeviceAudienceResult.reduced(results) { first, second ->
                    first && second
                }
            }
            is Or -> {
                if (selectors.isEmpty()) {
                    return AirshipDeviceAudienceResult.miss
                }

                val results = mutableListOf<AirshipDeviceAudienceResult>()
                for (selector in selectors) {
                    val partialResult = selector.evaluate(newEvaluationDate, infoProvider, hashChecker)
                    results.add(partialResult)
                    if (partialResult.isMatch) {
                        break
                    }
                }

                return AirshipDeviceAudienceResult.reduced(results) { first, second ->
                    first || second
                }
            }
        }
    }
}
