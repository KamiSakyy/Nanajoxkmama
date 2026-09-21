package com.kamisakyy.nanajoxkmama.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.common.greeting
import com.kamisakyy.nanajoxkmama.core.common.seasonLabel
import com.kamisakyy.nanajoxkmama.core.design.AnimeCard
import com.kamisakyy.nanajoxkmama.core.design.Artwork
import com.kamisakyy.nanajoxkmama.core.design.EmptyState
import com.kamisakyy.nanajoxkmama.core.design.ErrorState
import com.kamisakyy.nanajoxkmama.core.design.SectionHeader
import com.kamisakyy.nanajoxkmama.core.design.SegmentedControl
import com.kamisakyy.nanajoxkmama.core.design.ShimmerCard
import com.kamisakyy.nanajoxkmama.core.design.ShimmerTrackRow
import com.kamisakyy.nanajoxkmama.core.design.TrackTile
import com.kamisakyy.nanajoxkmama.core.model.Track

/**
 * Home — website App shell port: greeting, Fresh (+period toggle), Random (with
 * extras), Latest, season mix, curated mixes, decade shortcuts.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlayAll: (List<Track>, Int) -> Unit,
    onOpenMix: (String) -> Unit,
    onOpenDecade: (Int) -> Unit,
    onOpenAnime: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val settings by viewModel.settings.snapshot.collectAsState(initial = null)
    val haptics = LocalHapticFeedback.current

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = viewModel::refresh) {
        when {
            ui.loading && ui.feed == null -> LazyColumn { items(6) { ShimmerTrackRow() } }
            ui.error != null && ui.feed == null -> ErrorState(ui.error!!, onRetry = viewModel::load)
            else -> {
                val feed = ui.feed ?: return@PullToRefreshBox
                LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("AniBeat", style = MaterialTheme.typography.displaySmall)
                            }
                            IconButton(onClick = viewModel::refresh) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (feed.fresh.isNotEmpty()) {
                        item {
                            SectionHeader("Свежие релизы")
                            SegmentedControl(
                                options = listOf("Сегодня", "Неделя", "Всё"),
                                selectedIndex = when (settings?.period) {
                                    "today" -> 0; "week" -> 1; else -> 2
                                },
                                onSelect = { i -> viewModel.setPeriod(listOf("today", "week", "all")[i]) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(feed.fresh.size) { i ->
                                    val t = feed.fresh[i]
                                    TrackTile(t, onClick = { onPlayAll(feed.fresh, i) }, onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayAll(feed.fresh, i)
                                    })
                                }
                            }
                        }
                    }
                    if (feed.random.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader("Случайные подборки")
                            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(feed.random.size) { i ->
                                    val t = feed.random[i]
                                    TrackTile(t, onClick = { onPlayAll(feed.random, i) }, onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayAll(feed.random, i)
                                    })
                                }
                            }
                        }
                    }
                    if (feed.latest.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader("Последние темы")
                            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(feed.latest.size) { i ->
                                    val t = feed.latest[i]
                                    TrackTile(t, onClick = { onPlayAll(feed.latest, i) }, onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayAll(feed.latest, i)
                                    })
                                }
                            }
                        }
                    }
                    if (feed.season.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(18.dp))
                            val (y, s) = com.kamisakyy.nanajoxkmama.core.common.currentSeasonSite()
                            SectionHeader("Сезон — " + seasonLabel(y, s))
                            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(feed.season.size) { i ->
                                    val t = feed.season[i]
                                    TrackTile(t, onClick = { onPlayAll(feed.season, i) }, onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayAll(feed.season, i)
                                    })
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionHeader("Миксы")
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            feed.mixes.forEach { m ->
                                MixRow(m, feed.mixCovers[m.id], onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenMix(m.id)
                                })
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionHeader("Десятилетия")
                        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(feed.decadeCovers.entries.toList().size) { i ->
                                val e = feed.decadeCovers.entries.toList()[i]
                                DecadeCard(e.key, e.value, onClick = { onOpenDecade(e.key) })
                            }
                        }
                        Spacer(Modifier.height(120.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MixRow(m: com.kamisakyy.nanajoxkmama.core.common.Curated.Mix, cover: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(), indication = ripple(), onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(cover, Modifier.size(56.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(m.title, style = MaterialTheme.typography.titleSmall)
            Text(m.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DecadeCard(year: Int, cover: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(), indication = ripple(), onClick = onClick),
    ) {
        Box {
            Artwork(cover, Modifier.size(150.dp), RoundedCornerShape(16.dp))
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$year", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
