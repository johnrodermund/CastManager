package com.johnrodermund.castmanager.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

class GoogleCastSessionGateway(context: Context) : CastSessionGateway {
    private val sessionManager = CastContext.getSharedInstance(context).sessionManager
    private val _connection = MutableStateFlow(sessionManager.currentCastSession.toConnection())
    override val connection: StateFlow<CastConnection> = _connection

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = connected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connected(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = disconnected()
        override fun onSessionResumeFailed(session: CastSession, error: Int) = disconnected()
        override fun onSessionEnded(session: CastSession, error: Int) = disconnected()
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
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
                when (client.playerState) {
                    MediaStatus.PLAYER_STATE_PLAYING -> trySend(CastMediaStatus.Playing)
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
        client.registerCallback(callback)
        // Success only means the receiver took the request. The file can still fail, which arrives as a status.
        client.load(media.toMediaInfo()).setResultCallback { result ->
            if (!result.status.isSuccess) {
                trySend(CastMediaStatus.Failed)
                close()
            }
        }
        awaitClose { client.unregisterCallback(callback) }
    }

    fun close() {
        sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
    }

    private fun connected(session: CastSession) {
        _connection.value = session.toConnection()
    }

    private fun disconnected() {
        _connection.value = CastConnection.Disconnected
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
