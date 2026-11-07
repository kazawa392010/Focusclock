# Focus Clock 1.0.0 - báo cáo kiểm tra mã nguồn

Ngày kiểm tra: 07/11/2026

## Đã kiểm tra trong môi trường tạo project

- Parse thành công toàn bộ XML trong `app/src/main`.
- Biên dịch thành công các phần Kotlin thuần không phụ thuộc Android SDK: `Models.kt` và `TimeUtils.kt`.
- Kiểm tra cú pháp shell của script bootstrap `gradlew`.
- SQLite schema tạo thành công; các truy vấn chính cho lịch, nhật ký và weighted roadmap progress đã được chạy thử bằng SQLite.
- Kiểm tra lịch mặc định: 33 mẫu lịch, không phát hiện xung đột giữa các mẫu đang bật. Ca học 04:30 ngày thường được seed ở trạng thái tắt.
- Kiểm tra cấu trúc backup: danh sách bảng/cột export khớp schema 13 bảng.
- Quyền trong manifest chỉ gồm `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` và `SCHEDULE_EXACT_ALARM`; không khai báo `INTERNET`, microphone, camera, location hoặc contacts.
- Luồng restore đã được bảo vệ để kiểm tra đủ các bảng trước khi xóa dữ liệu hiện tại.

## Chưa thể kiểm tra

- Không có Android SDK Platform / `android.jar` trong môi trường này.
- Không có Gradle cài sẵn; script bootstrap cần tải Gradle nhưng môi trường hiện tại không phân giải được host tải xuống.
- Vì vậy chưa chạy được `:app:assembleDebug`, chưa có APK được tạo và chưa thể xác nhận toàn bộ Android/Kotlin source compile.
- Chưa cài/chạy trên Samsung Galaxy A20s hoặc thiết bị Android thật; notification, Doze, exact alarm, reboot, UI và pin cần kiểm thử theo `TESTING.md`.

**Kết luận:** đây là project source đã qua kiểm tra cấu trúc và một số kiểm thử schema/logic, không phải APK đã build hoặc bản phát hành đã kiểm thử trên thiết bị. Không được xem các mục chưa kiểm tra là đã đạt.
