# Doc2ChatGPT

Ứng dụng Android trung gian cho workflow dịch/tái tạo ảnh tài liệu toán - vật lý bằng ChatGPT.

## Mục tiêu

- Nhập PDF lớn, có thể tới hàng nghìn trang.
- Không render toàn bộ PDF một lần; chỉ render từng trang khi người dùng bấm Share.
- Nhập trực tiếp một hoặc nhiều ảnh.
- Cho phép sửa prompt mặc định trong app.
- Share từng trang/ảnh sang ChatGPT bằng cơ chế Android Share Sheet.

## Build APK bằng Android Studio

1. Mở thư mục `Doc2ChatGPT` bằng Android Studio.
2. Đợi Gradle Sync hoàn tất.
3. Chọn menu:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

4. APK debug nằm tại:

```text
app/build/outputs/apk/debug/app-debug.apk
```

5. Copy APK sang điện thoại và cài đặt.

## Build APK bằng GitHub Actions

1. Tạo repository GitHub mới.
2. Upload toàn bộ thư mục project này.
3. Vào tab `Actions`.
4. Chạy workflow `Build Android APK`.
5. Tải artifact `Doc2ChatGPT-debug-apk`.

## Cách dùng app

1. Mở app.
2. Sửa prompt mặc định nếu cần.
3. Chọn `Chọn PDF` hoặc `Chọn ảnh`.
4. Với PDF: app hiện danh sách trang.
5. Bấm `Share` ở trang/ảnh cần gửi.
6. Chọn ChatGPT trong bảng chia sẻ Android.

## Giới hạn kỹ thuật quan trọng

- App ChatGPT có thể nhận ảnh qua Android Share Sheet.
- Việc ChatGPT có nhận đồng thời cả `ảnh + prompt text` hay không phụ thuộc vào app ChatGPT.
- Nếu ChatGPT chỉ nhận ảnh mà không nhận prompt, hãy copy prompt trong app rồi dán thủ công vào ChatGPT.

## Gợi ý việc tiếp theo cho Codex

Bạn có thể yêu cầu Codex làm tiếp các việc sau:

1. Thêm màn hình thumbnail giống CamScanner.
2. Thêm chọn nhiều trang PDF để share tuần tự.
3. Thêm lưu nhiều bộ prompt khác nhau.
4. Thêm OCR kiểm tra chữ Nga còn sót sau khi ChatGPT tạo ảnh.
5. Thêm xuất từng trang PDF thành ảnh hàng loạt theo thư mục.
6. Thêm chế độ crop/làm thẳng ảnh trước khi share.
