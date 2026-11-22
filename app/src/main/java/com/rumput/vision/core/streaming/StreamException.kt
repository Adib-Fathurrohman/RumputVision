package com.rumput.vision.core.streaming

import java.io.IOException

enum class StreamFailure(val retryable: Boolean) {
    /** The WebSocket upgrade returned 401/403. Retrying will not help until the session is fixed. */
    UNAUTHORIZED(false),

    /** go2rtc reported an error for the stream (unknown stream, camera unreachable, …). */
    SOURCE_ERROR(true),

    /** go2rtc chose codecs this device cannot decode. */
    UNSUPPORTED_CODEC(false),

    /** No data for too long, or the connection dropped. */
    NETWORK(true),

    /** Playback fell too far behind the live edge; reconnecting jumps back to live. */
    FELL_BEHIND(true),

    /** The device could not decode or render the stream. */
    DECODER(true),

    /** No server connection, so no stream URL. */
    NOT_CONNECTED(true),
}

class StreamException(val failure: StreamFailure, message: String, cause: Throwable? = null) : IOException(message, cause)
