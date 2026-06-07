# Assistant Interceptor

Một ứng dụng nhẹ nhàng, hiệu quả được thiết kế để thay thế trợ lý ảo mặc định bằng ứng dụng bạn yêu thích trên các điện thoại chạy hệ điều hành tùy biến (như OriginOS, FuntouchOS).

## Vấn đề
Nhiều thiết bị điện thoại nội địa (Trung Quốc) cài đặt sẵn các trợ lý ảo mặc định (như BlueLM) không thể thay đổi thông qua cài đặt hệ thống thông thường. Điều này gây khó khăn khi bạn muốn sử dụng các trợ lý quốc tế như Google Gemini hoặc ChatGPT.

## Giải pháp: Chiến thuật "Delayed Overlay Kill"
**Assistant Interceptor** sử dụng quyền Trợ năng (Accessibility Service) để can thiệp vào quy trình mở ứng dụng:

1. **Ghi đè tốc độ cao (Fresh Launch):** Ngay khi phát hiện trợ lý mặc định khởi chạy, ứng dụng lập tức phóng ứng dụng thay thế của bạn lên màn hình ngay lập tức mà không có độ trễ.
2. **Dọn dẹp bóng ma (Delayed Kill):** Tận dụng đặc tính hoạt ảnh của hệ thống, sau 700ms, ứng dụng sẽ thực hiện lệnh "Quay lại" (Back) để âm thầm đóng trợ lý mặc định, đảm bảo không có xung đột ứng dụng hay tốn tài nguyên chạy ngầm.

## Tính năng chính
* **Thay thế liền mạch:** Trải nghiệm chuyển đổi nhanh chóng, không gây giật lag.
* **Cơ chế chống lặp (Debounce):** Đảm bảo ứng dụng chỉ kích hoạt một lần, tránh lỗi vòng lặp hoặc bật đúp.
* **Tối ưu tài nguyên:** Sử dụng Kotlin Coroutines giúp ứng dụng hoạt động cực kỳ nhẹ nhàng, không hao pin.

## Yêu cầu thiết lập
Để ứng dụng hoạt động ổn định trên các dòng máy có cơ chế quản lý nền gắt gao (như Vivo/iQOO), hãy thực hiện:

1. **Cấp quyền Trợ năng:** Bật dịch vụ trong `Cài đặt > Trợ năng`.
2. **Cho phép tự khởi động (Auto-start):** Trong cài đặt quản lý ứng dụng.
3. **Tắt tối ưu hóa pin:** Đặt thành chế độ "Không hạn chế".
4. **Khóa trong đa nhiệm:** Khóa ứng dụng để tránh bị hệ thống đóng băng.

## Cấu hình
Bạn có thể dễ dàng điều chỉnh thời gian phản hồi trong mã nguồn (`AssistantInterceptorService.kt`):
* `DELAY_KILL_TIME = 700L`: Điều chỉnh thời gian chờ để dọn dẹp trợ lý mặc định (đơn vị: mili giây).

## Đóng góp
Dự án được tạo ra nhằm tối ưu hóa trải nghiệm người dùng cá nhân. Mọi đề xuất cải thiện về hiệu năng hoặc tính tương thích đều được hoan nghênh.

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/cc0bcb0f-913f-4bce-8805-5adca6c0eadc

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
