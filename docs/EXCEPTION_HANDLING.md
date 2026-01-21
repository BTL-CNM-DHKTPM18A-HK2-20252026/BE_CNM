# Exception Handling System - Fruvia Chat

> 📦 **Package**: `iuh.fit.exception`  
> 🎯 **Purpose**: Centralized error handling with standardized response format  
> 🚀 **Status**: Production Ready

---

## 📋 Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Error Response Format](#error-response-format)
4. [Error Codes](#error-codes)
5. [Custom Exceptions](#custom-exceptions)
6. [Usage Examples](#usage-examples)
7. [Best Practices](#best-practices)
8. [Testing Exception Handling](#testing-exception-handling)

---

## 1. Overview

### What is this?

A comprehensive exception handling system that provides:
- ✅ **Standardized error responses** across all API endpoints
- ✅ **40+ predefined error codes** for common scenarios
- ✅ **Type-safe exceptions** with helper methods
- ✅ **Automatic error handling** via `@RestControllerAdvice`
- ✅ **Consistent error format** for frontend integration

### Why do we need it?

**❌ Without Exception Handling:**
```json
// Inconsistent error responses
{
  "error": "User not found"
}

// Or sometimes:
{
  "message": "Cannot find user"
}

// Or even:
"Internal Server Error"
```

**✅ With Exception Handling:**
```json
// Always consistent format
{
  "status": 404,
  "errorCode": "USER_NOT_FOUND",
  "message": "Không tìm thấy người dùng với ID: user123",
  "timestamp": "2026-01-21T14:30:00",
  "path": "/api/v1/users/user123"
}
```

---

## 2. Architecture

### File Structure

```
iuh/fit/exception/
├── ErrorCode.java                    # Enum with 40+ error codes
├── ErrorResponse.java                # Standardized response format
├── AppException.java                 # Base custom exception
├── ResourceNotFoundException.java    # 404 Not Found
├── InvalidInputException.java        # 400 Bad Request
├── UnauthorizedException.java        # 401 Unauthorized
├── ForbiddenException.java           # 403 Forbidden
└── GlobalExceptionHandler.java       # @RestControllerAdvice
```

### Component Diagram

```
Controller/Service throws Exception
        ↓
GlobalExceptionHandler catches it
        ↓
Maps to ErrorResponse
        ↓
Returns standardized JSON to client
```

---

## 3. Error Response Format

### ErrorResponse.java

**Fields:**
- `status` (int): HTTP status code (400, 401, 404, 500...)
- `errorCode` (String): Specific error identifier (USER_NOT_FOUND, INVALID_TOKEN...)
- `message` (String): Human-readable message in Vietnamese
- `timestamp` (LocalDateTime): When the error occurred
- `path` (String): API endpoint that caused the error
- `details` (Object, optional): Additional context (validation errors, etc.)

**Example Response:**

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

## 4. Error Codes

### ErrorCode.java

An enum defining all possible error codes with associated HTTP status and message.

### Categories

#### 🔐 Authentication & Authorization
```java
INVALID_CREDENTIALS      // 401: Wrong email/password
INVALID_TOKEN           // 401: Token invalid or malformed
TOKEN_EXPIRED           // 401: Token expired
UNAUTHORIZED            // 401: Not logged in
FORBIDDEN               // 403: No permission
```

#### 👤 User Errors
```java
USER_NOT_FOUND          // 404: User doesn't exist
USER_ALREADY_EXISTS     // 409: Email already registered
USER_INACTIVE           // 403: Account deactivated
INVALID_USER_DATA       // 400: Invalid user data
```

#### 💬 Message Errors
```java
MESSAGE_NOT_FOUND       // 404: Message doesn't exist
MESSAGE_EMPTY           // 400: Content is blank
MESSAGE_TOO_LONG        // 400: Exceeds 5000 chars
CANNOT_EDIT_MESSAGE     // 403: Not your message
CANNOT_DELETE_MESSAGE   // 403: Not your message
```

#### 💭 Conversation Errors
```java
CONVERSATION_NOT_FOUND      // 404: Conversation doesn't exist
NOT_CONVERSATION_MEMBER     // 403: Not a member
CANNOT_LEAVE_CONVERSATION   // 400: Cannot leave
```

#### 👥 Friend Errors
```java
FRIEND_REQUEST_NOT_FOUND        // 404: Request doesn't exist
ALREADY_FRIENDS                 // 409: Already friends
FRIEND_REQUEST_ALREADY_SENT     // 409: Duplicate request
CANNOT_ADD_YOURSELF             // 400: Cannot add self
```

#### 📎 File Upload Errors
```java
FILE_EMPTY              // 400: No file provided
FILE_TOO_LARGE          // 413: Exceeds size limit
INVALID_FILE_TYPE       // 400: Unsupported format
FILE_UPLOAD_FAILED      // 500: Upload error
```

#### ✅ Validation Errors
```java
INVALID_INPUT           // 400: Generic validation
INVALID_EMAIL           // 400: Email format wrong
INVALID_PHONE           // 400: Phone format wrong
INVALID_PASSWORD        // 400: Password too weak
MISSING_REQUIRED_FIELD  // 400: Required field empty
```

#### 🔴 Server Errors
```java
INTERNAL_SERVER_ERROR   // 500: Unexpected error
DATABASE_ERROR          // 500: DB connection issue
EXTERNAL_SERVICE_ERROR  // 503: External API down
```

---

## 5. Custom Exceptions

### 5.1 AppException (Base Class)

All custom exceptions extend `AppException`.

```java
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object details;
    
    // Constructors...
}
```

### 5.2 ResourceNotFoundException (404)

For resources that don't exist.

**Helper Methods:**
```java
ResourceNotFoundException.user("user123");
ResourceNotFoundException.message("msg456");
ResourceNotFoundException.conversation("conv789");
ResourceNotFoundException.friendRequest("req101");
```

**Usage:**
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

For validation errors and invalid data.

**Helper Methods:**
```java
InvalidInputException.invalidEmail(email);
InvalidInputException.invalidPhone(phone);
InvalidInputException.invalidPassword();
InvalidInputException.missingField("username");
InvalidInputException.messageTooLong();
InvalidInputException.messageEmpty();
```

**Usage:**
```java
@PostMapping
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    if (request.getContent().isBlank()) {
        throw InvalidInputException.messageEmpty();
    }
    
    if (request.getContent().length() > 5000) {
        throw InvalidInputException.messageTooLong();
    }
    
    // Process message...
}
```

### 5.4 UnauthorizedException (401)

For authentication failures.

**Helper Methods:**
```java
UnauthorizedException.invalidCredentials();
UnauthorizedException.invalidToken();
UnauthorizedException.tokenExpired();
UnauthorizedException.notAuthenticated();
```

**Usage:**
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

For authorization failures (user logged in but no permission).

**Helper Methods:**
```java
ForbiddenException.accessDenied();
ForbiddenException.cannotEditMessage();
ForbiddenException.cannotDeleteMessage();
ForbiddenException.notConversationMember();
ForbiddenException.userInactive();
```

**Usage:**
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

## 6. Usage Examples

### Example 1: Simple Resource Lookup

```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
    
    return ResponseEntity.ok(UserResponse.from(user));
}
```

**Response when user not found:**
```json
{
  "status": 404,
  "errorCode": "USER_NOT_FOUND",
  "message": "Không tìm thấy người dùng với ID: user123",
  "timestamp": "2026-01-21T14:30:00",
  "path": "/api/v1/users/user123"
}
```

### Example 2: Validation

```java
@PostMapping("/register")
public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
    // Validate email
    if (!ValidationUtils.isValidEmail(request.getEmail())) {
        throw InvalidInputException.invalidEmail(request.getEmail());
    }
    
    // Check if email exists
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
    
    // Create user...
}
```

### Example 3: Authorization

```java
@PutMapping("/messages/{id}")
public ResponseEntity<MessageResponse> editMessage(
        @PathVariable String id,
        @RequestBody EditMessageRequest request) {
    
    String currentUserId = JwtUtils.getCurrentUserId();
    
    Message message = messageRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.message(id));
    
    // Check ownership
    if (!message.getSender().getId().equals(currentUserId)) {
        throw ForbiddenException.cannotEditMessage();
    }
    
    // Validate new content
    if (request.getContent().isBlank()) {
        throw InvalidInputException.messageEmpty();
    }
    
    message.setContent(request.getContent());
    message.setEdited(true);
    messageRepository.save(message);
    
    return ResponseEntity.ok(MessageResponse.from(message));
}
```

### Example 4: Using ErrorCode Directly

```java
@PostMapping("/conversations")
public ResponseEntity<ConversationResponse> createConversation(
        @RequestBody CreateConversationRequest request) {
    
    String currentUserId = JwtUtils.getCurrentUserId();
    
    // Check if user can create conversations
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
    
    // Create conversation...
}
```

### Example 5: Custom Error with Details

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

### ✅ DO

**1. Use specific exceptions with helper methods**
```java
// ✅ GOOD - Clear and specific
throw ResourceNotFoundException.user(userId);
throw InvalidInputException.invalidEmail(email);
throw ForbiddenException.cannotDeleteMessage();
```

**2. Throw exceptions early (fail fast)**
```java
// ✅ GOOD - Validate at the beginning
public void sendMessage(String userId, String content) {
    if (content.isBlank()) {
        throw InvalidInputException.messageEmpty();
    }
    
    if (content.length() > 5000) {
        throw InvalidInputException.messageTooLong();
    }
    
    // Process message...
}
```

**3. Use ErrorCode for new scenarios**
```java
// ✅ GOOD - Use predefined ErrorCode
if (user.isBanned()) {
    throw new AppException(ErrorCode.USER_INACTIVE);
}
```

**4. Add context to error messages**
```java
// ✅ GOOD - Include relevant info
throw new AppException(
    ErrorCode.USER_NOT_FOUND,
    "Không tìm thấy người dùng với email: " + email
);
```

### ❌ DON'T

**1. Don't use generic exceptions**
```java
// ❌ BAD - Too generic
throw new RuntimeException("User not found");
throw new Exception("Something went wrong");
```

**2. Don't catch and ignore exceptions**
```java
// ❌ BAD - Swallowing exceptions
try {
    user = userRepository.findById(id).get();
} catch (Exception e) {
    // Do nothing
}
```

**3. Don't return null instead of throwing**
```java
// ❌ BAD - Returning null
public User getUser(String id) {
    return userRepository.findById(id).orElse(null);
}

// ✅ GOOD - Throw exception
public User getUser(String id) {
    return userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
}
```

**4. Don't create custom error responses in controllers**
```java
// ❌ BAD - Manual error response
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

// ✅ GOOD - Let GlobalExceptionHandler handle it
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
    return ResponseEntity.ok(UserResponse.from(user));
}
```

---

## 8. Testing Exception Handling

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void getUser_WhenUserNotFound_ShouldThrowException() {
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
    void register_WhenEmailExists_ShouldThrowException() {
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

### Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void getUser_WhenNotFound_ShouldReturn404() throws Exception {
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
    void deleteMessage_WhenNotOwner_ShouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/v1/messages/msg456"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.errorCode").value("CANNOT_DELETE_MESSAGE"));
    }
}
```

---

## 📚 Additional Resources

- [Spring Boot Exception Handling](https://spring.io/guides/tutorials/rest/)
- [HTTP Status Codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status)
- [REST API Error Handling Best Practices](https://www.baeldung.com/exception-handling-for-rest-with-spring)

---

**Version**: 1.0  
**Created**: 21/01/2026  
**Maintained By**: Fruvia Development Team
