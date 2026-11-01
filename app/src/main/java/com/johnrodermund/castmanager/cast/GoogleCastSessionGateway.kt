package com.johnrodermund.castmanager.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

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

    override suspend fun load(media: CastMedia): Boolean {
        val client = sessionManager.currentCastSession?.remoteMediaClient ?: return false
        return suspendCancellableCoroutine { continuation ->
            client.load(media.toMediaInfo()).setResultCallback { result ->
                continuation.resume(result.status.isSuccess)
            }
        }
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
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        metadata.putString(MediaMetadata.KEY_TITLE, title)
        return MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()
    }
}
