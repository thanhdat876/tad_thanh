package com.example

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ĐÂY LÀ PHIÊN BẢN SỬ DỤNG GIAO DIỆN TRUYỀN THỐNG (XML-BASED VIEWS)
 * Nếu dự án của bạn sử dụng XML (AppCompatActivity), file này đã được nâng cấp đầy đủ để hoạt động song song.
 */
class MainActivity_XML : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvBlockedAppInfo: TextView
    private lateinit var tvLaunchAppInfo: TextView
    private lateinit var btnOpenSettings: Button
    private lateinit var btnSelectBlockedApp: Button
    private lateinit var btnSelectLaunchApp: Button

    private val sharedPrefs by lazy {
        getSharedPreferences("InterceptorPrefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvBlockedAppInfo = findViewById(R.id.tv_blocked_app_info)
        tvLaunchAppInfo = findViewById(R.id.tv_launch_app_info)
        btnOpenSettings = findViewById(R.id.btn_open_settings)
        btnSelectBlockedApp = findViewById(R.id.btn_select_blocked_app)
        btnSelectLaunchApp = findViewById(R.id.btn_select_launch_app)

        btnOpenSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Không thể mở cài đặt Trợ Năng", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectBlockedApp.setOnClickListener {
            showAppPickerDialog(isBlockedAppTarget = true)
        }

        btnSelectLaunchApp.setOnClickListener {
            showAppPickerDialog(isBlockedAppTarget = false)
        }

        updateAppConfigTextViews()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        if (isEnabled) {
            tvAccessibilityStatus.text = "Quyền Trợ Năng: Đã Bật"
            tvAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
        } else {
            tvAccessibilityStatus.text = "Quyền Trợ Năng: Chưa Bật"
            tvAccessibilityStatus.setTextColor(android.graphics.Color.parseColor("#BA1A1A"))
        }
    }

    private fun updateAppConfigTextViews() {
        val blockedPackage = sharedPrefs.getString("blockedAppPackage", null)
            ?: sharedPrefs.getString("blocked_package_name", "com.vivo.agent")
            ?: "com.vivo.agent"

        val launchPackage = sharedPrefs.getString("launchAppPackage", "com.google.android.apps.bard")
            ?: "com.google.android.apps.bard"

        tvBlockedAppInfo.text = "Đang chặn: ${getAppName(blockedPackage)}"
        tvLaunchAppInfo.text = "Sẽ bật: ${getAppName(launchPackage)}"
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
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
                else -> "Trợ lý ảo hệ thống"
            }
        }
    }

    private fun showAppPickerDialog(isBlockedAppTarget: Boolean) {
        // Tạo dialog thông báo đang load ứng dụng
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Vui lòng đợi")
            .setMessage("Đang đọc danh sách ứng dụng trong máy...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // Khởi chạy Coroutine để đọc danh sách app, đảm bảo mượt mà 100%
        lifecycleScope.launch {
            val appList = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val list = mutableListOf<AppInfo>()
                for (pkg in packages) {
                    val packageName = pkg.packageName
                    if (packageName == this@MainActivity_XML.packageName) continue

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

            loadingDialog.dismiss()

            // Tạo cửa sổ chọn app
            val dialogBuilder = AlertDialog.Builder(this@MainActivity_XML)
            dialogBuilder.setTitle(
                if (isBlockedAppTarget) "Chọn ứng dụng cần chặn (App A)" 
                else "Chọn ứng dụng thế chỗ muốn bật (App B)"
            )

            val recyclerView = RecyclerView(this@MainActivity_XML).apply {
                layoutManager = LinearLayoutManager(this@MainActivity_XML)
                setPadding(16, 24, 16, 24)
            }

            val pickerDialog = dialogBuilder.setView(recyclerView).create()

            val adapter = AppListAdapter(appList) { selectedApp ->
                val edit = sharedPrefs.edit()
                if (isBlockedAppTarget) {
                    edit.putString("blockedAppPackage", selectedApp.packageName)
                    edit.putString("blocked_package_name", selectedApp.packageName) // Backward compatibility
                } else {
                    edit.putString("launchAppPackage", selectedApp.packageName)
                }
                edit.apply()

                updateAppConfigTextViews()
                pickerDialog.dismiss()
                Toast.makeText(
                    this@MainActivity_XML, 
                    "Đã lưu: ${selectedApp.appName}", 
                    Toast.LENGTH_SHORT
                ).show()
            }

            recyclerView.adapter = adapter
            pickerDialog.show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, AssistantInterceptorService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
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
}
