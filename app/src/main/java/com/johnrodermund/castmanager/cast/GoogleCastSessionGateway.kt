package com.johnrodermund.castmanager.cast

import android.content.Context
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

private const val PROGRESS_INTERVAL_MILLIS = 1000L

class GoogleCastSessionGateway(context: Context) : CastSessionGateway {
    private val sessionManager = CastContext.getSharedInstance(context).sessionManager
    private val _connection = MutableStateFlow(sessionManager.currentCastSession.toConnection())
    override val connection: StateFlow<CastConnection> = _connection

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume

    private val volumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            sessionManager.currentCastSession?.let(::updateVolume)
        }
    }

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = connected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connected(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = disconnected(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = disconnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = disconnected(session)
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        sessionManager.currentCastSession?.let(::connected)
    }

    override fun load(media: CastMedia): Flow<CastMediaStatus> = callbackFlow {
        val client = sessionManager.currentCastSession?.remoteMediaClient
        if (client == null) {
            send(CastMediaStatus.Failed)
            close()
            return@callbackFlow
        }
        val callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                // Until the receiver reports this media, its status is about whatever it played before.
                if (client.mediaInfo?.contentId != media.url) return
                when (client.playerState) {
                    MediaStatus.PLAYER_STATE_PLAYING -> trySend(CastMediaStatus.Playing)
                    MediaStatus.PLAYER_STATE_PAUSED -> trySend(CastMediaStatus.Paused)
                    MediaStatus.PLAYER_STATE_IDLE -> when (client.idleReason) {
                        MediaStatus.IDLE_REASON_ERROR -> finish(CastMediaStatus.Failed)
                        MediaStatus.IDLE_REASON_FINISHED,
                        MediaStatus.IDLE_REASON_CANCELED -> finish(CastMediaStatus.Finished)
                    }
                }
            }

            private fun finish(status: CastMediaStatus) {
                trySend(status)
                close()
            }
        }
        val progressListener = RemoteMediaClient.ProgressListener { position, duration ->
            if (client.mediaInfo?.contentId == media.url) trySend(CastMediaStatus.Progress(position, duration))
        }
        client.registerCallback(callback)
        client.addProgressListener(progressListener, PROGRESS_INTERVAL_MILLIS)
        // Success only means the receiver took the request. The file can still fail, which arrives as a status.
        client.load(media.toMediaInfo()).setResultCallback { result ->
            if (!result.status.isSuccess) {
                trySend(CastMediaStatus.Failed)
                close()
            }
        }
        awaitClose {
            client.removeProgressListener(progressListener)
            client.unregisterCallback(callback)
        }
    }

    override fun play() {
        mediaClient?.play()
    }

    override fun pause() {
        mediaClient?.pause()
    }

    override fun seekTo(positionMillis: Long) {
        mediaClient?.seek(MediaSeekOptions.Builder().setPosition(positionMillis).build())
    }

    override fun stop() {
        mediaClient?.stop()
    }

    override fun setVolume(volume: Float) {
        try {
            sessionManager.currentCastSession?.volume = volume.toDouble()
        } catch (_: IOException) {
            // The Cast Session is failing, which its listener reports.
        }
    }

    fun close() {
        sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
        sessionManager.currentCastSession?.removeCastListener(volumeListener)
    }

    private val mediaClient get() = sessionManager.currentCastSession?.remoteMediaClient

    private fun connected(session: CastSession) {
        session.removeCastListener(volumeListener)
        session.addCastListener(volumeListener)
        updateVolume(session)
        _connection.value = session.toConnection()
    }

    private fun disconnected(session: CastSession) {
        session.removeCastListener(volumeListener)
        _connection.value = CastConnection.Disconnected
    }

    private fun updateVolume(session: CastSession) {
        if (session.isConnected) _volume.value = session.volume.toFloat()
    }

    private fun CastSession?.toConnection(): CastConnection {
        val device = this?.takeIf { it.isConnected }?.castDevice ?: return CastConnection.Disconnected
        return CastConnection.Connected(device.friendlyName.orEmpty())
    }

    private fun CastMedia.toMediaInfo(): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        metadata.putString(MediaMetadata.KEY_TITLE, title)
        return MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()
    }
}
