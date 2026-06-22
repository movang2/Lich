package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ShiftAlarm
import com.example.data.ShiftProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    // Helper to calculate midnight millis
    private fun getMidnightMillis(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Determine the active profile on a specific date in the future
    suspend fun getActiveProfileForDate(
        context: Context,
        db: AppDatabase,
        targetTimeMillis: Long
    ): ShiftProfile? {
        val appSettingDao = db.appSettingDao()
        val shiftProfileDao = db.shiftProfileDao()

        // Get settings
        val activeProfileIdSetting = appSettingDao.getSetting("active_profile_id")?.value?.toLongOrNull() ?: return null
        val rotationEnabled = appSettingDao.getSetting("rotation_enabled")?.value?.toBoolean() ?: false
        val cycleStartDateSetting = appSettingDao.getSetting("cycle_start_date")?.value?.toLongOrNull() ?: System.currentTimeMillis()
        val rotationSequenceSetting = appSettingDao.getSetting("rotation_sequence")?.value ?: ""
        val cycleDaysVal = appSettingDao.getSetting("cycle_days")?.value?.toIntOrNull() ?: 7

        if (!rotationEnabled || rotationSequenceSetting.isEmpty()) {
            return shiftProfileDao.getProfileById(activeProfileIdSetting)
        }

        val profileIdList = rotationSequenceSetting.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (profileIdList.isEmpty()) {
            return shiftProfileDao.getProfileById(activeProfileIdSetting)
        }

        // Calculate days difference
        val targetMidnight = getMidnightMillis(targetTimeMillis)
        val startMidnight = getMidnightMillis(cycleStartDateSetting)

        val diffMillis = targetMidnight - startMidnight
        val diffDays = (diffMillis / (24L * 60 * 60 * 1000)).toInt()

        // If target date is before the start date, return the base profile or calculate backwards
        val adjustedDays = if (diffDays < 0) {
            val seqSize = profileIdList.size * cycleDaysVal
            val positiveDiff = (diffDays % seqSize) + seqSize
            positiveDiff
        } else {
            diffDays
        }

        val rotationIndex = (adjustedDays / cycleDaysVal) % profileIdList.size
        val profileId = profileIdList[rotationIndex]

        return shiftProfileDao.getProfileById(profileId)
    }

    // Main routine to reschedule all alarms for the next 7 days
    suspend fun rescheduleAllAlarms(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext
        
        Log.d(TAG, "Starting rescheduling...")

        val globalLevelVal = db.appSettingDao().getSetting("global_alarm_level")?.value ?: "CUSTOM"
        Log.d(TAG, "Global alarm level configuration: $globalLevelVal")

        // Fetch AI Alarms
        val aiAlarms = db.aiQueryAlarmDao().getEnabledAiAlarms()

        // Loop for the next 7 days in the future (0 to 6)
        val now = Calendar.getInstance()
        val todayMidnight = getMidnightMillis(now.timeInMillis)

        for (i in 0..7) {
            val targetCal = Calendar.getInstance()
            targetCal.timeInMillis = todayMidnight
            targetCal.add(Calendar.DAY_OF_YEAR, i)
            val dayOfWeek = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            // A. Standard Shift Alarms for this day
            val activeProfile = getActiveProfileForDate(context, db, targetCal.timeInMillis)
            if (activeProfile != null) {
                val alarms = db.shiftAlarmDao().getEnabledAlarmsForProfileAndDay(activeProfile.id, dayOfWeek)
                for (alarm in alarms) {
                    val triggerCal = Calendar.getInstance()
                    triggerCal.timeInMillis = targetCal.timeInMillis
                    triggerCal.set(Calendar.HOUR_OF_DAY, alarm.hour)
                    triggerCal.set(Calendar.MINUTE, alarm.minute)
                    triggerCal.set(Calendar.SECOND, 0)
                    triggerCal.set(Calendar.MILLISECOND, 0)

                    // Skip if the alarm is in the past
                    if (triggerCal.timeInMillis <= now.timeInMillis) {
                        continue
                    }

                    val resolvedLevel = if (globalLevelVal == "CUSTOM") {
                        alarm.level
                    } else {
                        globalLevelVal
                    }

                    // Schedule
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        action = AlarmReceiver.ACTION_TRIGGER_SHIFT_ALARM
                        putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
                        putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
                        putExtra(AlarmReceiver.EXTRA_ALARM_RINGTONE, alarm.ringTone)
                        putExtra(AlarmReceiver.EXTRA_ALARM_VIBRATE, alarm.vibrate)
                        putExtra(AlarmReceiver.EXTRA_ALARM_LEVEL, resolvedLevel)
                        putExtra(AlarmReceiver.EXTRA_PROFILE_NAME, activeProfile.name)
                    }

                    val requestCode = (100000 + alarm.id * 10 + i).toInt()
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        cancelAlarmSafely(alarmManager, pendingIntent)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            val info = AlarmManager.AlarmClockInfo(triggerCal.timeInMillis, pendingIntent)
                            alarmManager.setAlarmClock(info, pendingIntent)
                            Log.d(TAG, "Scheduled alarm '${alarm.label}' on day index $i, time: ${triggerCal.time}")
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerCal.timeInMillis,
                                pendingIntent
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to schedule alarm: ${e.message}")
                    }
                }
            }

            // B. AI Query Alarms for this day
            for (aiAlarm in aiAlarms) {
                // Check repeating constraints for MONTHLY / YEARLY modes
                when (aiAlarm.repeatType) {
                    "MONTHLY" -> {
                        val alarmDay = aiAlarm.repeatDayOfMonth
                        val targetDay = targetCal.get(Calendar.DAY_OF_MONTH)
                        if (alarmDay != targetDay) {
                            continue // Skip if this target date doesn't match the monthly alarm day
                        }
                    }
                    "YEARLY" -> {
                        val alarmDay = aiAlarm.repeatDayOfMonth
                        val alarmMonth = aiAlarm.repeatMonthOfYear - 1 // 0-indexed in Calendar
                        val targetDay = targetCal.get(Calendar.DAY_OF_MONTH)
                        val targetMonth = targetCal.get(Calendar.MONTH)
                        if (alarmDay != targetDay || alarmMonth != targetMonth) {
                            continue // Skip if day or month doesn't match
                        }
                    }
                }

                val triggerCal = Calendar.getInstance()
                triggerCal.timeInMillis = targetCal.timeInMillis
                triggerCal.set(Calendar.HOUR_OF_DAY, aiAlarm.hour)
                triggerCal.set(Calendar.MINUTE, aiAlarm.minute)
                triggerCal.set(Calendar.SECOND, 0)
                triggerCal.set(Calendar.MILLISECOND, 0)

                if (triggerCal.timeInMillis <= now.timeInMillis) {
                    continue
                }

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_TRIGGER_AI_ALARM
                    putExtra(AlarmReceiver.EXTRA_AI_ID, aiAlarm.id)
                    putExtra(AlarmReceiver.EXTRA_AI_QUERY, aiAlarm.query)
                }

                val requestCode = (200000 + aiAlarm.id * 10 + i).toInt()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    cancelAlarmSafely(alarmManager, pendingIntent)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerCal.timeInMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled AI Alarm index $i (non-exact) at ${triggerCal.time}")
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerCal.timeInMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "Scheduled AI Alarm index $i (exact) at ${triggerCal.time}")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException: exact alarm permission denied. Falling back to non-exact AI alarm.")
                    try {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerCal.timeInMillis,
                            pendingIntent
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to schedule fallback non-exact AI alarm: ${ex.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to schedule AI alarm: ${e.message}")
                }
            }
        }
    }

    private fun cancelAlarmSafely(alarmManager: AlarmManager, pendingIntent: PendingIntent) {
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            // Safe cancel
        }
    }
}
