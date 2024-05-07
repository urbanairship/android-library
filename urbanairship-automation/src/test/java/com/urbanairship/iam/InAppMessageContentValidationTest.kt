package com.urbanairship.iam

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.Fullscreen
import com.urbanairship.iam.content.HTML
import com.urbanairship.iam.content.Modal
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.iam.info.InAppMessageMediaInfo
import com.urbanairship.iam.info.InAppMessageTextInfo
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class InAppMessageContentValidationTest {
    private lateinit var validHeading: InAppMessageTextInfo
    private lateinit var validBody: InAppMessageTextInfo
    private lateinit var validMedia: InAppMessageMediaInfo
    // Assuming invalid media would have an invalid URL or type, but keeping it simple here
    private lateinit var validButtonLabel: InAppMessageTextInfo
    private lateinit var validButton: InAppMessageButtonInfo

    private val validText = "Valid Text"
    private val validIdentifier = "d17a055c-ed67-4101-b65f-cd28b5904c84"
    private val validURL = "some://image.png"
    private val validColor = InAppMessageColor(Color.WHITE)
    private val validFontFam = listOf("sans-serif")

    private lateinit var emptyHeading: InAppMessageTextInfo
    private lateinit var emptyBody: InAppMessageTextInfo
    private lateinit var emptyMedia: InAppMessageMediaInfo
    private lateinit var emptyButtonLabel: InAppMessageTextInfo
    private lateinit var emptyButton: InAppMessageButtonInfo
    private lateinit var validVideoMedia: InAppMessageMediaInfo
    private lateinit var validYoutubeMedia: InAppMessageMediaInfo

    @Before
    public fun setup() {
        // Valid components
        validHeading = InAppMessageTextInfo(text = validText,  color = validColor,  size = 22.0F,  fontFamilies = validFontFam,  alignment = InAppMessageTextInfo.Alignment.CENTER)

        validBody = InAppMessageTextInfo(text = validText, color = validColor, size = 16.0F, fontFamilies = validFontFam, alignment = InAppMessageTextInfo.Alignment.CENTER)
        validMedia = InAppMessageMediaInfo(url = validURL, type = InAppMessageMediaInfo.MediaType.IMAGE, description = validText)
        validButtonLabel = InAppMessageTextInfo(text = validText, color = validColor, size = 10F, fontFamilies = validFontFam, style = listOf(
            InAppMessageTextInfo.Style.BOLD))
        validButton = InAppMessageButtonInfo(identifier = validIdentifier, label = validButtonLabel, actions = emptyMap(), backgroundColor = validColor, borderColor = validColor, borderRadius = 2F)

        // Empty components
        emptyHeading = InAppMessageTextInfo(text = "", color = validColor, size = 22.0F, fontFamilies = validFontFam, alignment = InAppMessageTextInfo.Alignment.CENTER)
        emptyBody = InAppMessageTextInfo(text = "", color = validColor, size = 16.0F, fontFamilies = validFontFam, alignment = InAppMessageTextInfo.Alignment.CENTER)
        emptyMedia = InAppMessageMediaInfo(url = "", type = InAppMessageMediaInfo.MediaType.IMAGE, description = "")
        emptyButtonLabel = InAppMessageTextInfo(text = "", color = validColor, size = 10F, fontFamilies = validFontFam, style = listOf(
            InAppMessageTextInfo.Style.BOLD))
        emptyButton = InAppMessageButtonInfo(identifier = "", label = validButtonLabel, actions = emptyMap(), backgroundColor = validColor, borderColor = validColor, borderRadius = 2F)

        validVideoMedia = InAppMessageMediaInfo(url = validURL, type = InAppMessageMediaInfo.MediaType.VIDEO, description = validText)
        validYoutubeMedia = InAppMessageMediaInfo(url = validURL, type = InAppMessageMediaInfo.MediaType.VIDEO, description = validText)
    }

    @Test
    public fun testBanner() {
        val result = Banner(
            heading =  validHeading,
            body =  validBody,
            media =  validMedia,
            buttons =  listOf(validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Banner.Template.MEDIA_LEFT,
            backgroundColor =  validColor,
            dismissButtonColor =  validColor,
            borderRadius =  5F,
            duration =  100,
            placement = Banner.Placement.TOP
        ).validate()
        assertTrue(result)
    }

    @Test
    public fun testInvalidBanner() {
        // No heading or body
        val noHeaderOrBodyContent = Banner(
            heading = null,
            body = null,
            media = validMedia,
            buttons = listOf(validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Banner.Template.MEDIA_LEFT,
            backgroundColor = validColor,
            dismissButtonColor = validColor,
            borderRadius = 5F,
            duration = 100,
            placement = Banner.Placement.TOP
        )

        val tooManyButtons = Banner(
            heading = validHeading,
            body = validBody,
            media = validYoutubeMedia,
            buttons = listOf(validButton, validButton, validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Banner.Template.MEDIA_LEFT,
            backgroundColor = validColor,
            dismissButtonColor = validColor,
            borderRadius = 5F,
            duration = 100,
            placement = Banner.Placement.TOP
        )

        assertFalse(noHeaderOrBodyContent.validate())
        assertFalse(tooManyButtons.validate())
    }

    @Test
    public fun testModal() {
        val valid = Modal(
            heading = validHeading,
            body = validBody,
            media = validMedia,
            footer = validButton,
            buttons = listOf(validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Modal.Template.MEDIA_HEADER_BODY,
            dismissButtonColor = validColor,
            backgroundColor = validColor,
            allowFullscreenDisplay = true
        )

        assertTrue(valid.validate())
    }

    @Test
    public fun testInvalidModal() {
        val emptyHeadingAndBody = Modal(
            heading = emptyHeading,
            body = emptyBody,
            media = validMedia,
            footer = validButton,
            buttons = listOf(validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Modal.Template.MEDIA_HEADER_BODY,
            dismissButtonColor = validColor,
            backgroundColor = validColor,
            allowFullscreenDisplay = true
        )

        val tooManyButtons = Modal(
            heading = emptyHeading,
            body = emptyBody,
            media = validMedia,
            footer = validButton,
            buttons = listOf(validButton, validButton, validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Modal.Template.MEDIA_HEADER_BODY,
            dismissButtonColor = validColor,
            backgroundColor = validColor,
            allowFullscreenDisplay = true
        )

        assertFalse(emptyHeadingAndBody.validate())
        assertFalse(tooManyButtons.validate())
    }

    @Test
    public fun testFullscreen() {
        val valid = Fullscreen(
            heading = validHeading,
            body = validBody,
            media = validMedia,
            footer = validButton,
            buttons = listOf(validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Fullscreen.Template.MEDIA_HEADER_BODY,
            dismissButtonColor = validColor,
            backgroundColor = validColor
        )

        assertTrue(valid.validate())
    }

    @Test
    public fun testInvalidFullscreen() {
        val emptyHeadingAndBody = Fullscreen(
            heading = emptyHeading,
            body = emptyBody,
            media = validMedia,
            footer = validButton,
            buttons = listOf(validButton, validButton, validButton, validButton, validButton, validButton),
            buttonLayoutType = InAppMessageButtonLayoutType.STACKED,
            template = Fullscreen.Template.MEDIA_HEADER_BODY,
            dismissButtonColor = validColor,
            backgroundColor = validColor)

        assertFalse(emptyHeadingAndBody.validate())
    }

    @Test
    public fun testHTML() {
        val valid = HTML(
            url = validURL,
            height = 100,
            width = 100,
            aspectLock = true,
            requiresConnectivity = true,
            dismissButtonColor = validColor,
            backgroundColor = validColor,
            borderRadius = 5F,
            allowFullscreenDisplay = true
        )

        assertTrue(valid.validate())
    }

    @Test
    public fun testInvalidHTML() {
        val emptyURL = HTML(
            url = "",
            height = 100,
            width = 100,
            aspectLock = true,
            requiresConnectivity = true,
            dismissButtonColor = validColor,
            backgroundColor = validColor,
            borderRadius = 5F,
            allowFullscreenDisplay = true
        )

        assertFalse(emptyURL.validate())
    }

    @Test
    public fun testTextInfo() {
        assertTrue(validHeading.validate())
        assertTrue(validBody.validate())
        assertFalse(emptyHeading.validate())
        assertFalse(emptyBody.validate())
    }

    @Test
    public fun testButtonInfo() {
        assertTrue(validButton.validate())
        assertFalse(emptyButton.validate())
    }
}
