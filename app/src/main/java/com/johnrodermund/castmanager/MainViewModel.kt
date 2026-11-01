package com.johnrodermund.castmanager

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnrodermund.castmanager.cast.GoogleCastSessionGateway
import com.johnrodermund.castmanager.media.FormatCheck
import com.johnrodermund.castmanager.media.FormatRisk
import com.johnrodermund.castmanager.media.MediaServer
import com.johnrodermund.castmanager.media.mediaFile
import com.johnrodermund.castmanager.media.mediaFormat
import com.johnrodermund.castmanager.media.wifiAddress
import com.johnrodermund.castmanager.playback.PlaybackController
import com.johnrodermund.castmanager.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GoogleCastSessionGateway(application)
    private val controller = PlaybackController(
        gateway = gateway,
        mediaServer = MediaServer(hostAddress = application::wifiAddress),
        localNetworkAllowed = ::localNetworkAllowed,
        scope = viewModelScope,
    )

    val state: StateFlow<PlaybackState> = controller.state

    fun onMediaFileChosen(uri: Uri) {
        val application = getApplication<Application>()
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { application.contentResolver.mediaFile(uri) }
            if (file == null) {
                controller.onMediaFileUnreadable()
                return@launch
            }
            val format = withContext(Dispatchers.IO) { application.mediaFormat(uri, file.contentType) }
            controller.chooseMediaFile(file, format?.let(FormatCheck::check) ?: FormatRisk.Risky)
        }
    }

    fun onLocalNetworkAnswer(allowed: Boolean) = controller.onLocalNetworkAnswer(allowed)

    fun castAnyway() = controller.castAnyway()

    fun cancelMediaFile() = controller.cancelMediaFile()

    fun play() = controller.play()

    fun pause() = controller.pause()

    fun seekTo(positionMillis: Long) = controller.seekTo(positionMillis)

    fun stop() = controller.stop()

    fun setVolume(volume: Float) = controller.setVolume(volume)

    fun changeVolume(change: Float) = controller.changeVolume(change)

    private fun localNetworkAllowed(): Boolean =
        Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK ||
            getApplication<Application>().checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        gateway.close()
    }

    private companion object {
        const val LOCAL_NETWORK_PERMISSION_SDK = 37
    }
}
