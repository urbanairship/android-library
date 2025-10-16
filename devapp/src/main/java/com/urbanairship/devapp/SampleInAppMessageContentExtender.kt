package com.urbanairship.devapp

import com.urbanairship.Airship
import com.urbanairship.automation.InAppAutomation
import com.urbanairship.automation.inAppAutomation
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.InAppMessageContentExtender
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.optionalField

class SampleInAppMessageContentExtender : InAppMessageContentExtender {

    override fun extend(message: InAppMessage): InAppMessageDisplayContent =
        when (val originalContent = message.displayContent) {
            is InAppMessageDisplayContent.HTMLContent -> {
                if (message.isSquareView()) {
                    val size = with(originalContent.html) {
                        width.coerceAtLeast(height).takeUnless { it <= 0 } ?: DEFAULT_SIZE
                    }
                    val extendedContent = originalContent.html.copy(width = size, height = size)
                    originalContent.copy(html = extendedContent)
                } else {
                    originalContent
                }
            }

            else -> originalContent
        }

    private fun InAppMessage.isSquareView(): Boolean =
        extras?.optionalField<String>("squareview") == "true"

    companion object {
        private const val DEFAULT_SIZE = 350L

        fun register() {
            Airship.inAppAutomation.inAppMessaging.messageContentExtender = SampleInAppMessageContentExtender()
        }
    }
}
