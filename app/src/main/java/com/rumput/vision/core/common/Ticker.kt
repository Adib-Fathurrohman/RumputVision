package com.rumput.vision.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/**
 * Emits 0, 1, 2, … every [intervalMillis] while [active] is true, and nothing while it is false.
 * Collected through a WhileSubscribed state flow, it also stops when the screen stops observing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun ticker(active: Flow<Boolean>, intervalMillis: Long): Flow<Long> {
    var count = 0L
    return active.distinctUntilChanged().transformLatest { isActive ->
        while (isActive) {
            emit(count++)
            delay(intervalMillis)
        }
    }
}
