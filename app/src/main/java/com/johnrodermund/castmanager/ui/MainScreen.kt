package com.johnrodermund.castmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.johnrodermund.castmanager.R
import com.johnrodermund.castmanager.playback.PlaybackState
import com.johnrodermund.castmanager.playback.PlaybackStatus

class MainScreenActions(
    val chooseMediaFile: () -> Unit,
    val castAnyway: () -> Unit,
    val cancelMediaFile: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val seekTo: (Long) -> Unit,
    val stop: () -> Unit,
    val setVolume: (Float) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: PlaybackState,
    actions: MainScreenActions,
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
            if (state.playback == PlaybackStatus.Playing || state.playback == PlaybackStatus.Paused) {
                PlaybackControls(state, actions)
            }
            if (state.connectedDevice != null) {
                Text(stringResource(R.string.volume))
                Slider(value = state.volume, onValueChange = actions.setVolume)
            }
            Button(onClick = actions.chooseMediaFile) {
                Text(stringResource(R.string.choose_media_file))
            }
        }
    }
    if (state.playback == PlaybackStatus.RiskyFormat) {
        AlertDialog(
            onDismissRequest = actions.cancelMediaFile,
            text = { Text(stringResource(R.string.risky_format)) },
            confirmButton = {
                TextButton(onClick = actions.castAnyway) { Text(stringResource(R.string.cast_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = actions.cancelMediaFile) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PlaybackControls(state: PlaybackState, actions: MainScreenActions) {
    // While dragging, the slider follows the finger rather than the Cast Device's reports.
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val position = dragPosition ?: state.positionMillis.toFloat()
    val duration = state.durationMillis.toFloat().coerceAtLeast(1f)
    Slider(
        value = position.coerceAtMost(duration),
        valueRange = 0f..duration,
        onValueChange = { dragPosition = it },
        onValueChangeFinished = {
            dragPosition?.let { actions.seekTo(it.toLong()) }
            dragPosition = null
        },
        enabled = state.durationMillis > 0,
    )
    Text(stringResource(R.string.position, formatTime(position.toLong()), formatTime(state.durationMillis)))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.playback == PlaybackStatus.Playing) {
            Button(onClick = actions.pause) { Text(stringResource(R.string.pause)) }
        } else {
            Button(onClick = actions.play) { Text(stringResource(R.string.play)) }
        }
        OutlinedButton(onClick = actions.stop) { Text(stringResource(R.string.stop)) }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
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
    PlaybackStatus.RiskyFormat, PlaybackStatus.NeedsLocalNetwork -> null
    PlaybackStatus.LocalNetworkDenied -> stringResource(R.string.local_network_denied)
    PlaybackStatus.Loading -> stringResource(R.string.playback_loading)
    PlaybackStatus.Playing -> stringResource(R.string.playback_playing)
    PlaybackStatus.Paused -> stringResource(R.string.playback_paused)
    PlaybackStatus.Unreadable -> stringResource(R.string.media_file_unreadable)
    PlaybackStatus.NotOnWifi -> stringResource(R.string.not_on_wifi)
    PlaybackStatus.Failed -> stringResource(R.string.playback_failed)
}
