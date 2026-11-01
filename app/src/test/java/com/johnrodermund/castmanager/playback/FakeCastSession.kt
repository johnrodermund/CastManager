package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastConnection
import com.johnrodermund.castmanager.cast.CastMedia
import com.johnrodermund.castmanager.cast.CastMediaStatus
import com.johnrodermund.castmanager.cast.CastSessionGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Stands in for a Cast Device. A load reports nothing until the test calls [report]. */
class FakeCastSession : CastSessionGateway {
    override val connection = MutableStateFlow<CastConnection>(CastConnection.Disconnected)

    val loaded = mutableListOf<CastMedia>()
    private val statuses = MutableSharedFlow<CastMediaStatus>(extraBufferCapacity = 1)

    override fun load(media: CastMedia): Flow<CastMediaStatus> = flow {
        loaded += media
        emitAll(statuses)
    }

    fun connect(deviceName: String) {
        connection.value = CastConnection.Connected(deviceName)
    }

    fun disconnect() {
        connection.value = CastConnection.Disconnected
    }

    fun report(status: CastMediaStatus) {
        statuses.tryEmit(status)
    }
}
