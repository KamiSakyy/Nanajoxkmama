package com.kamisakyy.nanajoxkmama.core.network

import android.content.Context
import com.kamisakyy.nanajoxkmama.core.common.ApiException
import com.kamisakyy.nanajoxkmama.core.common.DAY_MS
import com.kamisakyy.nanajoxkmama.core.common.HOUR_MS
import com.kamisakyy.nanajoxkmama.core.common.MINUTE_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API transport — VERBATIM port of the proven v1.x ApiClient.requestText:
 * platform HttpURLConnection, plain UTF-8 stream, fixed timeouts,
 * Accept: application/json, User-Agent: AniBeat/1.0 (Android; Anime music player).
 * Requests are clean — Accept-Encoding: identity (no compression negotiation).
 * The old versions had NO cache layer — neither has this.
 * If a hop wraps the body anyway, it is decoded manually exactly like the
 * website (src/api/http.ts): gzip / brotli / deflate / zip, magic-byte sniffed.
 */
object HttpCachePolicy {
    data class Policy(
        val ttl: Long = 5 * MINUTE_MS,
        val fresh: Long = HOUR_MS,
        val maxAge: Long = 7 * DAY_MS,
        val noStore: Boolean = false,
        val refresh: Boolean = false,
        val body: String? = null,
        val cacheKey: String? = null,
        val timeoutMs: Long = 15_000,
        val retries: Int = 3,
    ) {
        fun ttl(v: Long) = copy(ttl = v)
        fun fresh(v: Long) = copy(fresh = v)
        fun maxAge(v: Long) = copy(maxAge = v)
        fun noStore(v: Boolean = true) = copy(noStore = v)
        fun refresh(v: Boolean = true) = copy(refresh = v)
        fun body(v: String?) = copy(body = v)
        fun cacheKey(v: String?) = copy(cacheKey = v)
        fun timeout(v: Long) = copy(timeoutMs = v)
        fun retries(v: Int) = copy(retries = v)
    }

    fun default() = Policy()
}

@Singleton
class HttpEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        // Purge every cache directory ever written by older builds —
        // poisoned entries there were serving garbage long after updates.
        purgeOldCaches()
    }

    private fun purgeOldCaches() {
        try {
            val base = context.cacheDir ?: return
            base.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name == "http-cache" || f.name == "http-cache-v2")) f.deleteRecursively()
            }
        } catch (_: Throwable) { }
    }

    private data class Entry(val ts: Long, val raw: String)

    private val mem = ConcurrentHashMap<String, Entry>()
    private val diskDir: File = File(context.cacheDir, "http-cache-v3").apply { mkdirs() }
    private val swrScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private fun key(url: String, policy: HttpCachePolicy.Policy): String =
        policy.cacheKey ?: if (policy.body != null) "$url#${policy.body}" else url

    private fun diskFile(key: String): File =
        File(diskDir, MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) })

    private fun readDisk(key: String): Entry? = try {
        val f = diskFile(key)
        if (!f.exists()) null
        else {
            val head = f.readLines()
            if (head.size >= 2) {
                val body = head.drop(1).joinToString("\n").trim()
                if (body.startsWith("{") || body.startsWith("[")) Entry(head[0].toLongOrNull() ?: 0, body)
                else { f.delete(); null }
            } else null
        }
    } catch (_: Throwable) { null }

    private fun writeDisk(key: String, raw: String) {
        try {
            diskFile(key).writeText(System.currentTimeMillis().toString() + "\n" + raw)
        } catch (_: Throwable) { }
    }

    fun buildUrl(base: String, path: String, params: Map<String, Any?>): String {
        val sb = StringBuilder(base).append(path)
        val entries = params.filterValues { it != null && "$it".isNotEmpty() }
        if (entries.isNotEmpty()) {
            sb.append('?')
            var first = true
            for ((k, v) in entries) {
                if (!first) sb.append('&')
                first = false
                sb.append(urlEncode(k)).append('=').append(urlEncode("$v"))
            }
        }
        return sb.toString()
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    suspend fun getJson(url: String, policy: HttpCachePolicy.Policy = HttpCachePolicy.default()): JSONObject {
        try {
            return JSONObject(getString(url, policy))
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            if (e is ApiException) throw e
            // one clean refetch before giving up (bypass cache)
            try {
                return JSONObject(getString(url, policy.copy(refresh = true)))
            } catch (e2: Exception) {
                if (e2 is java.util.concurrent.CancellationException) throw e2
                if (e2 is ApiException) throw e2
                throw ApiException("Неверный ответ сервера")
            }
        }
    }

    suspend fun getArray(url: String, policy: HttpCachePolicy.Policy = HttpCachePolicy.default()): JSONArray {
        try {
            return JSONArray(getString(url, policy))
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            if (e is ApiException) throw e
            try {
                return JSONArray(getString(url, policy.copy(refresh = true)))
            } catch (e2: Exception) {
                if (e2 is java.util.concurrent.CancellationException) throw e2
                if (e2 is ApiException) throw e2
                throw ApiException("Неверный ответ сервера")
            }
        }
    }

    suspend fun getString(url: String, policy: HttpCachePolicy.Policy = HttpCachePolicy.default()): String {
        val k = key(url, policy)
        val now = System.currentTimeMillis()
        if (!policy.refresh) {
            mem[k]?.let { if (now - it.ts < policy.ttl) return it.raw }
            if (!policy.noStore) {
                val disk = readDisk(k)
                if (disk != null) {
                    val age = now - disk.ts
                    if (age < policy.maxAge) {
                        mem[k] = disk
                        if (age >= policy.fresh) {
                            swrScope.launch {
                                runCatching {
                                    val fresh = fetch(url, policy)
                                    mem[k] = Entry(System.currentTimeMillis(), fresh)
                                    writeDisk(k, fresh)
                                }
                            }
                        }
                        return disk.raw
                    }
                }
            }
        }
        val result = withContext(kotlinx.coroutines.Dispatchers.IO) { fetch(url, policy) }
        val e = Entry(System.currentTimeMillis(), result)
        mem[k] = e
        if (!policy.noStore) writeDisk(k, result)
        return result
    }

    /* ---------------- site http.ts parity: manual decompression ---------------- */

    private fun looksLikeJson(t: String): Boolean {
        var i = 0
        while (i < t.length && (t[i] == ' ' || t[i] == '\t' || t[i] == '\r' || t[i] == '\n' || t[i] == '\uFEFF')) i++
        return i < t.length && (t[i] == '{' || t[i] == '[')
    }

    private fun isGzip(d: ByteArray) = d.size > 2 && d[0] == 0x1f.toByte() && d[1] == 0x8b.toByte()
    private fun isBrotli(d: ByteArray) = d.size > 2 && d[0] == 0x42.toByte() && d[1] == 0x7a.toByte()
    private fun isZip(d: ByteArray) = d.size > 3 && d[0] == 0x50.toByte() && d[1] == 0x4b.toByte() && d[2] == 0x03.toByte()
    private fun isZlib(d: ByteArray) = d.size > 1 && d[0] == 0x78.toByte()

    private fun decodeLayer(data: ByteArray): ByteArray? = try {
        when {
            isGzip(data) ->
                java.util.zip.GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
            isBrotli(data) ->
                org.brotli.dec.BrotliInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
            isZip(data) ->
                java.util.zip.ZipInputStream(ByteArrayInputStream(data)).use { z -> z.nextEntry; z.readBytes() }
            isZlib(data) ->
                java.util.zip.InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
            else -> null
        }
    } catch (_: Throwable) { null }

    /** Magic-byte sniffed multi-layer decode — gzip / brotli / zip / deflate, like the website. */
    private fun decodeChain(bytes: ByteArray, encoding: String?): String? {
        var data = bytes
        for (i in 0 until 4) {
            val next = decodeLayer(data) ?: break
            if (next.isEmpty()) break
            data = next
        }
        if (!looksLikeJson(String(data, Charsets.UTF_8)) &&
            (encoding ?: "").lowercase(Locale.US).contains("deflate")
        ) {
            try {
                data = java.util.zip.InflaterInputStream(ByteArrayInputStream(data), java.util.zip.Inflater(true))
                    .use { it.readBytes() }
            } catch (_: Throwable) { }
        }
        val text = String(data, Charsets.UTF_8)
        return if (looksLikeJson(text)) text else null
    }

    /** v1 plain read semantics first; manual decode only if the body is wrapped. */
    private fun charsetDecode(bytes: ByteArray): String {
        if (bytes.size > 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size > 1 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, Charsets.UTF_16LE)
        if (bytes.size > 1 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, Charsets.UTF_16BE)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readPlain(input: java.io.InputStream?, encoding: String?): String {
        if (input == null) return ""
        val bytes = input.use { it.readBytes() }
        val v1 = charsetDecode(bytes)
        if (looksLikeJson(v1)) return v1
        return decodeChain(bytes, encoding) ?: v1
    }

    /* --------- v1 ApiClient.requestText VERBATIM + retry / clear errors -------- */

    private fun fetch(url: String, policy: HttpCachePolicy.Policy): String {
        var attempt = 0
        val maxRetries = policy.retries.coerceAtMost(RETRY_DELAYS.size)
        while (true) {
            var connection: java.net.HttpURLConnection? = null
            try {
                connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
                connection.requestMethod = if (policy.body != null) "POST" else "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.useCaches = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "AniBeat/1.0 (Android; Anime music player)")
                val body = policy.body
                if (body != null) {
                    val payload = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { out ->
                        out.write(payload)
                        out.flush()
                    }
                }
                val code = connection.responseCode
                if (code == 429 || code >= 500) {
                    if (attempt < maxRetries) {
                        val ra = connection.getHeaderField("retry-after")?.toLongOrNull()?.times(1000) ?: 0L
                        Thread.sleep(if (ra > 0) minOf(ra, 15000) else RETRY_DELAYS[attempt])
                        attempt++
                        continue
                    }
                    throw ApiException(
                        if (code == 429) "Слишком много запросов — попробуйте чуть позже"
                        else "Сервер временно недоступен ($code)",
                        code
                    )
                }
                val input = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = readPlain(input, connection.getHeaderField("Content-Encoding"))
                if (code < 200 || code >= 300) throw ApiException("Ошибка API ($code)", code)
                return text
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                if (attempt < maxRetries) {
                    Thread.sleep(RETRY_DELAYS[attempt])
                    attempt++
                    continue
                }
                val err = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
                android.util.Log.e("AniBeatHttp", "fetch failed: $url", e)
                throw ApiException("Сервер не отвечает ($err)")
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun clear() {
        mem.clear()
        try { diskDir.listFiles()?.forEach { it.delete() } } catch (_: Throwable) { }
    }

    suspend fun clearAsync() { clear() }

    fun diskBytes(): Long = try { diskDir.listFiles()?.sumOf { it.length() } ?: 0L } catch (_: Throwable) { 0L }

    private val RETRY_DELAYS = longArrayOf(700, 1800, 3800, 6000)
}
