# Focus Clock - kiểm thử trước khi dùng thật

## 1. Kiểm tra trên máy build

- [ ] Cài JDK 17 trở lên.
- [ ] Cài Android SDK Platform 35 và Build Tools 35.x bằng Android Studio SDK Manager.
- [ ] Chạy `.\gradlew.bat :app:assembleDebug` trong PowerShell (hoặc `./gradlew :app:assembleDebug` trên macOS/Linux).
- [ ] Cài `app/build/outputs/apk/debug/app-debug.apk` lên thiết bị Android 11 trở lên.
- [ ] Xác nhận ứng dụng mở được lần đầu và lịch mặc định chỉ seed một lần.

## 2. Lịch và xung đột

- [ ] Xác nhận ca 04:30-06:00 ngày thường đang tắt mặc định do xung đột giờ ngủ 22:00-05:00.
- [ ] Thử bật ca buổi sáng khi vẫn còn xung đột; ứng dụng phải từ chối.
- [ ] Đổi giờ ngủ để hết xung đột, sau đó bật ca buổi sáng.
- [ ] Tạo hai nhiệm vụ trùng giờ; ứng dụng phải từ chối lưu/bật nhiệm vụ xung đột.
- [ ] Thử sửa ngày lặp và xóa một nhiệm vụ; xác nhận alarm cũ không còn phát.

## 3. Notification

- [ ] Cấp quyền thông báo trên Android 13+.
- [ ] Trên Android 12+, cấp quyền exact alarm nếu muốn nhắc chính xác.
- [ ] Không cấp exact alarm và xác nhận ứng dụng giải thích rằng giờ thông báo có thể trễ.
- [ ] Tạo một nhiệm vụ thử trong vài phút tới, đóng ứng dụng và kiểm tra notification.
- [ ] Thử action Hoàn thành, Hoãn 5 phút, Bỏ qua và Mở nhật ký.
- [ ] Khởi động lại điện thoại và kiểm tra lịch nhắc được khôi phục.
- [ ] Thử thay đổi giờ hệ thống/múi giờ và kiểm tra alarm được lập lại.
- [ ] Xác nhận quiet mode không gửi nhắc trong giờ ngủ và giờ học đã cấu hình.

## 4. Dữ liệu và thống kê

- [ ] Đánh dấu nhiệm vụ hoàn thành, một phần, bỏ lỡ và pending.
- [ ] Xác nhận chỉ sự kiện do người dùng xác nhận mới được tính là đã hoàn thành.
- [ ] Thêm ghi chú nhật ký, check-in thói quen, Focus Session và điểm kiểm tra.
- [ ] Đối chiếu Daily Completion với công thức theo thời lượng.
- [ ] Đối chiếu Roadmap Completion theo trọng số milestone.
- [ ] Xuất JSON, xóa dữ liệu thử, nhập lại JSON và đối chiếu dữ liệu.
- [ ] Thử nhập JSON sai/thiếu bảng; xác nhận dữ liệu đang có không bị xóa.
- [ ] Xuất CSV và mở bằng trình đọc bảng tính.

## 5. Giao diện và độ ổn định

- [ ] Thử Dark Mode, Light Mode và từng màu nhấn.
- [ ] Thử Focus Timer 25/5, 50/10, tùy chỉnh, pause/resume và đóng app giữa phiên.
- [ ] Kiểm tra kích thước chữ và thao tác cuộn trên màn hình nhỏ.
- [ ] Kiểm tra dữ liệu còn nguyên sau khi force-stop và khởi động lại thiết bị.
- [ ] Kiểm tra app không cần kết nối Internet để sử dụng.

> Các mục trên là checklist để chạy trên thiết bị thật. Mã nguồn đã được rà cấu trúc và một số truy vấn/schema đã kiểm tra trong môi trường tạo project, nhưng chưa thể đánh dấu các mục Android device test là đạt khi chưa có APK được build và cài trên máy thật.
