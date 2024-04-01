package com.davidstemmer.dictaphone.ui.compose

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.graphics.ColorUtils
import com.davidstemmer.dictaphone.R
import com.davidstemmer.dictaphone.ui.data.AudioState


@Composable
fun RecordAudioButton(modifier: Modifier = Modifier,
                      audioState: AudioState,
                      hasRecordAudioPermission: Boolean,
                      onRecord: RecordingCallback) {

    val callback =
        if (audioState is AudioState.Playback) null
        else onRecord

    val defaultContainerColor = FloatingActionButtonDefaults.containerColor
    val defaultContentColor = contentColorFor(FloatingActionButtonDefaults.containerColor)

    val contentColor = if (audioState == AudioState.Recording) {
        val infiniteTransition = rememberInfiniteTransition(label = "infinite")
        val endColor = Color.Red
        infiniteTransition.animateColor(
            initialValue = defaultContentColor,
            targetValue = endColor,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "contentColor"
        )
    } else remember { mutableStateOf(defaultContentColor) }

    val containerColor = if (audioState == AudioState.Recording) {
        val infiniteTransition = rememberInfiniteTransition(label = "infinite")
        val overlayColor = Color(ColorUtils.setAlphaComponent(defaultContentColor.toArgb(), 50))
        val endColor = overlayColor.compositeOver(defaultContainerColor)
        infiniteTransition.animateColor(
            initialValue = defaultContainerColor,
            targetValue = endColor,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "containerColor"
        )
    } else remember { mutableStateOf(defaultContainerColor) }


    FloatingActionButton(
        modifier = modifier,
        containerColor = containerColor.value,
        contentColor = contentColor.value,
        onClick = { if (callback != null) { callback(hasRecordAudioPermission) } },
        content = {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.baseline_mic_24),
                contentDescription = stringResource(R.string.record_audio_content_description)
            )
        }
    )
}