package com.kamisakyy.nanajoxkmama.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.common.formatBytes
import com.kamisakyy.nanajoxkmama.core.database.ThemeMode

/** Settings — theme, sources, data saver, cache (site SettingsModal port). */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val s by viewModel.settings.snapshot.collectAsState(initial = null)
    val snap = s ?: return

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))

        Text("Тема", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Системная", "Тёмная", "Светлая").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = snap.themeMode.ordinal == i,
                    onClick = { viewModel.setThemeMode(ThemeMode.entries[i]) },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }
        SettingSwitch("Material You · динамические цвета", snap.dynamicColor, viewModel::setDynamicColor)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text("Источники", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch("Названия на русском (Shikimori)", snap.ruTitles, viewModel::setRuTitles)
        SettingSwitch("Доп. источники (AnisongDB: OP/ED/IN)", snap.extraSources, viewModel::setExtraSources)
        SettingSwitch("Откровенный контент", snap.mature, viewModel::setMature)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text("Данные", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch("Экономия трафика", snap.dataSaver, viewModel::setDataSaver)
        SettingSwitch("Предзагрузка следующего трека", snap.preloadNext, viewModel::setPreloadNext)
        SettingSwitch("Скачивать видео по умолчанию", snap.downloadVideo, viewModel::setDownloadVideo)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Кэш API", style = MaterialTheme.typography.bodyLarge)
                Text(formatBytes(viewModel.cacheBytes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = viewModel::clearCache) { Text("Очистить") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(Modifier.height(20.dp))
        Text("AniBeat 2.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Минимализм без потери функционала. Скорость превыше всего. Приватность по умолчанию.\nAPI — AnimeThemes.moe + AnisongDB · Метаданные — Shikimori + AniList",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
