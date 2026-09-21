package com.kamisakyy.nanajoxkmama.core.common

/** Uniform result type — errors are values, never crashes. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Err(val message: String, val status: Int = 0) : ApiResult<Nothing> {
        val retryable: Boolean get() = status == 0 || status == 429 || status >= 500
    }

    inline fun <R> map(f: (T) -> R): ApiResult<R> = when (this) {
        is Ok -> Ok(f(data))
        is Err -> this
    }

    fun getOrNull(): T? = (this as? Ok)?.data
    val error: Err? get() = this as? Err
}

inline fun <T> runApi(block: () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: ApiException) {
    ApiResult.Err(e.message ?: "Ошибка", e.status)
} catch (e: java.io.IOException) {
    ApiResult.Err("Нет подключения к интернету")
} catch (e: Exception) {
    ApiResult.Err("Сервер не отвечает. Проверьте соединение")
}

class ApiException(message: String, val status: Int = 0) : Exception(message)
