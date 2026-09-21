package com.kamisakyy.nanajoxkmama.core.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** iOS-style segmented control with animated thumb (site SegmentedControl). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp)
    ) {
        // animated thumb — spring interpolation between slots
        val thumbIndex by androidx.compose.animation.core.animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = androidx.compose.animation.spring(
                dampingRatio = 0.85f,
                stiffness = androidx.compose.animation.core.StiffnessMediumLow,
            ),
            label = "segThumb",
        )
        Box(
            Modifier
                .matchThumbFloat(options.size, thumbIndex)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == selectedIndex) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Positions a thumb at 1/n width with a FLOAT index (animated). */
private fun Modifier.matchThumbFloat(count: Int, index: Float): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth / count
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative((w * index).roundToInt(), 0)
    }
}

/** Pulsing 3-bar playing indicator (like the site). */
@Composable
fun PulseBars(color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "pulse")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0..2) {
            val h by t.animateFloat(
                initialValue = 4f, targetValue = 14f,
                animationSpec = infiniteRepeatable(
                    tween(380 + i * 90, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/** Equalizer-style progress used on the mini player. */
@Composable
fun TinyProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .height(3.dp)
            .fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(color)
        )
    }
}
