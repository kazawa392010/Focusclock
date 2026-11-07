# Focus Clock 1.0.0

Ứng dụng Android native bằng Kotlin + XML Views, hướng tới Android 11 trở lên. Ứng dụng chạy offline, không đăng nhập, không dùng server/API key và không yêu cầu quyền nhạy cảm như microphone, camera, vị trí hay danh bạ.

## Môi trường build

- Android Studio bản có hỗ trợ Android Gradle Plugin 8.7.x.
- JDK 17 trở lên (JDK 21 cũng được).
- Android SDK Platform 35 và Build Tools 35.x.
- Gradle 8.9.
- `minSdk = 30`, `targetSdk = 35`.

Project có script bootstrap `gradlew` / `gradlew.bat`: lần đầu chạy sẽ tải Gradle 8.9 vào cache người dùng (cần Internet trên máy build). Sau đó chạy:

```bash
./gradlew :app:assembleDebug
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

Script bootstrap này tải và gọi Gradle trực tiếp, không phải `gradle-wrapper.jar` chuẩn. Android SDK Platform 35 vẫn cần được cài qua Android Studio / SDK Manager trước khi build.

**Lưu ý phân phối:** cấu hình `targetSdk = 35` hiện phù hợp cho bản cài trực tiếp và kiểm tra trên Galaxy A20s chạy Android 11. Theo yêu cầu Google Play có hiệu lực từ 31/08/2026, ứng dụng mới và bản cập nhật gửi lên Google Play phải target Android 16 / API 36 trở lên. Vì vậy không dùng cấu hình API 35 này để phát hành app mới trên Google Play; cần nâng compile/target SDK và kiểm thử lại hành vi Android 16 trước khi gửi store.

Hoặc mở thư mục `FocusClock` bằng Android Studio, cài SDK Platform 35, đồng bộ Gradle rồi chọn **Build > Build Bundle(s) / APK(s) > Build APK(s)**. APK debug dự kiến nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Chức năng có trong mã nguồn

- Dashboard đồng hồ 24 giờ, ngày tháng, nhiệm vụ hiện tại/kế tiếp, tiến độ trong ngày và thời gian Focus Timer.
- Lịch lặp theo thứ trong tuần, thêm/sửa/xóa nhiệm vụ, bật/tắt, chỉnh giờ, thời lượng, môn học và tùy chọn nhắc; chặn các ca mới bị trùng với lịch đang bật.
- Dữ liệu lịch mặc định theo brief: lịch trường, thời gian di chuyển, sinh hoạt, học buổi tối, thứ Bảy và Chủ Nhật.
- Ca Tiếng Anh 04:30–06:00 các ngày thường được tạo ở trạng thái tắt vì xung đột với giờ ngủ 22:00–05:00.
- AlarmManager, Notification Channel, action Hoàn thành / Hoãn 5 phút / Bỏ qua / Mở nhật ký, khôi phục alarm sau reboot và thay đổi thời gian/múi giờ.
- Exact alarm permission cho Android 12+, quyền POST_NOTIFICATIONS cho Android 13+, fallback alarm không chính xác khi chưa được cấp quyền.
- Nhật ký thủ công, lịch sử event tự động, Habit Tracker và Focus Timer với chu kỳ 25/5, 50/10 hoặc tùy chỉnh.
- Roadmap THPT, English, Technology và Secure AIoT Monitoring System; chỉnh trạng thái, độ tự tin và bằng chứng/ghi chú.
- Nhập điểm kiểm tra, thống kê tiến độ ngày/tuần và thống kê thời gian Focus theo môn.
- Dark/Light mode, màu nhấn, giờ ngủ/thức, quiet mode, mục tiêu tập trung.
- Backup/restore JSON qua Storage Access Framework và xuất nhật ký CSV.

## Những giới hạn đã biết trong bản 1.0.0

- Chưa có Android Home Screen Widget.
- Smart Rescheduler chưa tự đề xuất lịch bù tối ưu; người dùng có thể sửa lịch thủ công.
- Weekly Review đang được thể hiện qua thống kê 7 ngày, chưa có báo cáo tuần được lưu riêng.
- Lịch mặc định đã seed vào SQLite khi cài lần đầu; chức năng khôi phục riêng lịch mặc định mà không đụng tới nhật ký chưa được triển khai.
- Habit Tracker hiện có danh sách mặc định và check-in theo ngày; chưa có giao diện thêm/sửa/xóa thói quen.
- Chưa chạy được Android instrumentation/UI test hoặc xác nhận APK trên thiết bị thật trong môi trường tạo mã nguồn này.

## Cấp quyền nhắc nhở

1. Mở Focus Clock và cấp quyền thông báo khi Android yêu cầu.
2. Trong Cài đặt của app, cấp quyền báo thức/nhắc đúng giờ nếu muốn dùng exact alarm trên Android 12+.
3. Nếu không cấp exact alarm, app dùng `setAndAllowWhileIdle`; thời điểm thông báo có thể bị trễ do Android/Doze.
4. Samsung có thể áp dụng quản lý pin riêng. Cho phép Focus Clock hoạt động nền nếu máy trì hoãn thông báo.

## Dữ liệu và quyền riêng tư

Database SQLite nằm trong vùng dữ liệu riêng của ứng dụng. Không có quyền INTERNET trong manifest. Xuất/nhập dữ liệu dùng Storage Access Framework, chỉ truy cập tệp do người dùng tự chọn.
