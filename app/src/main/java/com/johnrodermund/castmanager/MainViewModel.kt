package com.johnrodermund.castmanager

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnrodermund.castmanager.cast.GoogleCastSessionGateway
import com.johnrodermund.castmanager.media.MediaServer
import com.johnrodermund.castmanager.media.mediaFile
import com.johnrodermund.castmanager.media.wifiAddress
import com.johnrodermund.castmanager.playback.PlaybackController
import com.johnrodermund.castmanager.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

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
        val file = getApplication<Application>().contentResolver.mediaFile(uri)
        if (file != null) controller.chooseMediaFile(file) else controller.onMediaFileUnreadable()
    }

    fun onLocalNetworkAnswer(allowed: Boolean) = controller.onLocalNetworkAnswer(allowed)

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
