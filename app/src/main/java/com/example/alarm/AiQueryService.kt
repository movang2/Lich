package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AiQueryService : Service() {
    companion object {
        private const val TAG = "AiQueryService"
        private const val CHANNEL_ID = "ai_query_channel"
        private const val FOREGROUND_ID = 2001
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val queryId = intent?.getLongExtra(AlarmReceiver.EXTRA_AI_ID, -1) ?: -1
        val query = intent?.getStringExtra(AlarmReceiver.EXTRA_AI_QUERY) ?: "Nhắc nhở chu kỳ mới"

        Log.d(TAG, "Starting alarm cycle for queryId=$queryId: '$query'")

        // 1. Create notification channel
        createNotificationChannel()

        // 2. Setup pending intent to open App if clicked
        val intentToOpenApp = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            (4000 + queryId).toInt(),
            intentToOpenApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Post user alarm notification directly without AI
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lịch Chu Kỳ Lớn ⏰")
            .setContentText(query)
            .setStyle(NotificationCompat.BigTextStyle().bigText(query))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        // Since it is a foreground service, startForeground must be called immediately
        startForeground(FOREGROUND_ID, notification)

        // Also notify with a unique id so notifications stack
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val uniqueId = (3000 + queryId).toInt()
        notificationManager.notify(uniqueId, notification)

        // Finish the foreground service immediately
        stopSelf()

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lịch chu kỳ lớn",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh thông báo nhắc nhở theo lịch chu kỳ lớn"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
