package com.johnrodermund.castmanager.cast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The app's only way to reach the Cast SDK. Nothing here or in its callers depends on SDK types. */
interface CastSessionGateway {
    val connection: StateFlow<CastConnection>

    /** The Cast Device's volume, from 0 to 1. */
    val volume: StateFlow<Float>

    /** Asks the connected Cast Device to play [media] when collected, and reports what the Cast Device then does. */
    fun load(media: CastMedia): Flow<CastMediaStatus>

    fun play()

    fun pause()

    fun seekTo(positionMillis: Long)

    fun stop()

    fun setVolume(volume: Float)
}

sealed interface CastConnection {
    data object Disconnected : CastConnection

    data class Connected(val deviceName: String) : CastConnection
}

sealed interface CastMediaStatus {
    data object Playing : CastMediaStatus

    data object Paused : CastMediaStatus

    data class Progress(val positionMillis: Long, val durationMillis: Long) : CastMediaStatus

    data object Finished : CastMediaStatus

    data object Failed : CastMediaStatus
}

data class CastMedia(val url: String, val contentType: String, val title: String)
