package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER_SHIFT_ALARM = "com.example.alarm.ACTION_TRIGGER_SHIFT_ALARM"
        const val ACTION_TRIGGER_AI_ALARM = "com.example.alarm.ACTION_TRIGGER_AI_ALARM"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_ALARM_RINGTONE = "extra_alarm_ringtone"
        const val EXTRA_ALARM_VIBRATE = "extra_alarm_vibrate"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"

        const val EXTRA_AI_ID = "extra_ai_id"
        const val EXTRA_AI_QUERY = "extra_ai_query"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d("AlarmReceiver", "Received alarm action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.rescheduleAllAlarms(context)
                    Log.d("AlarmReceiver", "Rescheduled successfully after reboot.")
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Reboot reschedule failed: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (action == ACTION_TRIGGER_SHIFT_ALARM) {
            val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
            val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Báo thức"
            val ringtone = intent.getStringExtra(EXTRA_ALARM_RINGTONE) ?: "Mặc định"
            val vibrate = intent.getBooleanExtra(EXTRA_ALARM_VIBRATE, true)
            val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: "Ca"

            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
                putExtra(EXTRA_ALARM_RINGTONE, ringtone)
                putExtra(EXTRA_ALARM_VIBRATE, vibrate)
                putExtra(EXTRA_PROFILE_NAME, profileName)
            }

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to start AlarmService: ${e.message}")
            }

            // Rolling reschedule
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.rescheduleAllAlarms(context)
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (action == ACTION_TRIGGER_AI_ALARM) {
            val aiId = intent.getLongExtra(EXTRA_AI_ID, -1)
            val query = intent.getStringExtra(EXTRA_AI_QUERY) ?: "Thời tiết hôm nay"

            val serviceIntent = Intent(context, AiQueryService::class.java).apply {
                putExtra(EXTRA_AI_ID, aiId)
                putExtra(EXTRA_AI_QUERY, query)
            }

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to start AiQueryService: ${e.message}")
            }

            // Rolling reschedule
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.rescheduleAllAlarms(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
