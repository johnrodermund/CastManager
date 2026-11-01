package com.johnrodermund.castmanager

import android.Manifest
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
import com.johnrodermund.castmanager.playback.PlaybackStatus
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
