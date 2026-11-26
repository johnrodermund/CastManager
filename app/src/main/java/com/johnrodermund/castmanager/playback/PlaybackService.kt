package com.johnrodermund.castmanager.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.johnrodermund.castmanager.CastManagerApp
import com.johnrodermund.castmanager.MainActivity
import com.johnrodermund.castmanager.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 1
private const val CHANNEL_ID = "playback"
private const val ACTION_PLAY_PAUSE = "com.johnrodermund.castmanager.PLAY_PAUSE"
private const val ACTION_STOP = "com.johnrodermund.castmanager.STOP"
private const val LOCK_TAG = "CastManager:Playback"

/** Keeps the app and its Media Server alive during Playback. It holds no Playback rules of its own. */
class PlaybackService : Service() {
    private val scope = MainScope()
    private val controller get() = (application as CastManagerApp).playbackController

    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        @Suppress("DEPRECATION") // The low latency mode that replaces this needs the screen on.
        val wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = getSystemService(WifiManager::class.java).createWifiLock(wifiMode, LOCK_TAG)
        wifiLock.setReferenceCounted(false)
        wifiLock.acquire()
        // The Wi-Fi lock keeps the radio on. Serving the file with the screen off also needs the CPU.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG)
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire()

        scope.launch {
            controller.state
                .distinctUntilChangedBy { Triple(it.mediaFileName, it.connectedDevice, it.playback) }
                .collect(::onStateChanged)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(controller.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayback()
            ACTION_STOP -> controller.stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        wakeLock.release()
        wifiLock.release()
        super.onDestroy()
    }

    private fun togglePlayback() {
        if (controller.state.value.playback == PlaybackStatus.Paused) controller.play() else controller.pause()
    }

    private fun onStateChanged(state: PlaybackState) {
        if (state.isPlaybackActive) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notification(state: PlaybackState): Notification {
        val paused = state.playback == PlaybackStatus.Paused
        val status = if (paused) R.string.notification_paused else R.string.notification_playing
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.mediaFileName)
            .setContentText(getString(status, state.connectedDevice))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(if (paused) R.string.play else R.string.pause), serviceIntent(ACTION_PLAY_PAUSE))
            .addAction(0, getString(R.string.stop), serviceIntent(ACTION_STOP))
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun Context.serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
