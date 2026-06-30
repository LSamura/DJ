package com.djassistant.core.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach

fun <T> Flow<T>.logErrors(tag: String): Flow<T> = this
    .catch { e -> timber.log.Timber.tag(tag).e(e, "Flow error") }

fun <T> Flow<T>.onEachLog(tag: String, message: (T) -> String): Flow<T> = this
    .onEach { value -> timber.log.Timber.tag(tag).d(message(value)) }
