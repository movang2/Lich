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
        val query = intent?.getStringExtra(AlarmReceiver.EXTRA_AI_QUERY) ?: "Tra cứu hôm nay"

        Log.d(TAG, "Starting AI query for: '$query'")

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

        // 3. Show temporary foreground notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Báo thức AI đang cập nhật...")
            .setContentText("🔍 Đang tra cứu: \"$query\"")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)

        startForeground(FOREGROUND_ID, builder.build())

        // 4. Fire coroutine for Gemini lookup
        serviceScope.launch {
            try {
                val aiResponse = GeminiClient.queryGemini(query)
                Log.d(TAG, "Gemini Response length: ${aiResponse.length}")

                // Post final user notification
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val finalNotification = NotificationCompat.Builder(this@AiQueryService, CHANNEL_ID)
                    .setContentTitle("Trợ lý AI Báo Thức 🤖")
                    .setContentText(aiResponse)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(aiResponse))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(pendingIntent)
                    .setSound(soundUri)
                    .setVibrate(longArrayOf(0, 300, 200, 300))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // Post unique notification so they can stack if multiple scheduled
                val uniqueId = (3000 + queryId).toInt()
                notificationManager.notify(uniqueId, finalNotification)

            } catch (e: Exception) {
                Log.e(TAG, "Gemini service search failed: ${e.message}")
            } finally {
                // Free service
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trợ lý Báo thức AI (Gemini)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh cập nhật dữ liệu tự động cho báo thức kết hợp AI"
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
