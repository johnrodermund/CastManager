package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastConnection
import com.johnrodermund.castmanager.cast.CastMedia
import com.johnrodermund.castmanager.cast.CastSessionGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Stands in for a Cast Device. Each load waits until the test calls [finishLoad]. */
class FakeCastSession : CastSessionGateway {
    override val connection = MutableStateFlow<CastConnection>(CastConnection.Disconnected)

    val loaded = mutableListOf<CastMedia>()
    private var pendingLoad = CompletableDeferred<Boolean>()

    override suspend fun load(media: CastMedia): Boolean {
        loaded += media
        pendingLoad = CompletableDeferred()
        return pendingLoad.await()
    }

    fun connect(deviceName: String) {
        connection.value = CastConnection.Connected(deviceName)
    }

    fun disconnect() {
        connection.value = CastConnection.Disconnected
    }

    fun finishLoad(accepted: Boolean) {
        pendingLoad.complete(accepted)
    }
}
