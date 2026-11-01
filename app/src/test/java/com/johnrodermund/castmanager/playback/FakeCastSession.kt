package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastConnection
import com.johnrodermund.castmanager.cast.CastMedia
import com.johnrodermund.castmanager.cast.CastMediaStatus
import com.johnrodermund.castmanager.cast.CastSessionGateway
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile

/** Stands in for a Cast Device. A load reports nothing until the test calls [report]. */
class FakeCastSession : CastSessionGateway {
    override val connection = MutableStateFlow<CastConnection>(CastConnection.Disconnected)

    override val volume = MutableStateFlow(1f)

    /** The commands the app sent, in order. */
    val commands = mutableListOf<String>()

    private val loaded = MutableStateFlow(emptyList<CastMedia>())
    private var statuses = Channel<CastMediaStatus>(Channel.UNLIMITED)

    val loadCount get() = loaded.value.size

    override fun load(media: CastMedia): Flow<CastMediaStatus> = flow {
        val current = Channel<CastMediaStatus>(Channel.UNLIMITED)
        statuses = current
        loaded.value += media
        current.receiveAsFlow()
            .transformWhile { status ->
                emit(status)
                status != CastMediaStatus.Finished && status != CastMediaStatus.Failed
            }
            .collect(::emit)
    }

    override fun play() {
        commands += "play"
    }

    override fun pause() {
        commands += "pause"
    }

    override fun seekTo(positionMillis: Long) {
        commands += "seek $positionMillis"
    }

    override fun stop() {
        commands += "stop"
    }

    override fun setVolume(volume: Float) {
        this.volume.value = volume
    }

    /** Waits for load number [number], counting from 1. */
    suspend fun awaitLoad(number: Int = 1): CastMedia = loaded.first { it.size >= number }[number - 1]

    fun connect(deviceName: String) {
        connection.value = CastConnection.Connected(deviceName)
    }

    fun disconnect() {
        connection.value = CastConnection.Disconnected
    }

    fun report(status: CastMediaStatus) {
        statuses.trySend(status)
    }
}
