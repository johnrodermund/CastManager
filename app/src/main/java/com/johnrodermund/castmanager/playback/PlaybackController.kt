package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastConnection
import com.johnrodermund.castmanager.cast.CastMedia
import com.johnrodermund.castmanager.cast.CastSessionGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val SampleMedia = CastMedia(
    url = "https://media.w3.org/2010/05/sintel/trailer.mp4",
    contentType = "video/mp4",
    title = "Sintel trailer",
)

data class PlaybackState(
    val connectedDevice: String? = null,
    val playback: PlaybackStatus = PlaybackStatus.None,
)

enum class PlaybackStatus { None, Loading, Playing, Failed }

/** Plays the sample video on each Cast Session that starts. */
class PlaybackController(
    private val gateway: CastSessionGateway,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    init {
        scope.launch {
            gateway.connection.collectLatest { connection ->
                when (connection) {
                    CastConnection.Disconnected -> _state.value = PlaybackState()
                    is CastConnection.Connected -> play(connection)
                }
            }
        }
    }

    private suspend fun play(connection: CastConnection.Connected) {
        _state.value = PlaybackState(connection.deviceName, PlaybackStatus.Loading)
        val accepted = gateway.load(SampleMedia)
        _state.update {
            it.copy(playback = if (accepted) PlaybackStatus.Playing else PlaybackStatus.Failed)
        }
    }
}
