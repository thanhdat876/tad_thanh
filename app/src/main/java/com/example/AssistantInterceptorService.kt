package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AssistantInterceptorService : AccessibilityService() {

    private var isLaunching = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Nếu đang trong quá trình chuyển hướng và khởi chạy, lập tức bỏ qua mọi sự kiện tiếp theo
        if (isLaunching) {
            return
        }

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            
            // Lấy Package Name ưu tiên từ event.packageName
            var openedPackage = event.packageName?.toString()

            // FALLBACK QUAN TRỌNG: Nếu event.packageName bị null, phải thử lấy từ rootInActiveWindow?.packageName
            if (openedPackage.isNullOrEmpty()) {
                openedPackage = rootInActiveWindow?.packageName?.toString()
            }

            if (openedPackage.isNullOrEmpty()) return

            // Đọc cấu hình từ SharedPreferences
            val prefs = getSharedPreferences("InterceptorPrefs", Context.MODE_PRIVATE)
            
            // App B sẽ mở thay thế
            val launchPackage = prefs.getString("launchAppPackage", "com.google.android.apps.bard")
                ?: "com.google.android.apps.bard"

            // Lọc chặn sớm (Early Exit): Nếu trùng với App B thế thân hoặc chính ứng dụng này, dừng ngay để tránh tự lặp
            if (openedPackage == launchPackage || openedPackage == packageName) {
                return
            }

            // App A cần chặn
            val blockedPackage = prefs.getString("blockedAppPackage", null)
                ?: prefs.getString("blocked_package_name", "com.vivo.agent")
                ?: "com.vivo.agent"

            if (openedPackage == blockedPackage) {
                // Khóa luồng khởi chạy ngay lập tức
                isLaunching = true
                Log.d("AssistantInterceptor", "Phát hiện ứng dụng cần chặn mở lên: $openedPackage -> Đang tiến hành thế chỗ bằng $launchPackage")

                // Gọi hàm khởi chạy App B
                launchTargetApp(launchPackage)

                // Cài đặt bẻ khóa isLaunching sau 2000ms (2 giây) đảm bảo hệ điều hành ổn định xong giao diện
                handler.postDelayed({
                    isLaunching = false
                }, 2000)
            }
        }
    }

    private fun launchTargetApp(packageName: String) {
        try {
            var launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                // Sử dụng chính xác cờ tối ưu tuyệt đối: FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(launchIntent)
            } else {
                // Intent dự phòng trong trường hợp không lấy được Launch Intent chuẩn
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
            Toast.makeText(this, "Đã mở ứng dụng thay thế tự động! 🚀", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AssistantInterceptor", "Không thể khởi chạy app $packageName: ${e.message}")
            Toast.makeText(
                this,
                "Lỗi: Không tìm thấy hoặc không thể mở ứng dụng thay thế ($packageName)!",
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
}
