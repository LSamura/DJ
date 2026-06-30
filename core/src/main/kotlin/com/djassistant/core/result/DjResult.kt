package com.djassistant.core.result

sealed class DjResult<out T> {
    data class Success<T>(val data: T) : DjResult<T>()
    data class Failure(val error: DjError) : DjResult<Nothing>()

    val isSuccess get() = this is Success
    val isFailure get() = this is Failure

    fun getOrNull(): T? = if (this is Success) data else null
}

inline fun <T> runCatching(block: () -> T): DjResult<T> = try {
    DjResult.Success(block())
} catch (e: Exception) {
    DjResult.Failure(DjError.Unknown(e.message ?: "Unknown error"))
}
