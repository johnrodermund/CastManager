package com.johnrodermund.castmanager.cast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The app's only way to reach the Cast SDK. Nothing here or in its callers depends on SDK types. */
interface CastSessionGateway {
    val connection: StateFlow<CastConnection>

    /** Asks the connected Cast Device to play [media] when collected, and reports what the Cast Device then does. */
    fun load(media: CastMedia): Flow<CastMediaStatus>
}

enum class CastMediaStatus { Playing, Finished, Failed }

sealed interface CastConnection {
    data object Disconnected : CastConnection

    data class Connected(val deviceName: String) : CastConnection
}

data class CastMedia(val url: String, val contentType: String, val title: String)
