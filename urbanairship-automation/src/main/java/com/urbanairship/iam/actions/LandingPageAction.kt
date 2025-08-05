/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import android.net.Uri
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.UrlAllowList
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.HTML
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.push.PushMessage
import com.urbanairship.util.Clock
import com.urbanairship.util.UriUtils
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * Schedules a landing page to display ASAP.
 *
 * Accepted situations: [Action.SITUATION_PUSH_OPENED], [Action.SITUATION_WEB_VIEW_INVOCATION],
 * [Action.SITUATION_MANUAL_INVOCATION], [Action.SITUATION_AUTOMATION],
 * and [Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON].
 *
 * Accepted argument value types: URL defined as either a String or a Map containing the key
 * "url" that defines the URL, an optional "width", "height" in dps as an int or "fill" string,
 * an optional "aspect_lock" option as a boolean.
 *
 * The aspect_lock option guarantees that if the message does not fit, it will be resized at the
 * same aspect ratio defined by the provided width and height parameters.
 *
 *
 * Default Registration Names: ^p, landing_page_action
 */
public class LandingPageAction(
    private val scheduler: suspend (AutomationSchedule) -> Unit,
    private val allowListChecker: (String) -> Boolean,
    private val scheduleExtender: ScheduleExtender? = null,
    private val borderRadius: Float,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) : Action() {

    /**
     * Default constructor
     *
     * @param borderRadius: Optional border radius in points. Defaults to 2.
     * @param scheduleExtender: Optional extender. Can be used to modify the landing page action schedule.
     */
    @JvmOverloads
    public constructor(
        borderRadius: Float = DEFAULT_BORDER_RADIUS,
        scheduleExtender: ScheduleExtender? = null) :
            this(
                allowListChecker = { url: String ->
                    UAirship.shared().urlAllowList.isAllowed(url, UrlAllowList.Scope.OPEN_URL)
                },
                scheduler = { schedule: AutomationSchedule ->
                    InAppAutomation.shared().upsertSchedules(listOf(schedule))
                },
                scheduleExtender = scheduleExtender,
                borderRadius = borderRadius)

    /**
     * Checks if the situation is not `Action.SITUATION_PUSH_RECEIVED` or
     * `Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON`
     *
     * @param arguments The action arguments.
     * @return `true` if the action can perform with the arguments,
     * otherwise `false`.
     */
    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when(arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.MANUAL_INVOCATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON,
            Situation.AUTOMATION -> true
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val pushMessage: PushMessage? = arguments.metadata.getParcelable(ActionArguments.PUSH_MESSAGE_METADATA)
        val messageID = pushMessage?.sendId
        val args = LandingPageArgs.fromJson(arguments.value.toJsonValue(), allowListChecker)

        val message = InAppMessage(
            name = "Landing Page ${args.uri}",
            displayContent = InAppMessageDisplayContent.HTMLContent(
                HTML(
                    url = args.uri.toString(),
                    height = args.height,
                    width = args.width,
                    aspectLock = args.aspectLock,
                    requiresConnectivity = false,
                    borderRadius = borderRadius,
                    allowFullscreenDisplay = false
                )
            ),
            isReportingEnabled = messageID != null,
            displayBehavior = InAppMessage.DisplayBehavior.IMMEDIATE
        )

        var schedule = AutomationSchedule(
            identifier = messageID ?: UUID.randomUUID().toString(),
            data = AutomationSchedule.ScheduleData.InAppMessageData(message),
            triggers = listOf(AutomationTrigger.activeSession(1u)),
            priority = Int.MIN_VALUE,
            bypassHoldoutGroups = true,
            productId = PRODUCT_ID,
            queue = QUEUE,
            created = clock.currentTimeMillis().toULong()
        )

        scheduleExtender?.let { schedule = it.invoke(arguments, schedule) }

        runBlocking { scheduler.invoke(schedule) }

        return ActionResult.newEmptyResult()
    }

    public companion object {
        /** Default registry name */
        @JvmStatic
        public val DEFAULT_NAMES: List<String> = listOf("landing_page_action", "^p")

        /** Default border radius. */
        private const val DEFAULT_BORDER_RADIUS = 2f

        private const val PRODUCT_ID = "landing_page"
        private const val QUEUE = "landing_page"
    }

    private data class LandingPageArgs(
        val uri: Uri,
        val height: Long = 0,
        val width: Long = 0,
        val aspectLock: Boolean? = null
    ) {

        companion object {
            private const val URL_KEY = "url"
            private const val HEIGHT_KEY = "height"
            private const val WIDTH_KEY = "width"
            private const val ASPECT_LOCK_KEY = "aspect_lock"
            private const val ASPECT_LOCK_LEGACY_KEY = "aspectLock"

            @Throws(IllegalArgumentException::class)
            fun fromJson(value: JsonValue, isUrlAllowed: UrlChecker): LandingPageArgs {
                return if (value.isString) {
                    LandingPageArgs(parseUri(value, isUrlAllowed))
                } else {
                    val content = value.requireMap()
                    LandingPageArgs(
                        uri = parseUri(content.require(URL_KEY), isUrlAllowed),
                        height = content.optionalField(HEIGHT_KEY) ?: 0,
                        width = content.optionalField(WIDTH_KEY) ?: 0,
                        aspectLock = content.optionalField(ASPECT_LOCK_KEY)
                            ?: content.optionalField(ASPECT_LOCK_LEGACY_KEY)
                    )
                }
            }

            @Throws(IllegalArgumentException::class)
            fun parseUri(value: JsonValue, checker: UrlChecker): Uri {
                val stringValue = value.string
                if (stringValue.isNullOrEmpty()) {
                    throw IllegalArgumentException()
                }

                var parsed = UriUtils.parse(stringValue) ?: throw IllegalArgumentException()
                if (parsed.toString().isEmpty()) {
                    throw IllegalArgumentException()
                }

                if (parsed.scheme?.isEmpty() != false) {
                    parsed = Uri.parse("https://$parsed")
                }

                if (!checker.invoke(parsed.toString())) {
                    UALog.e { "Landing page URL is not allowed $parsed" }
                    throw IllegalArgumentException()
                }

                return parsed
            }
        }
    }
}

public typealias ScheduleExtender = (ActionArguments, AutomationSchedule) -> AutomationSchedule
private typealias UrlChecker = (String) -> Boolean
