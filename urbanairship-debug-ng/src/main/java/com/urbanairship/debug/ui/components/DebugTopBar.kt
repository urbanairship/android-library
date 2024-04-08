package com.urbanairship.debug.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.urbanairship.debug.AirshipDebug

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AirshipDebug.TopAppBar(
    title: String
) {
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = title,
                label = "AirshipDebug.TopAppBar",
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    val fadeIn = fadeIn(animationSpec = tween(220, delayMillis = 90))
                    val scaleIn = scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))
                    val fadeOut = fadeOut(animationSpec = tween(90))

                    (fadeIn + scaleIn).togetherWith(fadeOut)
                }
            ) { text ->
                Text(text = text)
            }
        }
    )
}