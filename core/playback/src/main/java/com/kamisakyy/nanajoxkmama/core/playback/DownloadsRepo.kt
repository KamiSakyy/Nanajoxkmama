package com.kamisakyy.nanajoxkmama.core.playback

import android.content.Context
import com.kamisakyy.nanajoxkmama.core.database.LibraryRepository
import com.kamisakyy.nanajoxkmama.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data class Progress(val bytes: Long) : DownloadState
    data class Done(val path: String, val bytes: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Offline-first downloads — local file beats any remote URL in the player. */
@Singleton
class DownloadsRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val playerController: PlayerController,
) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val client = OkHttpClient()

    fun download(track: Track, video: Boolean): Flow<DownloadState> = flow {
        val url = if (video) {
            if (track.videoUrl.isNotEmpty()) track.videoUrl else track.audioUrl
        } else {
            if (track.audioUrl.isNotEmpty()) track.audioUrl else track.videoUrl
        }
        if (url.isBlank() || url.startsWith("/") || url.startsWith("file:")) {
            emit(DownloadState.Failed("Источник недоступен"))
            return@flow
        }
        val safe = (track.id + "_" + (if (video) "video" else "audio")).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = if (video) "webm" else if (url.endsWith(".ogg")) "ogg" else if (url.endsWith(".mp3")) "mp3" else "m4a"
        val out = File(dir, "$safe.$ext")
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
                val body = res.body ?: throw java.io.IOException("Пустой ответ")
                val total = body.contentLength()
                var written = 0L
                out.outputStream().use { os ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            os.write(buf, 0, n)
                            written += n
                            if (total > 0 && written % (512 * 1024) < 65536) {
                                emit(DownloadState.Progress(written))
                            }
                        }
                    }
                }
            }
            library.upsertDownload(track, video, out.absolutePath, out.length())
            playerController.invalidateOfflineIndex()
            emit(DownloadState.Done(out.absolutePath, out.length()))
        } catch (e: Exception) {
            out.delete()
            emit(DownloadState.Failed(e.message ?: "Ошибка загрузки"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun delete(key: String, path: String) {
        runCatching { File(path).delete() }
        library.deleteDownload(key)
        playerController.invalidateOfflineIndex()
    }

    fun bytesOnDisk(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
