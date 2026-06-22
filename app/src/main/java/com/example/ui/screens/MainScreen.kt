package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.api.GeminiClient
import com.example.data.AiQueryAlarm
import com.example.data.ShiftAlarm
import com.example.data.ShiftProfile
import com.example.viewmodel.ShiftAlarmViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ShiftAlarmViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe data safely
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val alarmsForProfile by viewModel.alarmsForSelectedProfile.collectAsStateWithLifecycle()
    val aiAlarms by viewModel.allAiAlarms.collectAsStateWithLifecycle()
    val settings by viewModel.settingsMap.collectAsStateWithLifecycle()

    // Local UI State
    var activeTab by remember { mutableStateOf(0) }
    var showAddProfileDialog by remember { mutableStateOf(false) }

    // Request permissions for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Chúng tôi cần quyền thông báo để chuông báo hoạt động đúng!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Báo thức Ca làm 🕰️",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("app_title")
                        )
                        val currentDate = remember {
                            val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("vi", "VN"))
                            sdf.format(Date())
                        }
                        Text(
                            text = "Hôm nay: $currentDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "✨ GEMINI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(if (activeTab == 0) Icons.Default.Notifications else Icons.Default.Notifications, contentDescription = "Báo thức") },
                    label = { Text("Báo thức", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Xoay ca") },
                    label = { Text("Hồ sơ Xoay ca", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Face, contentDescription = "AI Gemini") },
                    label = { Text("Trợ lý AI", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "tab_navigation"
            ) { targetTab ->
                when (targetTab) {
                    0 -> ShiftAlarmsTab(
                        viewModel = viewModel,
                        profiles = profiles,
                        selectedId = selectedProfileId,
                        alarms = alarmsForProfile,
                        onAddProfileClick = { showAddProfileDialog = true }
                    )
                    1 -> AutoRotationTab(
                        viewModel = viewModel,
                        profiles = profiles,
                        settings = settings
                    )
                    2 -> GeminiAssistantTab(
                        viewModel = viewModel,
                        aiAlarms = aiAlarms
                    )
                }
            }
        }
    }

    // Add Profile Dialog
    if (showAddProfileDialog) {
        var newProfileName by remember { mutableStateOf("") }
        var newProfileDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            title = { Text("Thêm lịch ca mới 📅") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Tên lịch ca (Ví dụ: Ca Chiều, Tăng Ca)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E293B)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_profile_name")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newProfileDesc,
                        onValueChange = { newProfileDesc = it },
                        label = { Text("Mô tả chi tiết (Tùy chọn)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E293B)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            viewModel.addProfile(newProfileName, newProfileDesc)
                            showAddProfileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_new_profile")
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

// ==========================================
// TAB 1: SHIFT ALARMS SCREEN
// ==========================================

@Composable
fun ShiftAlarmsTab(
    viewModel: ShiftAlarmViewModel,
    profiles: List<ShiftProfile>,
    selectedId: Long?,
    alarms: List<ShiftAlarm>,
    onAddProfileClick: () -> Unit
) {
    val context = LocalContext.current
    var showAddAlarmDialogForDay by remember { mutableStateOf<Int?>(null) } // holds dayOfWeek value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Active Profile Selector Pill Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFE2E8F0).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    val isSelected = profile.id == selectedId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { viewModel.selectProfileForEditing(profile.id) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val icon = if (profile.name.contains("Ngày")) "☀" else if (profile.name.contains("Đêm")) "☾" else "📅"
                            Text(
                                text = icon,
                                fontSize = 14.sp,
                                color = if (isSelected) Color(0xFFF97316) else Color(0xFF64748B),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            IconButton(
                onClick = onAddProfileClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("add_profile_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm lịch ca", tint = Color.White)
            }
        }

        val selectedProfile = profiles.find { it.id == selectedId }
        if (selectedProfile != null) {
            // Selected Profile Header Details Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lịch ca chỉnh sửa: ${selectedProfile.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (selectedProfile.description.isNotBlank()) {
                            Text(
                                text = selectedProfile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (profiles.size > 1) {
                        IconButton(
                            onClick = { viewModel.deleteProfile(selectedProfile) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_profile_button")
                        ) {
                            Icon(
                                Icons.Default.Delete, 
                                contentDescription = "Xóa lịch ca", 
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Bento Grid Module - Weekly Schedule block header with integrated copy monday action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch trình tuần của ca".uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFF64748B)
                )

                Button(
                    onClick = {
                        viewModel.copyMondayToWeekdays(selectedProfile.id)
                        Toast.makeText(context, "Đã sao chép báo thức Thứ 2 sang Thứ 3 -> Thứ 6 📑", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("copy_monday_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Share, 
                            contentDescription = "Copy", 
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Sao chép T2 ➡️ T3-T6", 
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Week days listing
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = (1..7).toList()) { weekday ->
                    WeekdayCard(
                        dayOfWeek = weekday,
                        alarms = alarms.filter { it.dayOfWeek == weekday },
                        onAddAlarmClick = { showAddAlarmDialogForDay = weekday },
                        onAlarmToggle = { alarm -> viewModel.updateShiftAlarm(alarm.copy(isEnabled = !alarm.isEnabled)) },
                        onAlarmDelete = { alarm -> viewModel.deleteShiftAlarm(alarm) },
                        onAlarmEdit = { alarm ->
                            showAndroidTimePicker(context, alarm.hour, alarm.minute) { hour, minute ->
                                viewModel.updateShiftAlarm(alarm.copy(hour = hour, minute = minute))
                            }
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    // Dialog for adding shift alarm
    if (showAddAlarmDialogForDay != null) {
        val targetDay = showAddAlarmDialogForDay!!
        var labelText by remember { mutableStateOf("") }
        var vibrateState by remember { mutableStateOf(true) }
        var alarmHour by remember { mutableStateOf(6) }
        var alarmMinute by remember { mutableStateOf(0) }

        var showTimeSet by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Instantly open time picker
            showAndroidTimePicker(context, alarmHour, alarmMinute) { h, m ->
                alarmHour = h
                alarmMinute = m
                showTimeSet = true
            }
        }

        if (showTimeSet) {
            AlertDialog(
                onDismissRequest = { showAddAlarmDialogForDay = null },
                title = { Text("Tạo Báo Thức Mới [${getVietnameseDayName(targetDay)}]") },
                text = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .clickable {
                                    showAndroidTimePicker(context, alarmHour, alarmMinute) { h, m ->
                                        alarmHour = h
                                        alarmMinute = m
                                    }
                                }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Chọn giờ")
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = String.format("Thời gian: %02d:%02d", alarmHour, alarmMinute),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            label = { Text("Nhãn báo thức (e.g. Đi làm, Ăn trưa, Thức dậy)") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E293B)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alarm_label_input"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = vibrateState,
                                onCheckedChange = { vibrateState = it },
                                modifier = Modifier.testTag("vibrate_checkbox")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chế độ rung điện thoại")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalLabel = labelText.ifBlank { "Báo thức" }
                            if (selectedId != null) {
                                viewModel.addShiftAlarm(
                                    profileId = selectedId,
                                    dayOfWeek = targetDay,
                                    hour = alarmHour,
                                    minute = alarmMinute,
                                    label = finalLabel
                                )
                            }
                            showAddAlarmDialogForDay = null
                        },
                        modifier = Modifier.testTag("save_alarm_button")
                    ) {
                        Text("Lưu Báo Thức")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAlarmDialogForDay = null }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

fun showAndroidTimePicker(context: Context, initialHour: Int, initialMinute: Int, onTimeSelected: (Int, Int) -> Unit) {
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onTimeSelected(hour, minute) },
        initialHour,
        initialMinute,
        true
    ).show()
}

@Composable
fun WeekdayCard(
    dayOfWeek: Int,
    alarms: List<ShiftAlarm>,
    onAddAlarmClick: () -> Unit,
    onAlarmToggle: (ShiftAlarm) -> Unit,
    onAlarmDelete: (ShiftAlarm) -> Unit,
    onAlarmEdit: (ShiftAlarm) -> Unit
) {
    val areAlarmsActive = alarms.any { it.isEnabled }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekday_card_$dayOfWeek"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (areAlarmsActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Custom Bento Day Badge Column (similar style to "flex-none w-10 text-center")
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (areAlarmsActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color(0xFFF1F5F9)
                    )
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = getVietnameseShortDayLabel(dayOfWeek),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (areAlarmsActive) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                )
                Text(
                    text = dayOfWeek.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    ),
                    color = if (areAlarmsActive) MaterialTheme.colorScheme.primary else Color(0xFF334155)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right content Column
            Column(modifier = Modifier.weight(1f)) {
                // Day Title and Header Action button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = getVietnameseDayName(dayOfWeek),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = if (areAlarmsActive) MaterialTheme.colorScheme.primary else Color(0xFF475569)
                    )

                    IconButton(
                        onClick = onAddAlarmClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("add_alarm_day_$dayOfWeek")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle, 
                            contentDescription = "Thêm báo thức",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (alarms.isEmpty()) {
                    Text(
                        text = "Không có báo thức (Tắt báo thức) 🔕",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        alarms.forEach { alarm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (alarm.isEnabled) Color(0xFFF8FAFC)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (alarm.isEnabled) Color(0xFFEEF2F6) else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Alarm Time Details
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onAlarmEdit(alarm) }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 18.sp
                                            ),
                                            color = if (alarm.isEnabled) Color(0xFF1E293B) else Color(0xFF94A3B8)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Sửa giờ",
                                            modifier = Modifier.size(11.dp),
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                    Text(
                                        text = alarm.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (alarm.isEnabled) Color(0xFF475569) else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Vibrate Icon Indicator
                                if (alarm.isEnabled && alarm.vibrate) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Chế độ rung",
                                        tint = BentoGreen,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp)
                                    )
                                }

                                // Switch
                                Switch(
                                    checked = alarm.isEnabled,
                                    onCheckedChange = { onAlarmToggle(alarm) },
                                    modifier = Modifier
                                        .scale(0.82f)
                                        .testTag("toggle_alarm_${alarm.id}")
                                )

                                Spacer(modifier = Modifier.width(2.dp))

                                // Delete Button
                                IconButton(
                                    onClick = { onAlarmDelete(alarm) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("delete_alarm_${alarm.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Xóa báo thức",
                                        tint = BentoAlertRed.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to get compact day labels
fun getVietnameseShortDayLabel(day: Int): String {
    return when (day) {
        1 -> "THỨ 2"
        2 -> "THỨ 3"
        3 -> "THỨ 4"
        4 -> "THỨ 5"
        5 -> "THỨ 6"
        6 -> "THỨ 7"
        7 -> "CN"
        else -> "THỨ 2"
    }
}


fun getVietnameseDayName(day: Int): String {
    return when (day) {
        1 -> "Thứ hai (Thứ 2) 🗓️"
        2 -> "Thứ ba (Thứ 3)"
        3 -> "Thứ tư (Thứ 4)"
        4 -> "Thứ năm (Thứ 5)"
        5 -> "Thứ sáu (Thứ 6)"
        6 -> "Thứ bảy (Thứ 7) 🏖️"
        7 -> "Chủ nhật (Chủ Nhật) ⛪"
        else -> "Thứ Hai"
    }
}

// ==========================================
// TAB 2: AUTOMATIC SHIFT ROTATION SCREEN
// ==========================================

@Composable
fun AutoRotationTab(
    viewModel: ShiftAlarmViewModel,
    profiles: List<ShiftProfile>,
    settings: Map<String, String>
) {
    val context = LocalContext.current
    val rotationEnabled = settings["rotation_enabled"]?.toBoolean() ?: false
    val cycleStartDateMillis = settings["cycle_start_date"]?.toLongOrNull() ?: System.currentTimeMillis()
    val cycleDaysVal = settings["cycle_days"]?.toIntOrNull() ?: 7
    val activeProfileId = settings["active_profile_id"]?.toLongOrNull() ?: (profiles.firstOrNull()?.id ?: 0L)

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Overview and Enable Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (rotationEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color(0xFFE2E8F0)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (rotationEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color(0xFFF1F5F9)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Quay ca",
                                tint = if (rotationEnabled) MaterialTheme.colorScheme.primary else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Kích hoạt Xoay Ca Tự Động",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (rotationEnabled) MaterialTheme.colorScheme.primary else Color(0xFF1E293B)
                        )
                    }

                    Switch(
                        checked = rotationEnabled,
                        onCheckedChange = { viewModel.saveSetting("rotation_enabled", it.toString()) },
                        modifier = Modifier.testTag("toggle_auto_rotation")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Khi bật, hệ thống tự động luân chuyển chu kỳ báo thức liên tục giữa Ca Ngày và Ca Đêm sau mỗi $cycleDaysVal ngày, lặp lại vĩnh viễn mà không cần thao tác bấm đổi thủ công.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
        }

        // Card 2: Configuration settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cấu hình",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cấu hình Tham số Chu kỳ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1E293B)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Date Picker field styled as custom grid block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = cycleStartDateMillis
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = Calendar.getInstance()
                                    selectedCal.set(year, month, dayOfMonth)
                                    viewModel.saveSetting(
                                        "cycle_start_date",
                                        selectedCal.timeInMillis.toString()
                                    )
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ngày xuất phát chu kỳ gốc", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = dateFormat.format(Date(cycleStartDateMillis)),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.DateRange, 
                        contentDescription = "Chọn ngày",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration field
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thời gian mỗi lịch ca",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "$cycleDaysVal ngày",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = cycleDaysVal.toFloat(),
                        onValueChange = { viewModel.saveSetting("cycle_days", it.toInt().toString()) },
                        valueRange = 1f..14f,
                        steps = 13,
                        modifier = Modifier.testTag("cycle_days_slider")
                    )
                }

                // Default starting manual profile selection (when rotation is disabled)
                if (!rotationEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Áp dụng thủ công ca hiện tại 👇",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        profiles.forEach { p ->
                            val isSelected = p.id == activeProfileId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else Color(0xFFF1F5F9)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.saveSetting("active_profile_id", p.id.toString()) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 3: Dynamic future list calendar (highly satisfying UX)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Lịch",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Dự kiến Lịch làm việc (14 ngày tới)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1E293B)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Generate active profiles lists
                val futureDates = remember(cycleStartDateMillis, cycleDaysVal, activeProfileId, rotationEnabled) {
                    val list = mutableListOf<Pair<String, String>>()
                    val sdf = SimpleDateFormat("EE dd/MM", Locale("vi", "VN"))
                    val nowCal = Calendar.getInstance()

                    for (dayIndex in 0..13) {
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.DAY_OF_YEAR, dayIndex)
                        val textDate = sdf.format(cal.time)

                        // Calculate profile ID mathematically
                        var activeName = "Không xác định"
                        if (!rotationEnabled) {
                            activeName = profiles.find { it.id == activeProfileId }?.name ?: "Mặc định"
                        } else {
                            // Find active profile
                            val targetMidnight = getMidnightMillisOfTime(cal.timeInMillis)
                            val startMidnight = getMidnightMillisOfTime(cycleStartDateMillis)
                            val diffDays = ((targetMidnight - startMidnight) / (24L * 60 * 60 * 1000)).toInt()

                            val rotationSequenceIds = settings["rotation_sequence"] ?: ""
                            val profileIdList = rotationSequenceIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                            if (profileIdList.isNotEmpty()) {
                                val adjustedDays = if (diffDays < 0) {
                                    val seqSize = profileIdList.size * cycleDaysVal
                                    val positiveDiff = (diffDays % seqSize) + seqSize
                                    positiveDiff
                                } else {
                                    diffDays
                                }
                                val rotIndex = (adjustedDays / cycleDaysVal) % profileIdList.size
                                val pId = profileIdList[rotIndex]
                                activeName = profiles.find { it.id == pId }?.name ?: "Mặc định"
                            } else {
                                activeName = profiles.firstOrNull()?.name ?: "Mặc định"
                            }
                        }

                        list.add(textDate to activeName)
                    }
                    list
                }

                // Grid mapping
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    futureDates.forEachIndexed { index, pair ->
                        val isToday = index == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                    else if (index % 2 == 0) Color(0xFFF8FAFC)
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "HÔM NAY", 
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp
                                            ), 
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = pair.first, 
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal), 
                                    color = if (isToday) Color(0xFF1E293B) else Color(0xFF475569)
                                )
                            }

                            val isNightShift = pair.second.contains("Đêm")
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isNightShift) Color(0xFFEEF2F6)
                                        else Color(0xFFFFF7ED)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isNightShift) "☾ " else "☀ ",
                                        fontSize = 11.sp,
                                        color = if (isNightShift) Color(0xFF3B82F6) else Color(0xFFEA580C)
                                    )
                                    Text(
                                        text = pair.second,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (isNightShift) Color(0xFF1E293B) else Color(0xFF9A3412)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getMidnightMillisOfTime(timeMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ==========================================
// TAB 3: GEMINI AI ASSISTANT CONFIG SCREEN
// ==========================================

@Composable
fun GeminiAssistantTab(
    viewModel: ShiftAlarmViewModel,
    aiAlarms: List<AiQueryAlarm>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showAddAiDialog by remember { mutableStateOf(false) }

    // Live AI test variables
    var testQueryText by remember { mutableStateOf("Giá vàng SJC hôm nay và thời tiết Yangsan") }
    var testResponseText by remember { mutableStateOf("") }
    var isTestLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Informational header styled with subtle indigo accents
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🤖", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Trợ lý Báo cáo Tự động AI",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lên lịch thời gian cố định. Hệ thống sẽ tự động tổng hợp thông tin thời tiết, tỷ giá, tin tức bạn quan tâm qua Gemini AI hoàn toàn tự động ngay khi báo thức kích hoạt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Section header and add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lịch Trình AI Hẹn Giờ 🗓️",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1E293B)
            )
            Button(
                onClick = { showAddAiDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.testTag("add_ai_alarm_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thêm trợ lý",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        // List of existing AI queries
        if (aiAlarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💤", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Chưa cài đặt trợ lý hẹn giờ AI nào.", 
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Nhấp vào nút để thiết lập lịch hoạt động tự động đầu tiên.", 
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                aiAlarms.forEach { alarm ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                        .clickable {
                                            showAndroidTimePicker(context, alarm.hour, alarm.minute) { h, m ->
                                                viewModel.updateAiAlarm(alarm.copy(hour = h, minute = m))
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit, 
                                        contentDescription = "Sửa giờ", 
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF94A3B8)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { viewModel.updateAiAlarm(alarm.copy(isEnabled = it)) },
                                        modifier = Modifier.scale(0.85f).testTag("toggle_ai_alarm_${alarm.id}")
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteAiAlarm(alarm) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFFEF2F2))
                                            .testTag("delete_ai_${alarm.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete, 
                                            contentDescription = "Delete", 
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Query prompt string editor
                            OutlinedTextField(
                                value = alarm.query,
                                onValueChange = { newVal -> viewModel.updateAiAlarm(alarm.copy(query = newVal)) },
                                label = { Text("Nội dung Gemini AI cần thu thập bản tin", style = MaterialTheme.typography.bodySmall) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Repeat options selection row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lặp lại:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF475569)
                                )

                                listOf("DAILY" to "Hằng ngày", "MONTHLY" to "Tháng", "YEARLY" to "Năm").forEach { (typeKey, label) ->
                                    val isSelected = alarm.repeatType == typeKey
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFF1F5F9))
                                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.updateAiAlarm(alarm.copy(repeatType = typeKey))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                                            )
                                        )
                                    }
                                }
                            }

                            if (alarm.repeatType == "MONTHLY" || alarm.repeatType == "YEARLY") {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (alarm.repeatType == "YEARLY") {
                                        Column {
                                            Text("Tháng lặp:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFF1F5F9))
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val nextMonth = if (alarm.repeatMonthOfYear <= 1) 12 else alarm.repeatMonthOfYear - 1
                                                        viewModel.updateAiAlarm(alarm.copy(repeatMonthOfYear = nextMonth))
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("-", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                                }
                                                Text(
                                                    text = "Tháng ${alarm.repeatMonthOfYear}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF1E293B),
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val nextMonth = if (alarm.repeatMonthOfYear >= 12) 1 else alarm.repeatMonthOfYear + 1
                                                        viewModel.updateAiAlarm(alarm.copy(repeatMonthOfYear = nextMonth))
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                                }
                                            }
                                        }
                                    }

                                    Column {
                                        Text("Ngày lặp:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF1F5F9))
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val nextDay = if (alarm.repeatDayOfMonth <= 1) 31 else alarm.repeatDayOfMonth - 1
                                                    viewModel.updateAiAlarm(alarm.copy(repeatDayOfMonth = nextDay))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("-", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                            }
                                            Text(
                                                text = "Ngày ${alarm.repeatDayOfMonth}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF1E293B),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val nextDay = if (alarm.repeatDayOfMonth >= 31) 1 else alarm.repeatDayOfMonth + 1
                                                    viewModel.updateAiAlarm(alarm.copy(repeatDayOfMonth = nextDay))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("+", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Card 2: Interactive Playground
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEEF2F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Thử nghiệm Nhanh AI Gemini Live",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Khảo sát kết nối API hoặc tinh chỉnh nội dung tra cứu",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = testQueryText,
                    onValueChange = { testQueryText = it },
                    label = { Text("Nhập bất kỳ câu hỏi/lệnh tra khảo nào...", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color(0xFF64748B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (testQueryText.isNotBlank()) {
                            isTestLoading = true
                            testResponseText = "Đang tra cứu dữ liệu thời tiết và tài chính từ Gemini AI..."
                            coroutineScope.launch {
                                val out = GeminiClient.queryGemini(testQueryText)
                                testResponseText = out
                                isTestLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_ai_test_button"),
                    enabled = !isTestLoading && testQueryText.isNotBlank(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isTestLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = "Gửi Trực Tiếp Lên Gemini AI",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }

                if (testResponseText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF3B82F6), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Kết quả tra cứu từ Gemini AI", 
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), 
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = testResponseText,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add AI Alarm Dialog
    if (showAddAiDialog) {
        var h by remember { mutableStateOf(7) }
        var m by remember { mutableStateOf(0) }
        var queryStr by remember { mutableStateOf("Thời tiết tại Yangsan, tỷ giá won sang vnd hôm nay") }
        var repeatType by remember { mutableStateOf("DAILY") }
        var repeatDayOfMonth by remember { mutableStateOf(1) }
        var repeatMonthOfYear by remember { mutableStateOf(1) }

        var readyToInput by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            showAndroidTimePicker(context, h, m) { finalH, finalM ->
                h = finalH
                m = finalM
                readyToInput = true
            }
        }

        if (readyToInput) {
            AlertDialog(
                onDismissRequest = { showAddAiDialog = false },
                title = { Text("Cài Lịch Hẹn Trợ Lý AI ⏰") },
                text = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .clickable {
                                    showAndroidTimePicker(context, h, m) { curH, curM ->
                                        h = curH
                                        m = curM
                                    }
                                }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Thời Gian: " + String.format("%02d:%02d", h, m),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = queryStr,
                            onValueChange = { queryStr = it },
                            label = { Text("Thông tin bạn muốn AI tự cập nhật và báo cho bạn") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1E293B)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_alarm_prompt_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Lặp lại:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("DAILY" to "Hằng ngày", "MONTHLY" to "Tháng", "YEARLY" to "Năm").forEach { (typeKey, label) ->
                                val isSelected = repeatType == typeKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFF1F5F9))
                                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                        .clickable {
                                            repeatType = typeKey
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                                        )
                                    )
                                }
                            }
                        }

                        if (repeatType == "MONTHLY" || repeatType == "YEARLY") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (repeatType == "YEARLY") {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Tháng lặp:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF1F5F9))
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    repeatMonthOfYear = if (repeatMonthOfYear <= 1) 12 else repeatMonthOfYear - 1
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("-", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                            }
                                            Text(
                                                text = "Tháng $repeatMonthOfYear",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF1E293B),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    repeatMonthOfYear = if (repeatMonthOfYear >= 12) 1 else repeatMonthOfYear + 1
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("+", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ngày lặp:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF1F5F9))
                                    ) {
                                        IconButton(
                                            onClick = {
                                                repeatDayOfMonth = if (repeatDayOfMonth <= 1) 31 else repeatDayOfMonth - 1
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                        }
                                        Text(
                                            text = "Ngày $repeatDayOfMonth",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = {
                                                repeatDayOfMonth = if (repeatDayOfMonth >= 31) 1 else repeatDayOfMonth + 1
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (queryStr.isNotBlank()) {
                                viewModel.addAiAlarm(h, m, queryStr, repeatType, repeatDayOfMonth, repeatMonthOfYear)
                            }
                            showAddAiDialog = false
                        },
                        modifier = Modifier.testTag("confirm_save_ai_alarm")
                    ) {
                        Text("Xác Nhận Lưu")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAiDialog = false }) {
                        Text("Bỏ qua")
                    }
                }
            )
        }
    }
}
