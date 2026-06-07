package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.*

class AssistantInterceptorService : AccessibilityService() {

    // Chiến thuật "Delayed Overlay Kill" - Hằng số tinh chỉnh nhịp độ
    private val DELAY_KILL_TIME = 700L    // Chờ App A trồi lên trên cùng hẳn rồi bắn BACK
    private val LOCK_COOLDOWN = 1500L     // Thời gian chặn Service nhận sự kiện mới để ổn định luồng

    private var isLaunching = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            
            // Ưu tiên lấy Package Name an toàn từ event.packageName hoặc rootInActiveWindow
            var resolvedPackage = event.packageName?.toString()

            // FALLBACK QUAN TRỌNG: Nếu event.packageName bị null, lấy từ rootInActiveWindow?.packageName
            if (resolvedPackage.isNullOrEmpty()) {
                try {
                    resolvedPackage = rootInActiveWindow?.packageName?.toString()
                } catch (e: Exception) {
                    Log.e("AssistantInterceptor", "Không thể truy xuất rootInActiveWindow: ${e.message}")
                }
            }

            if (resolvedPackage.isNullOrEmpty()) return

            // Lọc bỏ chính ứng dụng này để tránh can thiệp tự lặp
            if (resolvedPackage == packageName) {
                return
            }

            // Đọc cấu hình từ SharedPreferences
            val prefs = getSharedPreferences("InterceptorPrefs", Context.MODE_PRIVATE)
            
            // App B mở thế thân (ví dụ: Google Gemini)
            val launchPackage = prefs.getString("launchAppPackage", "com.google.android.apps.bard")
                ?: "com.google.android.apps.bard"

            // Nếu là App B đang mở, hoặc đang trong luồng khởi chạy, tránh xử lý tiếp
            if (resolvedPackage == launchPackage || isLaunching) {
                return
            }

            // App A cần chặn (ví dụ: BlueLM / Vivo Agent)
            val blockedPackage = prefs.getString("blockedAppPackage", null)
                ?: prefs.getString("blocked_package_name", "com.vivo.agent")
                ?: "com.vivo.agent"

            if (resolvedPackage == blockedPackage) {
                // BƯỚC 1: Khóa luồng ngay lập tức để chống lặp
                isLaunching = true
                Log.d("AssistantInterceptor", "Kích hoạt Delayed Overlay Kill đối với $resolvedPackage")

                // BƯỚC 2: Gọi App B (thế thân) ngay lập tức không trì hoãn
                launchTargetApp(launchPackage)

                // BƯỚC 3: Đợi App A trồi lên xong thì bắn BACK hạ gục nó xuống, hiển thị App B đã load bên dưới
                serviceScope.launch {
                    try {
                        // Chờ App A chạy hoàn tất hiệu ứng mở và nằm Foreground
                        delay(DELAY_KILL_TIME)
                        
                        // Tiêu diệt App A bằng cách gửi phím BACK
                        Log.d("AssistantInterceptor", "Sử dụng phím BACK để đè bẹp $resolvedPackage")
                        performGlobalAction(GLOBAL_ACTION_BACK)

                        // Cooldown an toàn chống lặp lại sự kiện trong quá trình chuyển trạng thái
                        delay(LOCK_COOLDOWN)
                    } catch (e: Exception) {
                        Log.e("AssistantInterceptor", "Lỗi trong Coroutine Delayed Overlay Kill: ${e.message}")
                    } finally {
                        isLaunching = false
                        Log.d("AssistantInterceptor", "Đã mở khóa khóa trùng lặp (isLaunching = false)")
                    }
                }
            }
        }
    }

    private fun launchTargetApp(packageName: String) {
        try {
            var launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                // Sử dụng cờ tối ưu tuyệt đối NEW_TASK và SINGLE_TOP để tránh việc mở lại trùng lặp Activity mới
                // Đảm bảo App B được tải sẵn ổn định ở phía dưới
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(launchIntent)
            } else {
                // Intent dự phòng
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
            Toast.makeText(this, "Đã kích hoạt ứng dụng thế thân! 🚀", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AssistantInterceptor", "Không thể khởi chạy app $packageName: ${e.message}")
            Toast.makeText(
                this,
                "Lỗi: Không thể mở ứng dụng thay thế ($packageName)!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onInterrupt() {
        Log.d("AssistantInterceptor", "Dịch vụ đã bị ngắt quãng (Interrupt)")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AssistantInterceptor", "Dịch vụ Trợ Năng chặn trợ lý ảo đã kết nối thành công")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d("AssistantInterceptor", "Dịch vụ đã bị hủy và hủy toàn bộ Coroutines đang chạy.")
    }
}
