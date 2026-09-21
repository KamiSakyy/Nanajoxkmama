package com.kamisakyy.nanajoxkmama.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * iOS-style swipe actions on a track row:
 *  • swipe right → «Играть следующим»
 *  • swipe left  → «Удалить» (history / playlist)
 * Spring physics + colour fade on the revealed action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeTrackRow(
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enableGestures: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPlayNext()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.32f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enableGestures,
        enableDismissFromEndToStart = enableGestures,
        backgroundContent = {
            val dir = state.dismissDirection
            val c by animateColorAsState(
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                label = "swipeBg",
            )
            Box(
                Modifier.fillMaxSize().padding(horizontal = 20.dp).background(c, MaterialTheme.shapes.medium),
                contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Rounded.PlaylistPlay, contentDescription = "Играть следующим", tint = MaterialTheme.colorScheme.primary)
                } else if (dir == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        content = { content() },
    )
}
