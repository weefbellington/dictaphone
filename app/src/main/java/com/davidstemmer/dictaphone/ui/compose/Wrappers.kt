package com.davidstemmer.dictaphone.ui.compose

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

typealias ContentFn = @Composable () -> Unit


@Composable
fun DisableWhen(condition: Boolean, content: ContentFn) {
    if (condition) { DisabledAlpha(content = content) }
    else { content() }
}

@Composable
fun RemoveWhen(condition: Boolean, content: ContentFn) {
    if (!condition) { content() }
}


@Composable
fun DisabledAlpha(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f), content = content)
}

@Composable
fun LowEmphasisAlpha(content: @Composable () -> Unit)  {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)) {
        content()
    }
}
