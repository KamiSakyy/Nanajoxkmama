package com.kamisakyy.nanajoxkmama.feature.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.design.Artwork
import com.kamisakyy.nanajoxkmama.core.design.PulseBars
import com.kamisakyy.nanajoxkmama.core.design.TagChip
import com.kamisakyy.nanajoxkmama.core.model.Track
import kotlin.math.roundToInt

/**
 * Playlist editor — drag-and-drop reorder (long-press + drag) + multi-select.
 * iOS-level: lifted card while dragging, haptics on grab, spring settle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderTrackList(
    tracks: List<Track>,
    currentId: String?,
    selection: Set<String>?,
    onSelectionToggle: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onLongPress: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 68f // dp→px approximated below via px value at drag time

    LazyColumn(modifier, state = listState) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            val dragging = draggingIndex == index
            val offsetPx = if (dragging) dragOffset else 0f
            Box(
                Modifier
                    .graphicsLayer {
                        translationY = offsetPx
                        scaleX = if (dragging) 1.03f else 1f
                        scaleY = if (dragging) 1.03f else 1f
                    }
                    .shadow(if (dragging) 10.dp else 0.dp, RoundedCornerShape(14.dp))
                    .pointerInput(tracks.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { _, delta ->
                                dragOffset += delta.y
                                val threshold = 72.dp.toPx()
                                if (dragOffset > threshold && draggingIndex != null) {
                                    val from = draggingIndex!!
                                    if (from < tracks.size - 1) {
                                        onMove(from, from + 1)
                                        draggingIndex = from + 1
                                        dragOffset -= threshold
                                    }
                                } else if (dragOffset < -threshold && draggingIndex != null) {
                                    val from = draggingIndex!!
                                    if (from > 0) {
                                        onMove(from, from - 1)
                                        draggingIndex = from - 1
                                        dragOffset += threshold
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                        )
                    },
            ) {
                val inSelection = selection != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh
                            else if (selection?.contains(track.id) == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.background,
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                if (inSelection) onSelectionToggle(track.id)
                                else onPlay(tracks, index)
                            },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress(track)
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DragIndicator, contentDescription = "Перетащить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Artwork(track.coverSmall ?: track.cover, Modifier.size(48.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagChip(track.themeTag)
                            Spacer(Modifier.width(8.dp))
                            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        }
                        Text(track.animeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    if (inSelection) {
                        Checkbox(checked = selection.contains(track.id), onCheckedChange = { onSelectionToggle(track.id) })
                    } else if (track.id == currentId) {
                        PulseBars()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}
