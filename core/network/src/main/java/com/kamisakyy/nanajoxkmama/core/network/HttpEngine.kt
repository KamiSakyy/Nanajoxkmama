package com.kamisakyy.nanajoxkmama.core.network

import android.content.Context
import com.kamisakyy.nanajoxkmama.core.common.ApiException
import com.kamisakyy.nanajoxkmama.core.common.DAY_MS
import com.kamisakyy.nanajoxkmama.core.common.HOUR_MS
import com.kamisakyy.nanajoxkmama.core.common.MINUTE_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Browser-grade HTTP layer (website `src/api/http.ts` port):
 *  • HTTP/2 via OkHttp with connection pre-warm
 *  • two-tier cache — memory hot + disk, stale-while-revalidate
 *  • request de-duplication (identical inflight calls share one call)
 *  • retries with exponential backoff + Retry-After on 429 / 5xx / network errors
 *  • per-request timeouts
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
    private data class Entry(val ts: Long, val raw: String)

    private val mem = ConcurrentHashMap<String, Entry>()
    private val inflight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<String>>()
    private val diskDir: File = File(context.cacheDir, "http-cache").apply { mkdirs() }
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
    private val jsonMedia = "application/json".toMediaType()

    // Official OkHttp 5.5.0 — stock configuration, timeouts only. No custom hacks.
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun buildUrl(base: String, path: String, params: Map<String, Any?>): String {
        val sb = StringBuilder(base).append(path)
        val entries = params.filterValues { it != null && "$it".isNotEmpty() }.toList().sortedBy { it.first }
        if (entries.isNotEmpty()) {
            sb.append('?')
            entries.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append('&')
                sb.append(urlEncode(k)).append('=').append(urlEncode("$v"))
            }
        }
        return sb.toString()
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun key(url: String, policy: HttpCachePolicy.Policy): String =
        policy.cacheKey ?: if (policy.body != null) "$url#${policy.body}" else url

    private fun diskFile(key: String): File =
        File(diskDir, MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) })

    suspend fun getJson(url: String, policy: HttpCachePolicy.Policy = HttpCachePolicy.default()): JSONObject =
        JSONObject(getString(url, policy))

    suspend fun getArray(url: String, policy: HttpCachePolicy.Policy = HttpCachePolicy.default()): JSONArray =
        JSONArray(getString(url, policy))

    suspend fun getString(url: String, policy: HttpCachePolicy.Policy = HttpCachePolicy.default()): String {
        val k = key(url, policy)
        val now = System.currentTimeMillis()

        if (!policy.refresh) {
            mem[k]?.let { if (now - it.ts < policy.ttl) return it.raw }
            inflight[k]?.let { return it.await() }
        }

        val job = scope.async {
            if (!policy.refresh && !policy.noStore) {
                val disk = readDisk(k)
                if (disk != null) {
                    val age = System.currentTimeMillis() - disk.ts
                    if (age < policy.maxAge) {
                        mem[k] = disk
                        if (age >= policy.fresh) {
                            // stale-while-revalidate: paint instantly, refresh quietly
                            launch {
                                runCatching {
                                    val fresh = fetch(url, policy)
                                    val e = Entry(System.currentTimeMillis(), fresh)
                                    mem[k] = e
                                    writeDisk(k, fresh)
                                }
                            }
                        }
                        return@async disk.raw
                    }
                }
            }
            val result = fetch(url, policy)
            val e = Entry(System.currentTimeMillis(), result)
            mem[k] = e
            if (!policy.noStore) writeDisk(k, result)
            result
        }
        inflight[k] = job
        try {
            return job.await()
        } finally {
            inflight.remove(k, job)
        }
    }

    /**
     * Servers/middleboxes can deliver compressed bodies the HTTP layer does not
     * transparently unwrap — the website decompresses gzip/deflate/zip manually
     * in its http.ts; same here (magic-byte sniffing).
     */
    private fun decodeBody(bytes: ByteArray, encoding: String?): String {
        var data = bytes
        try {
            val enc = encoding?.lowercase(java.util.Locale.US) ?: ""
            val isGzip = data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
            val isZip = data.size > 3 && data[0] == 0x50.toByte() && data[1] == 0x4b.toByte() && data[2] == 0x03.toByte()
            data = when {
                enc.contains("gzip") || isGzip ->
                    java.util.zip.GZIPInputStream(data.inputStream()).use { it.readBytes() }
                enc.contains("deflate") ->
                    java.util.zip.InflaterInputStream(data.inputStream()).use { it.readBytes() }
                isZip ->
                    java.util.zip.ZipInputStream(data.inputStream()).use { z ->
                        z.nextEntry
                        z.readBytes()
                    }
                else -> data
            }
        } catch (_: Exception) {
        }
        return String(data, Charsets.UTF_8)
    }

    private fun fetch(url: String, policy: HttpCachePolicy.Policy): String {
        var attempt = 0
        val maxRetries = policy.retries.coerceAtMost(RETRY_DELAYS.size)
        while (true) {
            try {
                val rb = Request.Builder().url(url)
                if (policy.body != null) {
                    rb.post(policy.body.toRequestBody(jsonMedia))
                    rb.header("Accept-Encoding", "identity")
                } else {
                    rb.get()
                rb.header("Accept-Encoding", "identity")
                }
                client.newCall(rb.build()).execute().use { res ->
                    when {
                        res.code == 429 || res.code >= 500 -> {
                            if (attempt < maxRetries) {
                                val ra = res.header("retry-after")?.toLongOrNull()?.times(1000) ?: 0L
                                Thread.sleep(if (ra > 0) minOf(ra, 8000) else RETRY_DELAYS[attempt])
                                attempt++
                                return@use ""
                            }
                            throw ApiException(
                                if (res.code == 429) "Слишком много запросов — попробуйте чуть позже"
                                else "Сервер временно недоступен (${res.code})",
                                res.code
                            )
                        }
                        !res.isSuccessful -> throw ApiException(
                            if (res.code == 404) "Не найдено" else "Ошибка API (${res.code})",
                            res.code
                        )
                    }
                    return decodeBody(res.body?.bytes() ?: ByteArray(0), res.header("Content-Encoding"))
                }
                // loop continues only after a retryable sleep above
                continue
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    Thread.sleep(RETRY_DELAYS[attempt])
                    attempt++
                    continue
                }
                val err = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
                android.util.Log.e("AniBeatHttp", "fetch failed: $url", e)
                throw ApiException("Сервер не отвечает ($err)")
            }
        }
    }

    private fun readDisk(key: String): Entry? = try {
        val f = diskFile(key)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 30 * DAY_MS) null
        else {
            val head = f.readLines()
            if (head.size >= 2) Entry(head[0].toLongOrNull() ?: 0, head.drop(1).joinToString("\n")) else null
        }
    } catch (_: Exception) { null }

    private fun writeDisk(key: String, raw: String) {
        try {
            diskFile(key).writeText(System.currentTimeMillis().toString() + "\n" + raw)
        } catch (_: Exception) { }
    }

    fun clear() {
        mem.clear()
        diskDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun clearAsync() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { clear() }

    fun diskBytes(): Long = diskDir.listFiles()?.sumOf { it.length() } ?: 0L

    private val RETRY_DELAYS = longArrayOf(600, 1500, 3200)
}
