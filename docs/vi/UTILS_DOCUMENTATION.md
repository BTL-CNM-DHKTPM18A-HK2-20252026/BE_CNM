# Tài Liệu Package Utils

> 📦 **Package**: `iuh.fit.utils`  
> 📅 **Tạo**: Tháng 1/2026  
> 🎯 **Mục đích**: Các phương thức tiện ích chung cho Ứng Dụng Fruvia Chat

---

## 📚 Mục Lục
1. [DateTimeUtils](#datetimeutils)
2. [JwtUtils](#jwtutils)
3. [ValidationUtils](#validationutils)
4. [MessageUtils](#messageutils)
5. [FileUtils](#fileutils)

---

## 1. DateTimeUtils

### Mục Đích
Format timestamp và ngày tháng cho giao diện chat theo phong cách tiếng Việt.

### Các Phương Thức Chính

#### `formatChatTimestamp(LocalDateTime timestamp)`
Format timestamp dựa trên độ gần:
- **Hôm nay**: `"14:30"`
- **Hôm qua**: `"Hôm qua 14:30"`
- **Tuần này**: `"Thứ 2 14:30"`
- **Cũ hơn**: `"15/01/2026 14:30"`

**Ví dụ sử dụng:**
```java
LocalDateTime timestamp = message.getCreatedAt();
String formatted = DateTimeUtils.formatChatTimestamp(timestamp);
// Output: "14:30" hoặc "Hôm qua 14:30"
```

#### `formatLastSeen(LocalDateTime lastSeen)`
Hiển thị thời gian hoạt động cuối của user:
- **< 1 phút**: `"Vừa xong"`
- **< 1 giờ**: `"5 phút trước"`
- **< 24 giờ**: `"2 giờ trước"`
- **> 24 giờ**: `"3 ngày trước"`

**Ví dụ sử dụng:**
```java
String lastSeen = DateTimeUtils.formatLastSeen(user.getLastActiveAt());
// Output: "Vừa xong" hoặc "5 phút trước"
```

---

## 2. JwtUtils

### Mục Đích
Trích xuất thông tin user từ JWT tokens trong SecurityContext.

### Các Phương Thức Chính

#### `getCurrentUserId()`
Lấy ID của user đã xác thực từ JWT claim `sub`.

**Ví dụ sử dụng:**
```java
@PostMapping("/messages")
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    String userId = JwtUtils.getCurrentUserId();
    // Dùng userId để tạo tin nhắn
    return messageService.create(userId, request);
}
```

#### `getCurrentUsername()`
Lấy username từ JWT custom claim.

#### `hasRole(String role)`
Kiểm tra xem user hiện tại có role cụ thể không.

**Ví dụ sử dụng:**
```java
if (JwtUtils.hasRole("ADMIN")) {
    // Logic chỉ dành cho admin
}
```

#### `isAuthenticated()`
Xác minh user đã được xác thực chưa.

---

## 3. ValidationUtils

### Mục Đích
Validate đầu vào user, emails, số điện thoại, mật khẩu, và tin nhắn.

### Các Phương Thức Chính

#### `isValidEmail(String email)`
Validate format email sử dụng regex.

**Ví dụ sử dụng:**
```java
if (!ValidationUtils.isValidEmail(email)) {
    throw new InvalidInputException("Email không hợp lệ");
}
```

#### `isValidPhone(String phone)`
Validate số điện thoại Việt Nam:
- Formats: `0912345678`, `+84912345678`
- Đầu số hỗ trợ: 03, 05, 07, 08, 09

#### `isValidPassword(String password)`
Yêu cầu mật khẩu:
- Tối thiểu 8 ký tự
- Ít nhất 1 chữ cái
- Ít nhất 1 số

#### `sanitizeMessage(String message)`
Ngăn chặn tấn công XSS bằng cách escape HTML characters:
```java
String safe = ValidationUtils.sanitizeMessage(userInput);
// "<script>alert('xss')</script>" → "&lt;script&gt;alert('xss')&lt;/script&gt;"
```

#### `isValidMessageContent(String content)`
Validate độ dài tin nhắn (1-5000 ký tự).

---

## 4. MessageUtils

### Mục Đích
Xử lý và format tin nhắn chat.

### Các Phương Thức Chính

#### `generatePreview(String content)`
Rút gọn tin nhắn dài cho danh sách hội thoại:
```java
String preview = MessageUtils.generatePreview(longMessage);
// "Đây là một tin nhắn rất dài..." (tối đa 100 ký tự)
```

#### `generatePreviewWithType(String content, String messageType)`
Trả về preview theo loại tin nhắn:
- **IMAGE**: `"📷 Hình ảnh"`
- **FILE**: `"📎 Tệp đính kèm"`
- **VOICE**: `"🎤 Tin nhắn thoại"`
- **VIDEO**: `"🎥 Video"`

**Ví dụ sử dụng:**
```java
String preview = MessageUtils.generatePreviewWithType(null, "IMAGE");
// Output: "📷 Hình ảnh"
```

#### `containsUrl(String message)`
Kiểm tra xem tin nhắn có chứa URL HTTP/HTTPS không.

#### `containsMention(String message, String username)`
Phát hiện @username mentions trong tin nhắn.

---

## 5. FileUtils

### Mục Đích
Xử lý upload file, validation, và formatting.

### Các Phương Thức Chính

#### `isValidImage(MultipartFile file)`
Validate upload ảnh:
- **Loại cho phép**: JPEG, PNG, GIF, WebP
- **Kích thước tối đa**: 10MB

**Ví dụ sử dụng:**
```java
if (!FileUtils.isValidImage(file)) {
    throw new InvalidFileException("Chỉ chấp nhận file ảnh (JPEG, PNG, GIF, WebP)");
}
```

#### `isValidFile(MultipartFile file)`
Validate upload file chung:
- **Loại cho phép**: PDF, DOC, DOCX, XLS, XLSX, TXT, ZIP
- **Kích thước tối đa**: 50MB

#### `formatFileSize(long size)`
Chuyển bytes thành format dễ đọc:
```java
FileUtils.formatFileSize(1536000);
// Output: "1.46 MB"
```

#### `generateUniqueFilename(String originalFilename)`
Tạo tên file unique với timestamp:
```java
String uniqueName = FileUtils.generateUniqueFilename("avatar.jpg");
// Output: "avatar_1737468900000.jpg"
```

#### `getFileExtension(String filename)`
Trích xuất phần mở rộng file từ tên file.

---

## 🎯 Best Practices Sử Dụng

### 1. **Luôn validate trước khi xử lý**
```java
// ❌ Sai
String preview = message.getContent().substring(0, 100);

// ✅ Đúng
String preview = MessageUtils.generatePreview(message.getContent());
```

### 2. **Chỉ dùng JwtUtils trong Controllers/Services**
```java
// Trong Controller
@PostMapping("/profile")
public ResponseEntity<?> updateProfile(@RequestBody ProfileRequest request) {
    String userId = JwtUtils.getCurrentUserId();
    return userService.updateProfile(userId, request);
}
```

### 3. **Sanitize tất cả đầu vào user**
```java
String safeContent = ValidationUtils.sanitizeMessage(request.getContent());
message.setContent(safeContent);
```

### 4. **Validate files trước khi upload lên Cloudinary**
```java
if (!FileUtils.isValidImage(file) || !FileUtils.isValidImageSize(file)) {
    throw new InvalidFileException("File không hợp lệ");
}
```

---

## 🔧 Mở Rộng Utils

Để thêm phương thức utility mới:

1. Chọn class phù hợp dựa trên chức năng
2. Thêm JavaDoc comments
3. Bao gồm ví dụ sử dụng
4. Viết unit tests
5. Cập nhật tài liệu này

---

## 📝 Lưu Ý

- Tất cả utils đều **stateless** (không có biến instance)
- Tất cả methods đều **static** để dễ truy cập
- Utils không nên có **dependencies** vào Spring beans
- Giữ utils **tập trung** vào single responsibility

---

**Cập nhật lần cuối**: 21/01/2026  
**Người duy trì**: Fruvia Development Team
