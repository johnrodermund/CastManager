package com.johnrodermund.castmanager

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.johnrodermund.castmanager.cast.GoogleCastSessionGateway
import com.johnrodermund.castmanager.media.MediaServer
import com.johnrodermund.castmanager.media.releaseAllMediaFiles
import com.johnrodermund.castmanager.media.wifiAddress
import com.johnrodermund.castmanager.playback.PlaybackController
import com.johnrodermund.castmanager.playback.PlaybackService
import com.johnrodermund.castmanager.playback.isPlaybackActive
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Owns the Playback Controller, so that Playback outlives the screen. */
class CastManagerApp : Application() {
    private val scope = MainScope()

    val playbackController: PlaybackController by lazy {
        PlaybackController(
            gateway = GoogleCastSessionGateway(this),
            mediaServer = MediaServer(hostAddress = ::wifiAddress),
            localNetworkAllowed = ::localNetworkAllowed,
            scope = scope,
        ).also(::startServiceDuringPlayback)
    }

    override fun onCreate() {
        super.onCreate()
        // No Playback survives the process, so any grant still held is left over from one that did not end cleanly.
        contentResolver.releaseAllMediaFiles()
    }

    private fun startServiceDuringPlayback(controller: PlaybackController) {
        scope.launch {
            controller.state
                .map { it.isPlaybackActive }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) startForegroundService(Intent(this@CastManagerApp, PlaybackService::class.java))
                }
        }
    }

    private fun localNetworkAllowed(): Boolean =
        Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LOCAL_NETWORK_PERMISSION_SDK = 37
    }
}
