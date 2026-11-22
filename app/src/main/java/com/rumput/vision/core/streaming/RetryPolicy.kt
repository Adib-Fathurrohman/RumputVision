package com.rumput.vision.core.streaming

/**
 * Backoff for automatic stream reconnects: 1 s, 2 s, 4 s … capped at [maxDelayMillis]. Falling behind the
 * live edge reconnects immediately and does not count as a failure. A stream that has played steadily for
 * [stableAfterMillis] resets the backoff.
 */
class RetryPolicy(
    private val initialDelayMillis: Long = 1_000,
    private val maxDelayMillis: Long = 30_000,
    private val stableAfterMillis: Long = 15_000,
) {
    private var attempts = 0
    private var playingSinceMillis: Long? = null

    /** Delay before the next automatic retry, or null if [failure] should wait for the user. */
    fun nextDelay(failure: StreamFailure): Long? {
        playingSinceMillis = null
        if (!failure.retryable) return null
        if (failure == StreamFailure.FELL_BEHIND) return 0
        val delay = (initialDelayMillis shl attempts.coerceAtMost(20)).coerceAtMost(maxDelayMillis)
        attempts++
        return delay
    }

    fun onPlaying(nowMillis: Long) {
        if (playingSinceMillis == null) playingSinceMillis = nowMillis
    }

    /** Call periodically while playing. */
    fun onTick(nowMillis: Long) {
        val since = playingSinceMillis ?: return
        if (nowMillis - since >= stableAfterMillis) attempts = 0
    }

    fun reset() {
        attempts = 0
        playingSinceMillis = null
    }
}
