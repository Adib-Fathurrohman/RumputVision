package com.rumput.vision.core.network

import okhttp3.Dispatcher

/**
 * OkHttp runs a WebSocket's reader on a dispatcher thread for the socket's whole life, and that counts
 * against maxRequestsPerHost (5). Long-lived sockets (live video, realtime events) use this dispatcher so
 * they never starve API calls and image loads to the same server.
 */
val WebSocketDispatcher: Dispatcher = Dispatcher().apply {
    maxRequests = 64
    maxRequestsPerHost = 16
}
