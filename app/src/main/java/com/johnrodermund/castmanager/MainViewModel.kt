package com.johnrodermund.castmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnrodermund.castmanager.cast.GoogleCastSessionGateway
import com.johnrodermund.castmanager.playback.PlaybackController
import com.johnrodermund.castmanager.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = GoogleCastSessionGateway(application)
    private val controller = PlaybackController(gateway, viewModelScope)

    val state: StateFlow<PlaybackState> = controller.state

    override fun onCleared() {
        gateway.close()
    }
}
