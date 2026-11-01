package com.johnrodermund.castmanager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnrodermund.castmanager.media.FormatCheck
import com.johnrodermund.castmanager.media.FormatRisk
import com.johnrodermund.castmanager.media.mediaFile
import com.johnrodermund.castmanager.media.mediaFormat
import com.johnrodermund.castmanager.playback.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = (application as CastManagerApp).playbackController

    val state: StateFlow<PlaybackState> = controller.state

    /** Android only lets the app ask a few times, so it asks once each time the app opens. */
    var askedForNotifications = false

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
}
