package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(tableName = "shift_profiles")
data class ShiftProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = ""
)

@Entity(
    tableName = "shift_alarms",
    foreignKeys = [
        ForeignKey(
            entity = ShiftProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class ShiftAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val dayOfWeek: Int, // 1 (Mon) to 7 (Sun)
    val hour: Int,
    val minute: Int,
    val label: String,
    val isEnabled: Boolean = true,
    val ringTone: String = "Mặc định",
    val vibrate: Boolean = true,
    val level: String = "HIGH" // "LIGHT", "MEDIUM", "HIGH"
)

@Entity(tableName = "ai_query_alarms")
data class AiQueryAlarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val query: String,
    val isEnabled: Boolean = true,
    val repeatType: String = "DAILY", // "DAILY", "MONTHLY", "YEARLY"
    val repeatDayOfMonth: Int = 1,     // 1 to 31
    val repeatMonthOfYear: Int = 1     // 1 to 12 (for YEARLY mode)
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface ShiftProfileDao {
    @Query("SELECT * FROM shift_profiles ORDER BY id ASC")
    fun getAllProfiles(): Flow<List<ShiftProfile>>

    @Query("SELECT * FROM shift_profiles ORDER BY id ASC")
    suspend fun getAllProfilesList(): List<ShiftProfile>

    @Query("SELECT * FROM shift_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ShiftProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ShiftProfile): Long

    @Update
    suspend fun updateProfile(profile: ShiftProfile)

    @Delete
    suspend fun deleteProfile(profile: ShiftProfile)
}

@Dao
interface ShiftAlarmDao {
    @Query("SELECT * FROM shift_alarms WHERE profileId = :profileId ORDER BY hour ASC, minute ASC")
    fun getAlarmsForProfile(profileId: Long): Flow<List<ShiftAlarm>>

    @Query("SELECT * FROM shift_alarms WHERE profileId = :profileId AND dayOfWeek = :dayOfWeek AND isEnabled = 1")
    suspend fun getEnabledAlarmsForProfileAndDay(profileId: Long, dayOfWeek: Int): List<ShiftAlarm>

    @Query("SELECT * FROM shift_alarms WHERE profileId = :profileId")
    suspend fun getAlarmsForProfileList(profileId: Long): List<ShiftAlarm>

    @Query("SELECT * FROM shift_alarms WHERE profileId = :profileId AND dayOfWeek = :dayOfWeek")
    suspend fun getAlarmsForProfileAndDayList(profileId: Long, dayOfWeek: Int): List<ShiftAlarm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: ShiftAlarm): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<ShiftAlarm>)

    @Update
    suspend fun updateAlarm(alarm: ShiftAlarm)

    @Delete
    suspend fun deleteAlarm(alarm: ShiftAlarm)

    @Query("DELETE FROM shift_alarms WHERE profileId = :profileId AND dayOfWeek = :dayOfWeek")
    suspend fun deleteAlarmsForProfileAndDay(profileId: Long, dayOfWeek: Int)
}

@Dao
interface AiQueryAlarmDao {
    @Query("SELECT * FROM ai_query_alarms ORDER BY hour ASC, minute ASC")
    fun getAllAiAlarms(): Flow<List<AiQueryAlarm>>

    @Query("SELECT * FROM ai_query_alarms WHERE isEnabled = 1")
    suspend fun getEnabledAiAlarms(): List<AiQueryAlarm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAlarm(alarm: AiQueryAlarm): Long

    @Update
    suspend fun updateAiAlarm(alarm: AiQueryAlarm)

    @Delete
    suspend fun deleteAiAlarm(alarm: AiQueryAlarm)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSetting?

    @Query("SELECT * FROM app_settings")
    fun observeAllSettings(): Flow<List<AppSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)
}

@Database(
    entities = [ShiftProfile::class, ShiftAlarm::class, AiQueryAlarm::class, AppSetting::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftProfileDao(): ShiftProfileDao
    abstract fun shiftAlarmDao(): ShiftAlarmDao
    abstract fun aiQueryAlarmDao(): AiQueryAlarmDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shift_work_alarm_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate with default shift profiles in a background thread
                        INSTANCE?.let { appDb ->
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDefaultData(appDb)
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateDefaultData(db: AppDatabase) {
            // 1. Create Default Profiles
            val p1Id = db.shiftProfileDao().insertProfile(
                ShiftProfile(name = "Ca Ngày", description = "Lịch làm việc ca ngày")
            )
            val p2Id = db.shiftProfileDao().insertProfile(
                ShiftProfile(name = "Ca Đêm", description = "Lịch làm việc ca đêm")
            )

            // 2. Populate "Ca Ngày" Alarms
            // Monday to Friday: 06:00 Thức dậy, 12:00 Nghỉ trưa, 17:30 Tan ca (optional)
            val dayAlarms = mutableListOf<ShiftAlarm>()
            for (day in 1..5) {
                dayAlarms.add(ShiftAlarm(profileId = p1Id, dayOfWeek = day, hour = 6, minute = 0, label = "Thức dậy 🌅"))
                dayAlarms.add(ShiftAlarm(profileId = p1Id, dayOfWeek = day, hour = 12, minute = 0, label = "Nghỉ trưa 🍱"))
                dayAlarms.add(ShiftAlarm(profileId = p1Id, dayOfWeek = day, hour = 17, minute = 30, label = "Tan ca 🚗"))
            }
            // Saturday: 08:00 Thức dậy
            dayAlarms.add(ShiftAlarm(profileId = p1Id, dayOfWeek = 6, hour = 8, minute = 0, label = "Thức dậy cuối tuần ☀️"))
            // Sunday: empty / disabled (Chủ nhật tắt)

            // Populate "Ca Đêm" Alarms
            // Monday to Friday: 17:00 Bắt đầu ca, 23:00 Nghỉ giữa ca, 05:00 Tan ca
            for (day in 1..5) {
                dayAlarms.add(ShiftAlarm(profileId = p2Id, dayOfWeek = day, hour = 17, minute = 0, label = "Chuẩn bị đi làm 🌆"))
                dayAlarms.add(ShiftAlarm(profileId = p2Id, dayOfWeek = day, hour = 23, minute = 0, label = "Ăn tối giữa ca 🍲"))
                dayAlarms.add(ShiftAlarm(profileId = p2Id, dayOfWeek = day, hour = 5, minute = 0, label = "Tan ca đêm 🌙"))
            }
            // Saturday: 15:00 Thức dậy / Bắt đầu ca sớm
            dayAlarms.add(ShiftAlarm(profileId = p2Id, dayOfWeek = 6, hour = 15, minute = 0, label = "Chuẩn bị làm Thứ 7 ☕"))

            db.shiftAlarmDao().insertAlarms(dayAlarms)

            // 3. Create Default AI Query Alarms
            db.aiQueryAlarmDao().insertAiAlarm(
                AiQueryAlarm(hour = 7, minute = 0, query = "thời tiết hôm nay tại Seoul, giá vàng Việt Nam, giá vàng thế giới, tỷ giá won vnd")
            )
            db.aiQueryAlarmDao().insertAiAlarm(
                AiQueryAlarm(hour = 17, minute = 30, query = "giá vàng trong nước hôm nay, tỷ giá usd vnd mới nhất")
            )
            db.aiQueryAlarmDao().insertAiAlarm(
                AiQueryAlarm(
                    hour = 8,
                    minute = 0,
                    query = "Hôm nay là Ngày nhận lương 💵! Hãy nhắc nhở tôi kiểm tra tài khoản, rồi tóm tắt ngắn tin tức sáng nay và giá vàng hiện tại.",
                    repeatType = "MONTHLY",
                    repeatDayOfMonth = 15
                )
            )
            db.aiQueryAlarmDao().insertAiAlarm(
                AiQueryAlarm(
                    hour = 9,
                    minute = 0,
                    query = "Hôm nay đến hạn thanh toán tiền hóa đơn điện thoại, điện nước, gas ⚡! Hãy nhắc nhở tôi đóng tiền và tóm tắt nhanh tin tức hot.",
                    repeatType = "MONTHLY",
                    repeatDayOfMonth = 25
                )
            )
            db.aiQueryAlarmDao().insertAiAlarm(
                AiQueryAlarm(
                    hour = 7,
                    minute = 30,
                    query = "Chúc mừng sinh nhật tôi 🎂! Hãy tạo một lời chúc ngọt ngào, ấm áp, nhiều năng lượng tích cực rồi tóm tắt thời tiết hôm nay nhé.",
                    repeatType = "YEARLY",
                    repeatDayOfMonth = 21,
                    repeatMonthOfYear = 6
                )
            )

            // 4. Default Settings
            db.appSettingDao().insertSetting(AppSetting("active_profile_id", p1Id.toString()))
            db.appSettingDao().insertSetting(AppSetting("rotation_enabled", "true"))
            db.appSettingDao().insertSetting(AppSetting("cycle_start_date", System.currentTimeMillis().toString()))
            db.appSettingDao().insertSetting(AppSetting("rotation_sequence", "$p1Id,$p2Id"))
            db.appSettingDao().insertSetting(AppSetting("cycle_days", "7"))
        }
    }
}
