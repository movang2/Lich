package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmService : Service() {
    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_DISMISS = "com.example.alarm.AlarmService.ACTION_DISMISS"
        private const val CHANNEL_ID = "shift_alarm_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISMISS) {
            Log.d(TAG, "Dismiss action received, stopping service.")
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "Báo thức"
        val profileName = intent?.getStringExtra(AlarmReceiver.EXTRA_PROFILE_NAME) ?: "Lịch ca"
        val vibrate = intent?.getBooleanExtra(AlarmReceiver.EXTRA_ALARM_VIBRATE, true) ?: true

        Log.d(TAG, "Starting alarm sound and vibration for '$label' ($profileName)")

        // 1. Create Notification Channel
        createNotificationChannel()

        // 2. Build full-screen intent to let user stop easily
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Dismiss button pending intent
        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            this.action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            12,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đặt bởi: $profileName")
            .setContentText("⏰ $label")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "TẮT BÁO THỨC", dismissPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 4. Play alarm sound
        startSound()

        // 5. Start vibration if enabled
        if (vibrate) {
            startVibration()
        }

        return START_STICKY
    }

    private fun startSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm media: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 500, 500)
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    v.vibrate(longArrayOf(0, 500, 500), 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.run {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mediaPlayer: ${e.message}")
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibrator: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Báo thức lịch ca",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh truyền tải thông báo và âm thanh báo thức ca xoay vòng"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
