# VietTransGPT

[Tiếng Việt bên dưới]

An Android utility app designed to streamline the translation and recreation workflow for Math and Physics documents using ChatGPT.

## Key Features / Tính năng chính

- **Large Document Support:** Handles massive PDFs (thousands of pages) efficiently.
- **On-Demand Rendering:** Only renders individual pages as needed to save memory.
- **Multi-Source Import:** Import via PDF files or direct images from gallery.
- **Sequential Prompting System:** User-defined 3-slot prompt system (P1, P2, P3) that automatically populates the Android clipboard history for seamless copy-pasting within the ChatGPT app.
- **Adaptive UI:** Optimized for Foldables (Galaxy Z Fold, etc.) and Tablets with a dual-pane layout.

---

# Tiếng Việt

Ứng dụng Android hỗ trợ quy trình dịch và tái tạo ảnh tài liệu Toán - Vật lý thông qua ChatGPT.

## Mục tiêu

- **Hỗ trợ tài liệu lớn:** Xử lý các file PDF lên đến hàng nghìn trang một cách mượt mà.
- **Render theo yêu cầu:** Chỉ tạo ảnh khi người dùng nhấn Share để tiết kiệm tài nguyên máy.
- **Nhập dữ liệu đa dạng:** Hỗ trợ cả file PDF và chọn nhiều ảnh trực tiếp từ thư viện.
- **Hệ thống Prompt tuần tự:** Hệ thống 3 ô nhập prompt (P1, P2, P3) tự động nạp vào lịch sử Clipboard của Android, giúp người dùng dán lệnh vào ChatGPT cực nhanh mà không cần chuyển đổi qua lại giữa các ứng dụng.
- **Giao diện thích ứng:** Tối ưu riêng cho điện thoại màn hình gập (như Galaxy Z Fold) và máy tính bảng với giao diện hai cột.

## Build APK bằng Android Studio / Build Instructions

1. Mở thư mục project bằng Android Studio.
2. Đợi Gradle Sync hoàn tất.
3. Chọn menu:
   `Build > Build Bundle(s) / APK(s) > Build APK(s)`
4. APK debug nằm tại:
   `app/build/outputs/apk/debug/app-debug.apk`

## Cách dùng app / How to use

1. Nhập nội dung vào **Prompt 1, 2, 3**. Các nội dung này sẽ tự động được lưu.
2. Nhấn `Chọn PDF` hoặc `Chọn ảnh`.
3. Nhấn **Share** ở trang/ảnh cần xử lý.
4. App sẽ tự động nạp cả 3 Prompt vào lịch sử Clipboard và mở bảng chia sẻ Android.
5. Chọn ứng dụng **ChatGPT**.
6. Gửi ảnh kèm Prompt 1. Sau khi ChatGPT phản hồi, mở khay nhớ tạm (Clipboard history) trên bàn phím để chọn dán tiếp Prompt 2 và 3.

## Technical Notes / Lưu ý kỹ thuật

- App tận dụng cơ chế **Android Share Sheet** để gửi dữ liệu.
- Việc ChatGPT có nhận đồng thời cả `ảnh + prompt text` hay không phụ thuộc vào phiên bản ứng dụng ChatGPT trên máy bạn. Nếu không, hãy sử dụng tính năng dán từ lịch sử Clipboard đã được tích hợp sẵn trong app.

## License

This project is open-source. Feel free to contribute!
Dự án này là mã nguồn mở. Mọi sự đóng góp đều được chào đón!
