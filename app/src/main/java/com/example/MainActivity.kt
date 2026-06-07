package com.example

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val sharedPrefs by lazy {
        getSharedPreferences("InterceptorPrefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    sharedPrefs = sharedPrefs,
                    onOpenAccessibilitySettings = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Không thể mở cài đặt Trợ Năng", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedPrefs: SharedPreferences,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    
    // Đọc Package A (cần chặn) và Package B (muốn mở)
    var blockedPackage by remember {
        mutableStateOf(
            sharedPrefs.getString("blockedAppPackage", null) 
                ?: sharedPrefs.getString("blocked_package_name", "com.vivo.agent") 
                ?: "com.vivo.agent"
        )
    }
    var launchPackage by remember {
        mutableStateOf(
            sharedPrefs.getString("launchAppPackage", "com.google.android.apps.bard") 
                ?: "com.google.android.apps.bard"
        )
    }

    // Biến điều khiển dialog chọn app dán nhãn luồng
    var targetPickerType by remember { mutableStateOf<String?>(null) } // "blocked" hoặc "launch"
    var isLoadingApps by remember { mutableStateOf(false) }
    var appList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // Đồng bộ tình trạng trợ năng mỗi khi người dùng quay về từ cài đặt hệ thống
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Hàm phân tích tên hiển thị của package bất kỳ trên máy
    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            when (packageName) {
                "com.vivo.agent" -> "Trợ lý Jovi (Vivo/iQOO)"
                "com.vivo.globalsearch" -> "Tìm kiếm toàn cầu Jovi"
                "com.coloros.assistantscreen" -> "Breeno Assistant (OPPO)"
                "com.heytap.speechassist" -> "Breeno Voice (OPPO/Realme)"
                "com.miui.voiceassist" -> "Mi AI Voice Assistant (Xiaomi)"
                "com.google.android.apps.bard" -> "Google Gemini (Google)"
                else -> "Trợ lý ảo hệ thống (Chưa phát hiện)"
            }
        }
    }

    // Khởi động load app dạng bất đồng bộ ở background
    fun triggerAppLoading(pickerType: String) {
        targetPickerType = pickerType
        if (appList.isNotEmpty()) {
            return
        }
        isLoadingApps = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val list = mutableListOf<AppInfo>()
                for (pkg in packages) {
                    val packageName = pkg.packageName
                    if (packageName == context.packageName) continue // Không lấy chính ứng dụng này
                    
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null) {
                        val appName = appInfo.loadLabel(pm).toString()
                        val icon = try {
                            appInfo.loadIcon(pm)
                        } catch (e: Exception) {
                            null
                        }
                        list.add(AppInfo(appName, packageName, icon))
                    }
                }
                list.sortedBy { it.appName.lowercase() }
            }
            appList = loaded
            isLoadingApps = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Assistant Interceptor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hệ thống chuyển đổi trợ lý ảo",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // CARD 1: Trạng thái quyền Trợ năng (Accessibility Service)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessibilityEnabled) Color(0xFFE8F5E9) else Color(0xFFFFDAD6)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isAccessibilityEnabled) Color(0xFFC8E6C9) else Color(0xFFFFB4AB)
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TRẠNG THÁI HỆ THỐNG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAccessibilityEnabled) Color(0xFF2E7D32) else Color(0xFF410002),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isAccessibilityEnabled) "Quyền Trợ Năng: Đã Bật" else "Quyền Trợ Năng: Chưa Bật",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isAccessibilityEnabled) Color(0xFF0B2F10) else Color(0xFF410002)
                            )
                            Text(
                                text = if (isAccessibilityEnabled) 
                                    "Người dùng đã cấp quyền. Trợ lý ảo thay thế hoạt động tự động!" 
                                    else "Ứng dụng cần quyền trợ năng này để phát hiện khi phím trợ lý hệ thống khởi chạy.",
                                fontSize = 13.sp,
                                color = if (isAccessibilityEnabled) Color(0xFF1B5E20) else Color(0xFF7A1C1D),
                                lineHeight = 18.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (isAccessibilityEnabled) Color(0xFFC8E6C9).copy(alpha = 0.4f) else Color(0xFFFFFFFF).copy(alpha = 0.4f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAccessibilityEnabled) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isAccessibilityEnabled) Color(0xFF1B5E20) else Color(0xFF410002),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFBA1A1A),
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mở Cài Đặt Trợ Năng",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // CARD 2: Cấu hình ứng dụng chặn (App A) & ứng dụng khởi chạy (App B)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "CẤU HÌNH LIÊN KẾT ỨNG DỤNG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )

                    // CỤM 1: Ứng dụng cần chặn (App A)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bước 1: Ứng dụng gốc cần chặn (App A)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                var appIcon: android.graphics.drawable.Drawable? by remember(blockedPackage) {
                                    mutableStateOf(null)
                                }
                                LaunchedEffect(blockedPackage) {
                                    try {
                                        val pm = context.packageManager
                                        val pInfo = pm.getApplicationInfo(blockedPackage, 0)
                                        appIcon = pInfo.loadIcon(pm)
                                    } catch (e: Exception) {
                                        appIcon = null
                                    }
                                }
                                AppIconImage(
                                    drawable = appIcon,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Đang chặn: " + getAppName(blockedPackage),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = blockedPackage,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { triggerAppLoading("blocked") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chọn App Cần Chặn", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                    // CỤM 2: Ứng dụng thay thế muốn bật (App B)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bước 2: Ứng dụng thay thế muốn bật (App B)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                var appIcon: android.graphics.drawable.Drawable? by remember(launchPackage) {
                                    mutableStateOf(null)
                                }
                                LaunchedEffect(launchPackage) {
                                    try {
                                        val pm = context.packageManager
                                        val pInfo = pm.getApplicationInfo(launchPackage, 0)
                                        appIcon = pInfo.loadIcon(pm)
                                    } catch (e: Exception) {
                                        appIcon = null
                                    }
                                }
                                AppIconImage(
                                    drawable = appIcon,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sẽ bật: " + getAppName(launchPackage),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = launchPackage,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { triggerAppLoading("launch") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chọn App Muốn Bật", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // CARD 3: Hướng dẫn quan trọng cho điện thoại Trung Quốc (Vivo/iQOO/Xiaomi/Oppo)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "LƯU Ý ĐẶC BIỆT CHO VIVO / iQOO / ROM TRUNG QUỐC",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Text(
                        text = "Do cơ chế tối ưu RAM và pin cực đoan trên ROM nội địa FuntouchOS / OriginOS, bạn CẦN THIẾT LẬP 4 cài đặt quan trọng sau để app hoạt động 100% mượt mà:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val steps = listOf(
                            "Bật tự khởi động (Auto-start) trong Cài đặt hệ thống để trợ năng tự động duy trì hoạt động ngay cả khi khởi động lại máy.",
                            "Tắt tối ưu hóa pin: Chọn Không hạn chế (Unrestricted) hoặc 'Cho phép hao phí điện năng ở mức cao' (High background power consumption) cho ứng dụng này.",
                            "Khóa app trong đa nhiệm: Nhấn nút đa nhiệm, giữ cửa sổ app này và bấm CHỌN KHÓA (Lock) để tránh bị dọn dẹp bộ nhớ RAM.",
                            "BẮT BUỘC: Vào Cài đặt ứng dụng -> Bật quyền 'Khởi chạy trong nền' (Start in background) hoặc 'Hiển thị Pop-up' để ứng dụng có quyền khởi gọi App B (như Gemini) tự động lên màn hình từ background."
                        )
                        
                        steps.forEachIndexed { index, instruction ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(color = Color.White, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1A1C1E)
                                    )
                                }
                                Text(
                                    text = instruction,
                                    fontSize = 12.8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // App Picker Pop-up Dialog chung cho cả App A và App B
    if (targetPickerType != null) {
        val pickerType = targetPickerType!!
        val isBlockedAppPicker = pickerType == "blocked"
        
        Dialog(
            onDismissRequest = { targetPickerType = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp, horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (isBlockedAppPicker) "Chọn ứng dụng cần chặn (App A)" else "Chọn ứng dụng muốn bật (App B)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var searchQuery by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm tên hoặc package (vd: gemini)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (isLoadingApps) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Đang chuẩn bị danh sách ứng dụng...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val filteredList = appList.filter {
                            it.appName.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Không tìm thấy ứng dụng phù hợp",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredList) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val editPref = sharedPrefs.edit()
                                                if (isBlockedAppPicker) {
                                                    editPref.putString("blockedAppPackage", app.packageName)
                                                    editPref.putString("blocked_package_name", app.packageName) // sync cho khâu tương thích ngược
                                                    blockedPackage = app.packageName
                                                } else {
                                                    editPref.putString("launchAppPackage", app.packageName)
                                                    launchPackage = app.packageName
                                                }
                                                editPref.apply()
                                                
                                                targetPickerType = null
                                                Toast.makeText(
                                                    context,
                                                    "Đã cấu hình lý tưởng: ${app.appName}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIconImage(
                                            drawable = app.appIcon,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.appName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = app.packageName,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { targetPickerType = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("HỦY BỎ")
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconImage(drawable: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            try {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
                drawable.toBitmap(width, height)
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier
            )
        } else {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
    }
}

fun checkAccessibilityPermission(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, AssistantInterceptorService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}
