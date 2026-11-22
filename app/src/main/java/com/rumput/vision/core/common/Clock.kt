package com.rumput.vision.core.common

import java.util.UUID

fun interface Clock {
    fun nowEpochMillis(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random: IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    }
}
