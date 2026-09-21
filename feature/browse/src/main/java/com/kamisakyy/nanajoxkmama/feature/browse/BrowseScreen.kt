package com.kamisakyy.nanajoxkmama.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.kamisakyy.nanajoxkmama.core.common.Curated
import com.kamisakyy.nanajoxkmama.core.common.seasonRu
import com.kamisakyy.nanajoxkmama.core.design.Artwork
import com.kamisakyy.nanajoxkmama.core.design.EmptyState
import com.kamisakyy.nanajoxkmama.core.design.ErrorState
import com.kamisakyy.nanajoxkmama.core.design.ShimmerCard

/** Catalog browser — season/decade/genre grid (site BrowseTab + year shortcuts). */
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onOpenAnime: (String) -> Unit,
    onOpenDecade: (Int) -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Curated.SEASONS.forEach { s ->
                FilterChip(
                    selected = ui.season == s && ui.mode == "season",
                    onClick = { viewModel.setSeason(ui.year, s) },
                    label = { Text(seasonRu(s)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = false,
                onClick = { viewModel.setSeason(ui.year - 1, ui.season) },
                label = { Text("${ui.year - 1}") },
            )
            FilterChip(
                selected = false,
                onClick = { viewModel.setSeason(ui.year + 1, ui.season) },
                label = { Text("${ui.year + 1}") },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Curated.DECADES.forEach { d ->
                FilterChip(selected = ui.mode == "decade" && ui.year == d, onClick = { onOpenDecade(d) }, label = { Text("$d") })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.mode == "genre", onClick = { onOpenGenre("action") }, label = { Text("Жанры →") })
        }

        when {
            ui.loading && ui.items.isEmpty() -> LazyVerticalGrid(GridCells.Adaptive(108.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(8) { ShimmerCard(Modifier.height(160.dp)) }
            }
            ui.error != null && ui.items.isEmpty() -> ErrorState(ui.error!!, onRetry = { viewModel.loadSeason(true) })
            ui.items.isEmpty() -> EmptyState("Пусто", "Выбери сезон или год")
            else -> LazyVerticalGrid(
                GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(ui.items, key = { _, a -> a.slug }) { _, a ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpenAnime(a.slug) }) {
                        Artwork(a.coverSmall ?: a.cover, Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(14.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            a.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item {
                    if (ui.hasMore && ui.mode == "season") {
                        Text("Загрузить ещё", color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { viewModel.loadSeason(false) }.padding(16.dp))
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}
