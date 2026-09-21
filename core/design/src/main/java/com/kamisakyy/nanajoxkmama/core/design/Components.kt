package com.kamisakyy.nanajoxkmama.core.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap

/* ============ shared building blocks ============ */

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(base, hi, base),
                    start = Offset(x * size.width, 0f),
                    end = Offset((x + 1f) * size.width, size.height),
                )
                onDrawBehind { drawRect(brush) }
            }
    )
}

@Composable
fun ShimmerTrackRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ShimmerBox(Modifier.size(52.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(Modifier.width(190.dp).height(13.dp))
            ShimmerBox(Modifier.width(120.dp).height(11.dp))
        }
    }
}

@Composable
fun ShimmerCard(modifier: Modifier = Modifier) {
    ShimmerBox(modifier, RoundedCornerShape(16.dp))
}

/** Polished black card (Material 3) with subtle press animation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PressableCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .composed {
                if (onClick != null) {
                    combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.material3.ripple(bounded = true, radius = 240.dp),
                        onClickLabel = null,
                        role = null,
                        onLongClickLabel = null,
                        onLongClick = {
                            if (onLongClick != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            }
                        },
                        onClick = onClick,
                    )
                } else Modifier
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { content() }
}

@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    val c = tagColor(text)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.composed {
                    combinedClickableNoIndication(onClick = onAction).padding(4.dp)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoIndication(onClick: () -> Unit): Modifier = composed {
    combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    maxPx: Int = 640,
) {
    // ЭКОНОМИЯ: декодируем максимум maxPx px — обложки больше не грузятся в полный размер.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val model = remember(url, maxPx) {
        coil3.request.ImageRequest.Builder(ctx)
            .data(url)
            .size(maxPx, maxPx)
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    lottieAsset: String? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (lottieAsset != null) LottieEmpty(lottieAsset) else EmptyGlyph()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyGlyph() {
    val c = MaterialTheme.colorScheme.surfaceContainerHighest
    val a = MaterialTheme.colorScheme.primary
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)).background(c.copy(alpha = 0.5f)))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(c))
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(a.copy(alpha = 0.85f)))
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String = "Повторить",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text("!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(14.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

/** iOS-style primary CTA — white pill, black label, spring micro-press. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
        ),
        label = "pill",
    )
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        icon?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
