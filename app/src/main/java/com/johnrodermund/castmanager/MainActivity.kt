package com.johnrodermund.castmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johnrodermund.castmanager.cast.CastButton
import com.johnrodermund.castmanager.playback.PlaybackService
import com.johnrodermund.castmanager.playback.PlaybackStatus
import com.johnrodermund.castmanager.playback.isPlaybackActive
import com.johnrodermund.castmanager.ui.MainScreen
import com.johnrodermund.castmanager.ui.MainScreenActions

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onMediaFileChosen)
    }

    private val localNetworkRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        viewModel.onLocalNetworkAnswer(allowed)
    }

    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        // The service's first notification was dropped while this was unanswered, so it has to post it again.
        if (allowed && viewModel.state.value.isPlaybackActive) {
            startForegroundService(Intent(this, PlaybackService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            LaunchedEffect(state.playback) {
                if (state.playback == PlaybackStatus.NeedsLocalNetwork) {
                    localNetworkRequest.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
                if (state.isPlaybackActive) askForNotifications()
            }
            MaterialTheme(colorScheme = colors) {
                MainScreen(
                    state = state,
                    actions = MainScreenActions(
                        chooseMediaFile = { filePicker.launch(arrayOf("video/*", "audio/*")) },
                        castAnyway = viewModel::castAnyway,
                        cancelMediaFile = viewModel::cancelMediaFile,
                        play = viewModel::play,
                        pause = viewModel::pause,
                        seekTo = viewModel::seekTo,
                        stop = viewModel::stop,
                        setVolume = viewModel::setVolume,
                    ),
                    castButton = { CastButton() },
                )
            }
        }
    }

    /** Playback works without the notification, so the answer doesn't matter. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || viewModel.askedForNotifications) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        viewModel.askedForNotifications = true
        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** During a Cast Session, the phone's volume buttons change the Cast Device's volume. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val change = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VOLUME_STEP
            KeyEvent.KEYCODE_VOLUME_DOWN -> -VOLUME_STEP
            else -> return super.dispatchKeyEvent(event)
        }
        if (viewModel.state.value.connectedDevice == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) viewModel.changeVolume(change)
        return true
    }

    private companion object {
        const val VOLUME_STEP = 0.05f
    }
}
