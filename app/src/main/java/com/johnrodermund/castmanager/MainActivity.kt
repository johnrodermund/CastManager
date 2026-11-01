package com.johnrodermund.castmanager

import android.Manifest
import android.os.Bundle
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
                    onChooseMediaFile = { filePicker.launch(arrayOf("video/*", "audio/*")) },
                    castButton = { CastButton() },
                )
            }
        }
    }
}
