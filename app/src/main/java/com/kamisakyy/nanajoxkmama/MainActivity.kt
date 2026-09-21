package com.kamisakyy.nanajoxkmama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kamisakyy.nanajoxkmama.core.design.AniBeatTheme
import com.kamisakyy.nanajoxkmama.feature.browse.BrowseScreen
import com.kamisakyy.nanajoxkmama.feature.browse.BrowseViewModel
import com.kamisakyy.nanajoxkmama.feature.details.DetailsScreen
import com.kamisakyy.nanajoxkmama.feature.details.DetailsViewModel
import com.kamisakyy.nanajoxkmama.feature.home.HomeScreen
import com.kamisakyy.nanajoxkmama.feature.home.HomeViewModel
import com.kamisakyy.nanajoxkmama.feature.library.LibraryScreen
import com.kamisakyy.nanajoxkmama.feature.library.LibraryViewModel
import com.kamisakyy.nanajoxkmama.feature.player.MiniPlayer
import com.kamisakyy.nanajoxkmama.feature.player.NowPlayingScreen
import com.kamisakyy.nanajoxkmama.feature.player.NowPlayingViewModel
import com.kamisakyy.nanajoxkmama.feature.search.SearchScreen
import com.kamisakyy.nanajoxkmama.feature.search.SearchViewModel
import com.kamisakyy.nanajoxkmama.feature.settings.SettingsScreen
import com.kamisakyy.nanajoxkmama.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val npViewModel: NowPlayingViewModel = hiltViewModel()
            val settingsSnapshot by npViewModel.settings.snapshot.collectAsStateWithLifecycle(initialValue = null)
            AniBeatTheme(
                themeMode = settingsSnapshot?.themeMode?.ordinal ?: 0,
                dynamicColor = settingsSnapshot?.dynamicColor ?: false,
            ) {
                AppShell(npViewModel)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppShell(np: NowPlayingViewModel) {
    val nav = rememberNavController()
    val state by np.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("home") }
    var playerOpen by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    // toast channel from the playback engine
    LaunchedEffect(Unit) {
        np.player.toasts.collect { snack.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            val tabs = listOf(
                Tab("home", "Главная", Icons.Rounded.Home),
                Tab("search", "Поиск", Icons.Rounded.Search),
                Tab("browse", "Каталог", Icons.Rounded.Tune),
                Tab("library", "Библиотека", Icons.Rounded.LibraryMusic),
                Tab("settings", "Настройки", Icons.Rounded.Settings),
            )
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                composable("home") {
                    HomeScreen(
                        viewModel = hiltViewModel<HomeViewModel>(),
                        onPlayAll = { tracks, i -> np.player.playAll(tracks, i) },
                        onOpenMix = { nav.navigate("details/mix/$it") },
                        onOpenDecade = { nav.navigate("details/decade/$it") },
                        onOpenAnime = { nav.navigate("details/anime/$it") },
                    )
                }
                composable("search") {
                    SearchScreen(
                        viewModel = hiltViewModel<SearchViewModel>(),
                        playlists = playlistsCollect(np),
                        downloadProgress = dlCollect(np),
                        currentId = state.current?.id,
                        onPlay = { tracks, i -> np.player.playAll(tracks, i) },
                        onPlayNext = np.player::enqueueNext,
                        onEnqueue = np.player::enqueue,
                        onAddToPlaylist = np::addTrackToPlaylist,
                        onCreatePlaylist = np::createPlaylist,
                        onDownload = np::download,
                        onOpenAnime = { nav.navigate("details/anime/$it") },
                    )
                }
                composable("browse") {
                    BrowseScreen(
                        viewModel = hiltViewModel<BrowseViewModel>(),
                        onOpenAnime = { nav.navigate("details/anime/$it") },
                        onOpenDecade = { nav.navigate("details/decade/$it") },
                        onOpenGenre = { nav.navigate("details/decade/${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}") },
                    )
                }
                composable("library") {
                    LibraryScreen(
                        viewModel = hiltViewModel<LibraryViewModel>(),
                        currentId = state.current?.id,
                        onPlay = { tracks, i -> np.player.playAll(tracks, i) },
                        onOpenPlaylist = { nav.navigate("details/playlist/$it") },
                    )
                }
                composable("settings") {
                    SettingsScreen(viewModel = hiltViewModel<SettingsViewModel>())
                }
                composable(
                    "details/anime/{slug}",
                    arguments = listOf(navArgument("slug") { type = NavType.StringType }),
                ) { entry ->
                    val slug = entry.arguments?.getString("slug") ?: return@composable
                    DetailsDetailHost(
                        np = np, kind = "anime", id = slug,
                        onOpenAnime = { nav.navigate("details/anime/$it") },
                    )
                }
                composable(
                    "details/artist/{slug}",
                    arguments = listOf(navArgument("slug") { type = NavType.StringType }),
                ) { entry ->
                    val slug = entry.arguments?.getString("slug") ?: return@composable
                    DetailsDetailHost(np = np, kind = "artist", id = slug, onOpenAnime = { nav.navigate("details/anime/$it") })
                }
                composable(
                    "details/mix/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    DetailsDetailHost(np = np, kind = "mix", id = id, onOpenAnime = { nav.navigate("details/anime/$it") })
                }
                composable(
                    "details/decade/{year}",
                    arguments = listOf(navArgument("year") { type = NavType.IntType }),
                ) { entry ->
                    val y = entry.arguments?.getInt("year") ?: 2020
                    DetailsDetailHost(np = np, kind = "decade", id = y.toString(), onOpenAnime = { nav.navigate("details/anime/$it") })
                }
                composable(
                    "details/playlist/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    DetailsDetailHost(np = np, kind = "playlist", id = id, onOpenAnime = { nav.navigate("details/anime/$it") })
                }
            }

            // mini player sits above tabs — window controls live inside; never closes on pause
            AnimatedVisibility(
                visible = state.hasQueue && !playerOpen,
                enter = slideInVertically(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it },
                exit = slideOutVertically(animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { it },
            ) {
                MiniPlayer(
                    state = state,
                    onTogglePlay = np.player::togglePlay,
                    onNext = np.player::next,
                    onOpen = { playerOpen = true },
                )
            }

            // iOS-style floating dock
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    tabs.forEach { t ->
                        val sel = tab == t.route
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                                .background(if (sel) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(), indication = androidx.compose.material3.ripple()) {
                                    tab = t.route
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                t.icon,
                                contentDescription = t.label,
                                tint = if (sel) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)))
        }

        // full player window — overlay with slide-up motion
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit = slideOutVertically(animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)) { it },
        ) {
            NowPlayingScreen(
                viewModel = np,
                onClose = { playerOpen = false },
                onOpenAnime = {
                    playerOpen = false
                    nav.navigate("details/anime/$it")
                },
            )
        }

        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun playlistsCollect(np: NowPlayingViewModel) = np.playlists.collectAsStateWithLifecycle().value

@Composable
private fun dlCollect(np: NowPlayingViewModel) = np.downloadProgress.collectAsStateWithLifecycle().value

@Composable
private fun DetailsDetailHost(
    np: NowPlayingViewModel,
    kind: String,
    id: String,
    onOpenAnime: (String) -> Unit,
) {
    var sheetTrack by remember { mutableStateOf<com.kamisakyy.nanajoxkmama.core.model.Track?>(null) }
    var sheetPlaylistId by remember { mutableStateOf("") }
    var sheetOpen by remember { mutableStateOf(false) }
    val vm: DetailsViewModel = hiltViewModel(key = "details:$kind:$id")
    LaunchedEffect(kind, id) {
        when (kind) {
            "anime" -> vm.loadAnime(id)
            "artist" -> vm.loadArtist(id)
            "mix" -> vm.loadMix(id)
            "decade" -> vm.loadDecade(id.toIntOrNull() ?: 2020)
            "playlist" -> { /* playlist detail uses LibraryViewModel data via shared repo below */ }
        }
    }
    if (kind == "playlist") {
        val libVm: LibraryViewModel = hiltViewModel(key = "lib:$id")
        val playlists by libVm.playlists.collectAsStateWithLifecycle()
        val p = playlists.firstOrNull { it.id == id }
        val state by np.state.collectAsStateWithLifecycle()
        var selection by remember { mutableStateOf<Set<String>?>(null) }
        val sel = selection
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(p?.name ?: "Плейлист", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (sel != null) {
                    Text("Выбрано: ${sel.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = {
                        val tracks = p?.tracks ?: emptyList()
                        sel.forEach { tid -> np.addTrackToPlaylist(id, tracks.firstOrNull { it.id == tid } ?: return@forEach) }
                    }) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Добавить в очередь") }
                    IconButton(onClick = { sel.forEach { tid -> libVm.removeFromPlaylist(id, tid) }; selection = null }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Удалить выбранные")
                    }
                    IconButton(onClick = { selection = null }) { Icon(Icons.Rounded.Close, contentDescription = "Снять выделение") }
                } else {
                    IconButton(onClick = { selection = emptySet() }) {
                        Icon(Icons.Rounded.Checklist, contentDescription = "Выбрать несколько")
                    }
                }
            }
            com.kamisakyy.nanajoxkmama.feature.player.ReorderTrackList(
                tracks = p?.tracks ?: emptyList(),
                currentId = state.current?.id,
                selection = sel,
                onSelectionToggle = { tid ->
                    val cur = selection ?: emptySet()
                    selection = if (cur.contains(tid)) cur - tid else cur + tid
                },
                onMove = { from, to ->
                    val cur = (p?.tracks ?: emptyList()).toMutableList()
                    if (from in cur.indices && to in cur.indices) {
                        val item = cur.removeAt(from)
                        cur.add(to, item)
                        libVm.reorder(id, cur)
                    }
                },
                onPlay = { tracks, i -> np.player.playAll(tracks, i) },
                onLongPress = { t ->
                    sheetTrack = t
                    sheetPlaylistId = id
                    sheetOpen = true
                },
            )
        }
        if (sheetOpen) {
            sheetTrack?.let { t ->
                com.kamisakyy.nanajoxkmama.feature.player.TrackActionSheet(
                    track = t,
                    playlists = playlists,
                    downloadProgress = dlCollect(np)[t.id + ":a"] ?: dlCollect(np)[t.id + ":v"],
                    onPlay = { np.player.playAll(p?.tracks ?: listOf(t), (p?.tracks ?: listOf(t)).indexOfFirst { it.id == t.id }) },
                    onPlayNext = { np.player.enqueueNext(t) },
                    onEnqueue = { np.player.enqueue(t) },
                    onAddToPlaylist = { pid -> np.addTrackToPlaylist(pid, t) },
                    onCreatePlaylist = { name -> np.createPlaylist(name, t) },
                    onDownloadAudio = { np.download(t, false) },
                    onDownloadVideo = { np.download(t, true) },
                    onOpenAnime = { onOpenAnime(t.anime.slug) },
                    onDismiss = { sheetOpen = false },
                )
            }
        }
        return
    }
    val state by np.state.collectAsStateWithLifecycle()
    DetailsScreen(
        viewModel = vm,
        playlists = playlistsCollect(np),
        downloadProgress = dlCollect(np),
        currentId = state.current?.id,
        onPlay = { tracks, i -> np.player.playAll(tracks, i) },
        onPlayNext = np.player::enqueueNext,
        onEnqueue = np.player::enqueue,
        onAddToPlaylist = np::addTrackToPlaylist,
        onCreatePlaylist = np::createPlaylist,
        onDownload = np::download,
        onOpenAnime = onOpenAnime,
    )
}
