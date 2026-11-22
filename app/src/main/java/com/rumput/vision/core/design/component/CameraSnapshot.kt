package com.rumput.vision.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * The latest frame of a camera, reloaded whenever [refreshKey] changes. The previous frame stays on screen
 * while the next one loads (and if it fails), so periodic refreshes do not flicker.
 * [url] null (not connected, camera disabled) shows a neutral placeholder.
 */
@Composable
fun CameraSnapshot(
    url: String?,
    refreshKey: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(
                Icons.Outlined.VideocamOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            return@Box
        }
        var lastLoadedKey by remember(url) { mutableStateOf<String?>(null) }
        val key = "$url#$refreshKey"
        val context = LocalContext.current
        val request = remember(key) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(key)
                .placeholderMemoryCacheKey(lastLoadedKey)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onSuccess = { lastLoadedKey = key },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
