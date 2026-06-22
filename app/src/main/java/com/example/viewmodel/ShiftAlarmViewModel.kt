package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.data.AppDatabase
import com.example.data.AppSetting
import com.example.data.ShiftAlarm
import com.example.data.ShiftProfile
import com.example.data.AiQueryAlarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShiftAlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val profileDao = db.shiftProfileDao()
    private val alarmDao = db.shiftAlarmDao()
    private val aiDao = db.aiQueryAlarmDao()
    private val settingDao = db.appSettingDao()

    // 1. All Profiles Flow
    val allProfiles: StateFlow<List<ShiftProfile>> = profileDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Selected profile for editing (defaults to first profile when loaded)
    private val _selectedProfileId = MutableStateFlow<Long?>(null)
    val selectedProfileId: StateFlow<Long?> = _selectedProfileId.asStateFlow()

    // 3. Alarms for currently selected profile
    val alarmsForSelectedProfile: StateFlow<List<ShiftAlarm>> = _selectedProfileId
        .flatMapLatest { profileId ->
            if (profileId != null) {
                alarmDao.getAlarmsForProfile(profileId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. AI Query Alarms Flow
    val allAiAlarms: StateFlow<List<AiQueryAlarm>> = aiDao.getAllAiAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Settings map
    private val _settingsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val settingsMap: StateFlow<Map<String, String>> = _settingsMap.asStateFlow()

    init {
        // Observe settings changes
        viewModelScope.launch {
            settingDao.observeAllSettings().collect { settingsList ->
                val map = settingsList.associate { it.key to it.value }
                _settingsMap.value = map

                // Default selected profile if not set yet
                if (_selectedProfileId.value == null) {
                    val defaultId = map["active_profile_id"]?.toLongOrNull()
                    if (defaultId != null) {
                        _selectedProfileId.value = defaultId
                    } else {
                        // fallback to first profile found
                        val first = profileDao.getAllProfilesList().firstOrNull()
                        if (first != null) {
                            _selectedProfileId.value = first.id
                        }
                    }
                }
            }
        }
    }

    fun selectProfileForEditing(profileId: Long) {
        _selectedProfileId.value = profileId
    }

    // PROFILE MANAGEMENT
    fun addProfile(name: String, description: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = profileDao.insertProfile(ShiftProfile(name = name, description = description))
            // Pre-seed newly created profile with Sunday off and copy blank week
            _selectedProfileId.value = newId
            reschedule()
        }
    }

    fun deleteProfile(profile: ShiftProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            profileDao.deleteProfile(profile)
            // Reset selection if deleted current
            if (_selectedProfileId.value == profile.id) {
                val first = profileDao.getAllProfilesList().firstOrNull()
                _selectedProfileId.value = first?.id
            }
            reschedule()
        }
    }

    // SHIFT ALARM MANAGEMENT
    fun addShiftAlarm(profileId: Long, dayOfWeek: Int, hour: Int, minute: Int, label: String, level: String = "HIGH") {
        viewModelScope.launch(Dispatchers.IO) {
            val alarm = ShiftAlarm(
                profileId = profileId,
                dayOfWeek = dayOfWeek,
                hour = hour,
                minute = minute,
                label = label,
                isEnabled = true,
                level = level
            )
            alarmDao.insertAlarm(alarm)
            reschedule()
        }
    }

    fun updateShiftAlarm(alarm: ShiftAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            alarmDao.updateAlarm(alarm)
            reschedule()
        }
    }

    fun deleteShiftAlarm(alarm: ShiftAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            alarmDao.deleteAlarm(alarm)
            reschedule()
        }
    }

    // COPY FAST FEATURE
    fun copyMondayToWeekdays(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // Get Monday alarms
            val mondayAlarms = alarmDao.getAlarmsForProfileAndDayList(profileId, 1)
            
            // Delete existing alarms for Tue-Fri (2 to 5)
            for (day in 2..5) {
                alarmDao.deleteAlarmsForProfileAndDay(profileId, day)
            }

            // Insert copies
            val listToInsert = mutableListOf<ShiftAlarm>()
            for (day in 2..5) {
                for (monAlarm in mondayAlarms) {
                    listToInsert.add(
                        ShiftAlarm(
                            profileId = profileId,
                            dayOfWeek = day,
                            hour = monAlarm.hour,
                            minute = monAlarm.minute,
                            label = monAlarm.label,
                            isEnabled = monAlarm.isEnabled,
                            ringTone = monAlarm.ringTone,
                            vibrate = monAlarm.vibrate,
                            level = monAlarm.level
                        )
                    )
                }
            }

            if (listToInsert.isNotEmpty()) {
                alarmDao.insertAlarms(listToInsert)
            }
            reschedule()
        }
    }

    // AI ALARM MANAGEMENT
    fun addAiAlarm(
        hour: Int,
        minute: Int,
        query: String,
        repeatType: String = "DAILY",
        repeatDayOfMonth: Int = 1,
        repeatMonthOfYear: Int = 1
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            aiDao.insertAiAlarm(
                AiQueryAlarm(
                    hour = hour,
                    minute = minute,
                    query = query,
                    repeatType = repeatType,
                    repeatDayOfMonth = repeatDayOfMonth,
                    repeatMonthOfYear = repeatMonthOfYear
                )
            )
            reschedule()
        }
    }

    fun updateAiAlarm(alarm: AiQueryAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            aiDao.updateAiAlarm(alarm)
            reschedule()
        }
    }

    fun deleteAiAlarm(alarm: AiQueryAlarm) {
        viewModelScope.launch(Dispatchers.IO) {
            aiDao.deleteAiAlarm(alarm)
            reschedule()
        }
    }

    // SETTINGS MANAGEMENT
    fun saveSetting(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.insertSetting(AppSetting(key, value))
            reschedule()
        }
    }

    // Helper to trigger background rescheduling whenever data is altered
    private fun reschedule() {
        viewModelScope.launch {
            try {
                AlarmScheduler.rescheduleAllAlarms(getApplication())
            } catch (e: Exception) {
                // handle rescheduling errs
            }
        }
    }
}
