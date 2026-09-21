package com.kamisakyy.nanajoxkmama.core.common

/** Uniform result type — errors are values, never crashes. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Err(val message: String, val status: Int = 0) : ApiResult<Nothing> {
        val retryable: Boolean get() = status == 0 || status == 429 || status >= 500
    }

    fun getOrNull(): T? = (this as? Ok)?.data
    val error: Err? get() = this as? Err
}

inline fun <T> runApi(block: () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: ApiException) {
    ApiResult.Err(e.message ?: "Ошибка", e.status)
} catch (e: java.io.IOException) {
    ApiResult.Err("Нет подключения (${e.javaClass.simpleName}: ${e.message})")
} catch (e: java.util.concurrent.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("AniBeatApi", "api failed", e)
    ApiResult.Err("Ошибка (${e.javaClass.simpleName}: ${e.message})")
}

class ApiException(message: String, val status: Int = 0) : Exception(message)

inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(f(data))
    is ApiResult.Err -> this
}

inline fun <T> safeRun(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: java.util.concurrent.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
