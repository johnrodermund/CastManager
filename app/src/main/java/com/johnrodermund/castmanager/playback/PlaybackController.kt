package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastConnection
import com.johnrodermund.castmanager.cast.CastMedia
import com.johnrodermund.castmanager.cast.CastMediaStatus
import com.johnrodermund.castmanager.cast.CastSessionGateway
import com.johnrodermund.castmanager.media.MediaFile
import com.johnrodermund.castmanager.media.MediaServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackState(
    val connectedDevice: String? = null,
    val mediaFileName: String? = null,
    val playback: PlaybackStatus = PlaybackStatus.None,
)

enum class PlaybackStatus { None, NeedsLocalNetwork, LocalNetworkDenied, Loading, Playing, Failed }

/** Starts Playback once a Cast Session and a Media File are both set, and ends it. */
class PlaybackController(
    private val gateway: CastSessionGateway,
    private val mediaServer: MediaServer,
    private val localNetworkAllowed: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var mediaFile: MediaFile? = null
    private var playbackJob: Job? = null

    init {
        scope.launch {
            gateway.connection.collect { connection ->
                when (connection) {
                    CastConnection.Disconnected -> onDisconnected()
                    is CastConnection.Connected -> {
                        _state.update { it.copy(connectedDevice = connection.deviceName) }
                        startIfReady()
                    }
                }
            }
        }
    }

    /** Replaces the current Media File, if there is one. */
    fun chooseMediaFile(file: MediaFile) {
        mediaFile = file
        _state.update { it.copy(mediaFileName = file.name, playback = PlaybackStatus.None) }
        startIfReady()
    }

    fun onMediaFileUnreadable() {
        playbackJob?.cancel()
        endPlayback(PlaybackStatus.Failed)
    }

    /** The answer to [PlaybackStatus.NeedsLocalNetwork]. */
    fun onLocalNetworkAnswer(allowed: Boolean) {
        if (allowed) {
            startIfReady()
        } else {
            _state.update { it.copy(playback = PlaybackStatus.LocalNetworkDenied) }
        }
    }

    private fun onDisconnected() {
        playbackJob?.cancel()
        val hadPlayback = _state.value.playback in setOf(PlaybackStatus.Loading, PlaybackStatus.Playing)
        if (hadPlayback) endPlayback(PlaybackStatus.None)
        _state.update { it.copy(connectedDevice = null, playback = PlaybackStatus.None) }
    }

    private fun startIfReady() {
        val file = mediaFile ?: return
        if (_state.value.connectedDevice == null) return
        val previous = playbackJob
        previous?.cancel()
        if (!localNetworkAllowed()) {
            _state.update { it.copy(playback = PlaybackStatus.NeedsLocalNetwork) }
            return
        }
        _state.update { it.copy(playback = PlaybackStatus.Loading) }
        playbackJob = scope.launch {
            // The previous Playback stops the Media Server as it ends, so it must finish before this one starts it.
            previous?.cancelAndJoin()
            play(file)
        }
    }

    private suspend fun play(file: MediaFile) {
        try {
            val url = mediaServer.start(file)
            if (url == null) {
                endPlayback(PlaybackStatus.Failed)
                return
            }
            gateway.load(CastMedia(url, file.contentType, file.name)).collect { status ->
                when (status) {
                    CastMediaStatus.Playing -> _state.update { it.copy(playback = PlaybackStatus.Playing) }
                    CastMediaStatus.Finished -> endPlayback(PlaybackStatus.None)
                    CastMediaStatus.Failed -> endPlayback(PlaybackStatus.Failed)
                }
            }
        } finally {
            withContext(NonCancellable) { mediaServer.stop() }
        }
    }

    private fun endPlayback(status: PlaybackStatus) {
        mediaFile = null
        _state.update { it.copy(mediaFileName = null, playback = status) }
    }
}
