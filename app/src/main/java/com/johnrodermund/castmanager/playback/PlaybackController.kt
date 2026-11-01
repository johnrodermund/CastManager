package com.johnrodermund.castmanager.playback

import com.johnrodermund.castmanager.cast.CastConnection
import com.johnrodermund.castmanager.cast.CastMedia
import com.johnrodermund.castmanager.cast.CastMediaStatus
import com.johnrodermund.castmanager.cast.CastSessionGateway
import com.johnrodermund.castmanager.media.FormatRisk
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
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val volume: Float = 1f,
)

enum class PlaybackStatus {
    None,
    RiskyFormat,
    NeedsLocalNetwork,
    LocalNetworkDenied,
    Loading,
    Playing,
    Paused,
    Unreadable,
    NotOnWifi,
    Failed,
}

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
    private var unconfirmedRisk = false
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
        scope.launch {
            gateway.volume.collect { volume -> _state.update { it.copy(volume = volume) } }
        }
    }

    /** Replaces the current Media File, if there is one. */
    fun chooseMediaFile(file: MediaFile, risk: FormatRisk = FormatRisk.Safe) {
        mediaFile = file
        unconfirmedRisk = risk == FormatRisk.Risky
        _state.update {
            it.copy(mediaFileName = file.name, playback = PlaybackStatus.None, positionMillis = 0, durationMillis = 0)
        }
        startIfReady()
    }

    fun onMediaFileUnreadable() {
        stop()
        endPlayback(PlaybackStatus.Unreadable)
    }

    /** The answer to [PlaybackStatus.RiskyFormat]. */
    fun castAnyway() {
        unconfirmedRisk = false
        startIfReady()
    }

    /** The other answer to [PlaybackStatus.RiskyFormat]. */
    fun cancelMediaFile() {
        endPlayback(PlaybackStatus.None)
    }

    /** The answer to [PlaybackStatus.NeedsLocalNetwork]. */
    fun onLocalNetworkAnswer(allowed: Boolean) {
        if (allowed) {
            startIfReady()
        } else {
            _state.update { it.copy(playback = PlaybackStatus.LocalNetworkDenied) }
        }
    }

    fun play() = gateway.play()

    fun pause() = gateway.pause()

    fun seekTo(positionMillis: Long) {
        _state.update { it.copy(positionMillis = positionMillis) }
        gateway.seekTo(positionMillis)
    }

    /** Ends Playback and keeps the Cast Session. */
    fun stop() {
        if (stopCurrentPlayback()) endPlayback(PlaybackStatus.None)
    }

    fun setVolume(volume: Float) {
        gateway.setVolume(volume.coerceIn(0f, 1f))
    }

    fun changeVolume(change: Float) {
        setVolume(_state.value.volume + change)
    }

    private fun onDisconnected() {
        playbackJob?.cancel()
        val hadPlayback = _state.value.playback in
            setOf(PlaybackStatus.Loading, PlaybackStatus.Playing, PlaybackStatus.Paused)
        if (hadPlayback) endPlayback(PlaybackStatus.None)
        _state.update { it.copy(connectedDevice = null, playback = PlaybackStatus.None) }
    }

    private fun startIfReady() {
        val file = mediaFile ?: return
        if (_state.value.connectedDevice == null) return
        val previous = playbackJob
        when {
            unconfirmedRisk -> waitFor(PlaybackStatus.RiskyFormat)
            !localNetworkAllowed() -> waitFor(PlaybackStatus.NeedsLocalNetwork)
            else -> {
                previous?.cancel()
                _state.update { it.copy(playback = PlaybackStatus.Loading) }
                playbackJob = scope.launch {
                    // The previous Playback stops the Media Server as it ends, so it must finish before this one starts it.
                    previous?.cancelAndJoin()
                    play(file)
                }
            }
        }
    }

    private fun waitFor(status: PlaybackStatus) {
        stopCurrentPlayback()
        _state.update { it.copy(playback = status) }
    }

    /** Returns whether there was a Playback to stop. */
    private fun stopCurrentPlayback(): Boolean {
        if (playbackJob?.isActive != true) return false
        gateway.stop()
        playbackJob?.cancel()
        return true
    }

    private suspend fun play(file: MediaFile) {
        try {
            val url = mediaServer.start(file)
            if (url == null) {
                endPlayback(PlaybackStatus.NotOnWifi)
                return
            }
            gateway.load(CastMedia(url, file.contentType, file.name)).collect { status ->
                when (status) {
                    CastMediaStatus.Playing -> _state.update { it.copy(playback = PlaybackStatus.Playing) }
                    CastMediaStatus.Paused -> _state.update { it.copy(playback = PlaybackStatus.Paused) }
                    is CastMediaStatus.Progress -> _state.update {
                        it.copy(positionMillis = status.positionMillis, durationMillis = status.durationMillis)
                    }
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
        unconfirmedRisk = false
        _state.update {
            it.copy(mediaFileName = null, playback = status, positionMillis = 0, durationMillis = 0)
        }
    }
}
