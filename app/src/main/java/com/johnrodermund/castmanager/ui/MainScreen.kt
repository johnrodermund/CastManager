package com.johnrodermund.castmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.johnrodermund.castmanager.R
import com.johnrodermund.castmanager.playback.PlaybackState
import com.johnrodermund.castmanager.playback.PlaybackStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: PlaybackState,
    onChooseMediaFile: () -> Unit,
    castButton: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { castButton() },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text(
                text = state.connectedDevice?.let { stringResource(R.string.connected_to, it) }
                    ?: stringResource(R.string.not_connected),
                style = MaterialTheme.typography.titleMedium,
            )
            state.mediaFileName?.let { Text(stringResource(R.string.media_file, it)) }
            statusMessage(state)?.let { Text(it) }
            Button(onClick = onChooseMediaFile) {
                Text(stringResource(R.string.choose_media_file))
            }
        }
    }
}

@Composable
private fun statusMessage(state: PlaybackState): String? = when (state.playback) {
    PlaybackStatus.None -> {
        if (state.mediaFileName != null && state.connectedDevice == null) {
            stringResource(R.string.connect_to_play)
        } else {
            null
        }
    }
    PlaybackStatus.NeedsLocalNetwork -> null
    PlaybackStatus.LocalNetworkDenied -> stringResource(R.string.local_network_denied)
    PlaybackStatus.Loading -> stringResource(R.string.playback_loading)
    PlaybackStatus.Playing -> stringResource(R.string.playback_playing)
    PlaybackStatus.Failed -> stringResource(R.string.playback_failed)
}
