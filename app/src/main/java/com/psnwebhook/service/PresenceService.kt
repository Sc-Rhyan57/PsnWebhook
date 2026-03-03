package com.psnwebhook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.psnwebhook.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PresenceService : Service() {

    companion object {
        const val CHANNEL_ID = "psn_presence_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "STOP_PRESENCE"
        const val EXTRA_TITLE_ID = "titleId"
        const val EXTRA_TITLE_NAME = "titleName"
        const val EXTRA_ACCESS_TOKEN = "accessToken"

        fun startIntent(context: Context, titleId: String, titleName: String, accessToken: String) =
            Intent(context, PresenceService::class.java).apply {
                putExtra(EXTRA_TITLE_ID, titleId)
                putExtra(EXTRA_TITLE_NAME, titleName)
                putExtra(EXTRA_ACCESS_TOKEN, accessToken)
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var presenceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val titleId = intent?.getStringExtra(EXTRA_TITLE_ID) ?: ""
        val titleName = intent?.getStringExtra(EXTRA_TITLE_NAME) ?: "Unknown"
        val accessToken = intent?.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""

        startForeground(NOTIFICATION_ID, buildNotification(titleName))

        presenceJob?.cancel()
        presenceJob = scope.launch {
            while (isActive) {
                try {
                    com.psnwebhook.psn.PsnApiClient.setPresence(accessToken, titleId)
                } catch (_: Exception) {}
                delay(60_000L)
            }
        }

        return START_STICKY
    }

    private fun buildNotification(titleName: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, PresenceService::class.java).apply { action = ACTION_STOP },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PSN Webhook ativo")
            .setContentText("Jogando: $titleName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_delete, "Parar", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Presença PSN", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
