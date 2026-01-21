# Hệ Thống Xử Lý Exception - Fruvia Chat

> 📦 **Package**: `iuh.fit.exception`  
> 🎯 **Mục đích**: Xử lý lỗi tập trung với response format chuẩn  
> 🚀 **Trạng thái**: Production Ready

---

## 📋 Mục Lục
1. [Tổng Quan](#tổng-quan)
2. [Kiến Trúc](#kiến-trúc)
3. [Định Dạng Error Response](#định-dạng-error-response)
4. [Các Error Codes](#các-error-codes)
5. [Custom Exceptions](#custom-exceptions)
6. [Ví Dụ Sử Dụng](#ví-dụ-sử-dụng)
7. [Best Practices](#best-practices)
8. [Testing Exception Handling](#testing-exception-handling)

---

## 1. Tổng Quan

### Đây là gì?

Hệ thống xử lý exception toàn diện cung cấp:
- ✅ **Error responses chuẩn** trên tất cả API endpoints
- ✅ **40+ error codes định nghĩa sẵn** cho các tình huống thường gặp
- ✅ **Type-safe exceptions** với helper methods
- ✅ **Tự động xử lý lỗi** qua `@RestControllerAdvice`
- ✅ **Error format nhất quán** để frontend dễ tích hợp

### Tại sao cần?

**❌ Không Có Exception Handling:**
```json
// Responses không nhất quán
{
  "error": "User not found"
}

// Hoặc đôi khi:
{
  "message": "Cannot find user"
}

// Hoặc thậm chí:
"Internal Server Error"
```

**✅ Với Exception Handling:**
```json
// Luôn format nhất quán
{
  "status": 404,
  "errorCode": "USER_NOT_FOUND",
  "message": "Không tìm thấy người dùng với ID: user123",
  "timestamp": "2026-01-21T14:30:00",
  "path": "/api/v1/users/user123"
}
```

---

## 2. Kiến Trúc

### Cấu Trúc Files

```
iuh/fit/exception/
├── ErrorCode.java                    # Enum chứa 40+ error codes
├── ErrorResponse.java                # Response format chuẩn
├── AppException.java                 # Base custom exception
├── ResourceNotFoundException.java    # 404 Not Found
├── InvalidInputException.java        # 400 Bad Request
├── UnauthorizedException.java        # 401 Unauthorized
├── ForbiddenException.java           # 403 Forbidden
└── GlobalExceptionHandler.java       # @RestControllerAdvice
```

### Sơ Đồ Luồng

```
Controller/Service ném Exception
        ↓
GlobalExceptionHandler bắt lỗi
        ↓
Map thành ErrorResponse
        ↓
Trả JSON chuẩn về client
```

---

## 3. Định Dạng Error Response

### ErrorResponse.java

**Các Trường:**
- `status` (int): HTTP status code (400, 401, 404, 500...)
- `errorCode` (String): Mã lỗi cụ thể (USER_NOT_FOUND, INVALID_TOKEN...)
- `message` (String): Thông báo bằng tiếng Việt cho user
- `timestamp` (LocalDateTime): Thời điểm xảy ra lỗi
- `path` (String): API endpoint gây ra lỗi
- `details` (Object, optional): Context bổ sung (validation errors, etc.)

**Ví Dụ Response:**

```json
{
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Dữ liệu không hợp lệ",
  "timestamp": "2026-01-21T14:30:00",
  "path": "/api/v1/users",
  "details": {
    "email": "Email không hợp lệ",
    "password": "Mật khẩu phải có ít nhất 8 ký tự"
  }
}
```

---

## 4. Các Error Codes

### ErrorCode.java

Enum định nghĩa tất cả error codes với HTTP status và message tương ứng.

### Các Nhóm

#### 🔐 Authentication & Authorization
```java
INVALID_CREDENTIALS      // 401: Sai email/password
INVALID_TOKEN           // 401: Token không hợp lệ
TOKEN_EXPIRED           // 401: Token hết hạn
UNAUTHORIZED            // 401: Chưa đăng nhập
FORBIDDEN               // 403: Không có quyền
```

#### 👤 Lỗi User
```java
USER_NOT_FOUND          // 404: User không tồn tại
USER_ALREADY_EXISTS     // 409: Email đã được đăng ký
USER_INACTIVE           // 403: Tài khoản bị vô hiệu hóa
INVALID_USER_DATA       // 400: Dữ liệu user không hợp lệ
```

#### 💬 Lỗi Message
```java
MESSAGE_NOT_FOUND       // 404: Message không tồn tại
MESSAGE_EMPTY           // 400: Nội dung rỗng
MESSAGE_TOO_LONG        // 400: Vượt quá 5000 ký tự
CANNOT_EDIT_MESSAGE     // 403: Không phải message của bạn
CANNOT_DELETE_MESSAGE   // 403: Không phải message của bạn
```

#### 💭 Lỗi Conversation
```java
CONVERSATION_NOT_FOUND      // 404: Conversation không tồn tại
NOT_CONVERSATION_MEMBER     // 403: Không phải thành viên
CANNOT_LEAVE_CONVERSATION   // 400: Không thể rời khỏi
```

#### 👥 Lỗi Friend
```java
FRIEND_REQUEST_NOT_FOUND        // 404: Request không tồn tại
ALREADY_FRIENDS                 // 409: Đã là bạn bè
FRIEND_REQUEST_ALREADY_SENT     // 409: Đã gửi request trước đó
CANNOT_ADD_YOURSELF             // 400: Không thể tự kết bạn
```

#### 📎 Lỗi File Upload
```java
FILE_EMPTY              // 400: Không có file
FILE_TOO_LARGE          // 413: Vượt quá kích thước cho phép
INVALID_FILE_TYPE       // 400: Loại file không hỗ trợ
FILE_UPLOAD_FAILED      // 500: Lỗi upload
```

#### ✅ Lỗi Validation
```java
INVALID_INPUT           // 400: Validation chung
INVALID_EMAIL           // 400: Email sai format
INVALID_PHONE           // 400: Số điện thoại sai
INVALID_PASSWORD        // 400: Mật khẩu quá yếu
MISSING_REQUIRED_FIELD  // 400: Thiếu trường bắt buộc
```

#### 🔴 Lỗi Server
```java
INTERNAL_SERVER_ERROR   // 500: Lỗi không mong muốn
DATABASE_ERROR          // 500: Lỗi kết nối DB
EXTERNAL_SERVICE_ERROR  // 503: API bên ngoài không khả dụng
```

---

## 5. Custom Exceptions

### 5.1 AppException (Base Class)

Tất cả custom exceptions kế thừa từ `AppException`.

```java
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object details;
    
    // Constructors...
}
```

### 5.2 ResourceNotFoundException (404)

Cho các resource không tồn tại.

**Helper Methods:**
```java
ResourceNotFoundException.user("user123");
ResourceNotFoundException.message("msg456");
ResourceNotFoundException.conversation("conv789");
ResourceNotFoundException.friendRequest("req101");
```

**Cách Dùng:**
```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    return userRepository.findById(id)
        .map(UserResponse::from)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
}
```

### 5.3 InvalidInputException (400)

Cho validation errors và dữ liệu không hợp lệ.

**Helper Methods:**
```java
InvalidInputException.invalidEmail(email);
InvalidInputException.invalidPhone(phone);
InvalidInputException.invalidPassword();
InvalidInputException.missingField("username");
InvalidInputException.messageTooLong();
InvalidInputException.messageEmpty();
```

**Cách Dùng:**
```java
@PostMapping
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    if (request.getContent().isBlank()) {
        throw InvalidInputException.messageEmpty();
    }
    
    if (request.getContent().length() > 5000) {
        throw InvalidInputException.messageTooLong();
    }
    
    // Xử lý message...
}
```

### 5.4 UnauthorizedException (401)

Cho lỗi authentication.

**Helper Methods:**
```java
UnauthorizedException.invalidCredentials();
UnauthorizedException.invalidToken();
UnauthorizedException.tokenExpired();
UnauthorizedException.notAuthenticated();
```

**Cách Dùng:**
```java
public String login(LoginRequest request) {
    User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(UnauthorizedException::invalidCredentials);
    
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw UnauthorizedException.invalidCredentials();
    }
    
    return generateToken(user);
}
```

### 5.5 ForbiddenException (403)

Cho lỗi authorization (đã login nhưng không có quyền).

**Helper Methods:**
```java
ForbiddenException.accessDenied();
ForbiddenException.cannotEditMessage();
ForbiddenException.cannotDeleteMessage();
ForbiddenException.notConversationMember();
ForbiddenException.userInactive();
```

**Cách Dùng:**
```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
    String currentUserId = JwtUtils.getCurrentUserId();
    Message message = messageRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.message(id));
    
    if (!message.getSender().getId().equals(currentUserId)) {
        throw ForbiddenException.cannotDeleteMessage();
    }
    
    messageRepository.delete(message);
    return ResponseEntity.noContent().build();
}
```

---

## 6. Ví Dụ Sử Dụng

### Ví Dụ 1: Tìm Resource Đơn Giản

```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
    
    return ResponseEntity.ok(UserResponse.from(user));
}
```

**Response khi user không tìm thấy:**
```json
{
  "status": 404,
  "errorCode": "USER_NOT_FOUND",
  "message": "Không tìm thấy người dùng với ID: user123",
  "timestamp": "2026-01-21T14:30:00",
  "path": "/api/v1/users/user123"
}
```

### Ví Dụ 2: Validation

```java
@PostMapping("/register")
public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
    // Validate email
    if (!ValidationUtils.isValidEmail(request.getEmail())) {
        throw InvalidInputException.invalidEmail(request.getEmail());
    }
    
    // Check email tồn tại
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new AppException(
            ErrorCode.USER_ALREADY_EXISTS,
            "Email " + request.getEmail() + " đã được đăng ký"
        );
    }
    
    // Validate password
    if (!ValidationUtils.isValidPassword(request.getPassword())) {
        throw InvalidInputException.invalidPassword();
    }
    
    // Tạo user...
}
```

### Ví Dụ 3: Authorization

```java
@PutMapping("/messages/{id}")
public ResponseEntity<MessageResponse> editMessage(
        @PathVariable String id,
        @RequestBody EditMessageRequest request) {
    
    String currentUserId = JwtUtils.getCurrentUserId();
    
    Message message = messageRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.message(id));
    
    // Kiểm tra ownership
    if (!message.getSender().getId().equals(currentUserId)) {
        throw ForbiddenException.cannotEditMessage();
    }
    
    // Validate content mới
    if (request.getContent().isBlank()) {
        throw InvalidInputException.messageEmpty();
    }
    
    message.setContent(request.getContent());
    message.setEdited(true);
    messageRepository.save(message);
    
    return ResponseEntity.ok(MessageResponse.from(message));
}
```

### Ví Dụ 4: Dùng ErrorCode Trực Tiếp

```java
@PostMapping("/conversations")
public ResponseEntity<ConversationResponse> createConversation(
        @RequestBody CreateConversationRequest request) {
    
    String currentUserId = JwtUtils.getCurrentUserId();
    
    // Kiểm tra user có thể tạo conversation không
    User user = userRepository.findById(currentUserId)
        .orElseThrow(() -> ResourceNotFoundException.user(currentUserId));
    
    if (!user.isActive()) {
        throw new AppException(
            ErrorCode.USER_INACTIVE,
            "Tài khoản của bạn đã bị vô hiệu hóa"
        );
    }
    
    // Validate participants
    if (request.getParticipantIds().isEmpty()) {
        throw new AppException(
            ErrorCode.INVALID_INPUT,
            "Hội thoại phải có ít nhất 1 người tham gia"
        );
    }
    
    // Tạo conversation...
}
```

### Ví Dụ 5: Custom Error Với Details

```java
@PostMapping("/upload")
public ResponseEntity<FileResponse> uploadFile(@RequestParam MultipartFile file) {
    if (file.isEmpty()) {
        throw new AppException(ErrorCode.FILE_EMPTY);
    }
    
    if (!FileUtils.isValidImage(file)) {
        Map<String, Object> details = Map.of(
            "allowedTypes", List.of("JPEG", "PNG", "GIF", "WebP"),
            "receivedType", file.getContentType()
        );
        
        throw new AppException(
            ErrorCode.INVALID_FILE_TYPE,
            "File type không được hỗ trợ: " + file.getContentType(),
            details
        );
    }
    
    // Upload file...
}
```

**Response:**
```json
{
  "status": 400,
  "errorCode": "INVALID_FILE_TYPE",
  "message": "File type không được hỗ trợ: application/pdf",
  "timestamp": "2026-01-21T14:30:00",
  "path": "/api/v1/files/upload",
  "details": {
    "allowedTypes": ["JPEG", "PNG", "GIF", "WebP"],
    "receivedType": "application/pdf"
  }
}
```

---

## 7. Best Practices

### ✅ NÊN

**1. Dùng specific exceptions với helper methods**
```java
// ✅ TỐT - Rõ ràng và cụ thể
throw ResourceNotFoundException.user(userId);
throw InvalidInputException.invalidEmail(email);
throw ForbiddenException.cannotDeleteMessage();
```

**2. Ném exception sớm (fail fast)**
```java
// ✅ TỐT - Validate ngay từ đầu
public void sendMessage(String userId, String content) {
    if (content.isBlank()) {
        throw InvalidInputException.messageEmpty();
    }
    
    if (content.length() > 5000) {
        throw InvalidInputException.messageTooLong();
    }
    
    // Xử lý message...
}
```

**3. Dùng ErrorCode cho scenarios mới**
```java
// ✅ TỐT - Dùng ErrorCode định nghĩa sẵn
if (user.isBanned()) {
    throw new AppException(ErrorCode.USER_INACTIVE);
}
```

**4. Thêm context vào error messages**
```java
// ✅ TỐT - Bao gồm thông tin liên quan
throw new AppException(
    ErrorCode.USER_NOT_FOUND,
    "Không tìm thấy người dùng với email: " + email
);
```

### ❌ KHÔNG NÊN

**1. Đừng dùng generic exceptions**
```java
// ❌ TỆ - Quá chung chung
throw new RuntimeException("User not found");
throw new Exception("Something went wrong");
```

**2. Đừng catch và bỏ qua exceptions**
```java
// ❌ TỆ - Nuốt exception
try {
    user = userRepository.findById(id).get();
} catch (Exception e) {
    // Không làm gì
}
```

**3. Đừng return null thay vì throw**
```java
// ❌ TỆ - Return null
public User getUser(String id) {
    return userRepository.findById(id).orElse(null);
}

// ✅ TỐT - Throw exception
public User getUser(String id) {
    return userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
}
```

**4. Đừng tạo error response thủ công trong controllers**
```java
// ❌ TỆ - Manual error response
@GetMapping("/{id}")
public ResponseEntity<?> getUser(@PathVariable String id) {
    Optional<User> user = userRepository.findById(id);
    if (user.isEmpty()) {
        return ResponseEntity.status(404).body(
            Map.of("error", "User not found")
        );
    }
    return ResponseEntity.ok(user.get());
}

// ✅ TỐT - Để GlobalExceptionHandler xử lý
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
    return ResponseEntity.ok(UserResponse.from(user));
}
```

---

## 8. Testing Exception Handling

### Ví Dụ Unit Test

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void getUser_KhiKhongTimThay_NenThrowException() {
        // Given
        String userId = "nonexistent123";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // When & Then
        ResourceNotFoundException exception = assertThrows(
            ResourceNotFoundException.class,
            () -> userService.getUser(userId)
        );
        
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(userId));
    }
    
    @Test
    void register_KhiEmailTonTai_NenThrowException() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        
        // When & Then
        AppException exception = assertThrows(
            AppException.class,
            () -> userService.register(request)
        );
        
        assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
    }
}
```

### Ví Dụ Integration Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void getUser_KhiKhongTimThay_NenReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/users/nonexistent123"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.path").value("/api/v1/users/nonexistent123"));
    }
    
    @Test
    @WithMockUser(username = "user123")
    void deleteMessage_KhiKhongPhaichusohuu_NenReturn403() throws Exception {
        mockMvc.perform(delete("/api/v1/messages/msg456"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.errorCode").value("CANNOT_DELETE_MESSAGE"));
    }
}
```

---

## 📚 Tài Nguyên Bổ Sung

- [Spring Boot Exception Handling](https://spring.io/guides/tutorials/rest/)
- [HTTP Status Codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)
- [REST API Error Handling Best Practices](https://www.baeldung.com/exception-handling-for-rest-with-spring)

---

**Version**: 1.0  
**Tạo ngày**: 21/01/2026  
**Người duy trì**: Fruvia Development Team
