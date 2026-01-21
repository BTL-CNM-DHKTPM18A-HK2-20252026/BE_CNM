# Utils Package Documentation

> 📦 **Package**: `iuh.fit.utils`  
> 📅 **Created**: January 2026  
> 🎯 **Purpose**: Common utility methods for Fruvia Chat Application

---

## 📚 Table of Contents
1. [DateTimeUtils](#datetimeutils)
2. [JwtUtils](#jwtutils)
3. [ValidationUtils](#validationutils)
4. [MessageUtils](#messageutils)
5. [FileUtils](#fileutils)

---

## 1. DateTimeUtils

### Purpose
Format timestamps and dates for chat interface in Vietnamese style.

### Key Methods

#### `formatChatTimestamp(LocalDateTime timestamp)`
Formats timestamp based on recency:
- **Today**: `"14:30"`
- **Yesterday**: `"Hôm qua 14:30"`
- **This week**: `"Thứ 2 14:30"`
- **Older**: `"15/01/2026 14:30"`

**Usage Example:**
```java
LocalDateTime timestamp = message.getCreatedAt();
String formatted = DateTimeUtils.formatChatTimestamp(timestamp);
// Output: "14:30" hoặc "Hôm qua 14:30"
```

#### `formatLastSeen(LocalDateTime lastSeen)`
Shows user's last active time:
- **< 1 min**: `"Vừa xong"`
- **< 1 hour**: `"5 phút trước"`
- **< 24 hours**: `"2 giờ trước"`
- **> 24 hours**: `"3 ngày trước"`

**Usage Example:**
```java
String lastSeen = DateTimeUtils.formatLastSeen(user.getLastActiveAt());
// Output: "Vừa xong" hoặc "5 phút trước"
```

---

## 2. JwtUtils

### Purpose
Extract user information from JWT tokens in SecurityContext.

### Key Methods

#### `getCurrentUserId()`
Gets authenticated user's ID from JWT `sub` claim.

**Usage Example:**
```java
@PostMapping("/messages")
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    String userId = JwtUtils.getCurrentUserId();
    // Use userId to create message
    return messageService.create(userId, request);
}
```

#### `getCurrentUsername()`
Gets username from JWT custom claim.

#### `hasRole(String role)`
Checks if current user has specific role.

**Usage Example:**
```java
if (JwtUtils.hasRole("ADMIN")) {
    // Admin-only logic
}
```

#### `isAuthenticated()`
Verifies if user is authenticated.

---

## 3. ValidationUtils

### Purpose
Validate user inputs, emails, phones, passwords, and messages.

### Key Methods

#### `isValidEmail(String email)`
Validates email format using regex.

**Usage Example:**
```java
if (!ValidationUtils.isValidEmail(email)) {
    throw new InvalidInputException("Email không hợp lệ");
}
```

#### `isValidPhone(String phone)`
Validates Vietnamese phone numbers:
- Formats: `0912345678`, `+84912345678`
- Supported prefixes: 03, 05, 07, 08, 09

#### `isValidPassword(String password)`
Password requirements:
- Minimum 8 characters
- At least 1 letter
- At least 1 number

#### `sanitizeMessage(String message)`
Prevents XSS attacks by escaping HTML characters:
```java
String safe = ValidationUtils.sanitizeMessage(userInput);
// "<script>alert('xss')</script>" → "&lt;script&gt;alert('xss')&lt;/script&gt;"
```

#### `isValidMessageContent(String content)`
Validates message length (1-5000 characters).

---

## 4. MessageUtils

### Purpose
Process and format chat messages.

### Key Methods

#### `generatePreview(String content)`
Truncates long messages for conversation list:
```java
String preview = MessageUtils.generatePreview(longMessage);
// "This is a very long message..." (max 100 chars)
```

#### `generatePreviewWithType(String content, String messageType)`
Returns type-specific previews:
- **IMAGE**: `"📷 Hình ảnh"`
- **FILE**: `"📎 Tệp đính kèm"`
- **VOICE**: `"🎤 Tin nhắn thoại"`
- **VIDEO**: `"🎥 Video"`

**Usage Example:**
```java
String preview = MessageUtils.generatePreviewWithType(null, "IMAGE");
// Output: "📷 Hình ảnh"
```

#### `containsUrl(String message)`
Checks if message contains HTTP/HTTPS URLs.

#### `containsMention(String message, String username)`
Detects @username mentions in messages.

---

## 5. FileUtils

### Purpose
Handle file uploads, validation, and formatting.

### Key Methods

#### `isValidImage(MultipartFile file)`
Validates image uploads:
- **Allowed types**: JPEG, PNG, GIF, WebP
- **Max size**: 10MB

**Usage Example:**
```java
if (!FileUtils.isValidImage(file)) {
    throw new InvalidFileException("Chỉ chấp nhận file ảnh (JPEG, PNG, GIF, WebP)");
}
```

#### `isValidFile(MultipartFile file)`
Validates general file uploads:
- **Allowed types**: PDF, DOC, DOCX, XLS, XLSX, TXT, ZIP
- **Max size**: 50MB

#### `formatFileSize(long size)`
Converts bytes to human-readable format:
```java
FileUtils.formatFileSize(1536000);
// Output: "1.46 MB"
```

#### `generateUniqueFilename(String originalFilename)`
Creates unique filename with timestamp:
```java
String uniqueName = FileUtils.generateUniqueFilename("avatar.jpg");
// Output: "avatar_1737468900000.jpg"
```

#### `getFileExtension(String filename)`
Extracts file extension from filename.

---

## 🎯 Usage Best Practices

### 1. **Always validate before processing**
```java
// BAD
String preview = message.getContent().substring(0, 100);

// GOOD
String preview = MessageUtils.generatePreview(message.getContent());
```

### 2. **Use JwtUtils in Controllers/Services only**
```java
// In Controller
@PostMapping("/profile")
public ResponseEntity<?> updateProfile(@RequestBody ProfileRequest request) {
    String userId = JwtUtils.getCurrentUserId();
    return userService.updateProfile(userId, request);
}
```

### 3. **Sanitize all user inputs**
```java
String safeContent = ValidationUtils.sanitizeMessage(request.getContent());
message.setContent(safeContent);
```

### 4. **Validate files before uploading to Cloudinary**
```java
if (!FileUtils.isValidImage(file) || !FileUtils.isValidImageSize(file)) {
    throw new InvalidFileException("File không hợp lệ");
}
```

---

## 🔧 Extending Utils

To add new utility methods:

1. Choose appropriate class based on functionality
2. Add JavaDoc comments
3. Include usage examples
4. Write unit tests
5. Update this documentation

---

## 📝 Notes

- All utils are **stateless** (no instance variables)
- All methods are **static** for easy access
- Utils should have **NO dependencies** on Spring beans
- Keep utils **focused** on single responsibility

---

**Last Updated**: January 21, 2026  
**Maintainer**: Fruvia Development Team
