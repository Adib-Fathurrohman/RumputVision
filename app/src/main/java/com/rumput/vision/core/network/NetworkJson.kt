package com.rumput.vision.core.network

import kotlinx.serialization.json.Json

/** Frigate responses are large and change between versions: unknown keys are always ignored. */
val FrigateJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}
