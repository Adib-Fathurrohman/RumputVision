package com.rumput.vision.domain.model

enum class CameraStatus {
    ONLINE,
    OFFLINE,
    DISABLED,

    /** Only cached information is available; current status is not known. */
    UNKNOWN,
}
