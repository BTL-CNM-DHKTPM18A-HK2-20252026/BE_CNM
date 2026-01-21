# Quản Lý API Response - Fruvia Chat

> 📦 **Package**: `iuh.fit.response`  
> 🎯 **Mục đích**: Response format chuẩn cho tất cả API endpoints  
> 🚀 **Trạng thái**: Production Ready

---

## 📋 Mục Lục
1. [Tổng Quan](#tổng-quan)
2. [Cấu Trúc ApiResponse](#cấu-trúc-apiresponse)
3. [Ví Dụ Sử Dụng](#ví-dụ-sử-dụng)
4. [PageResponse Cho Pagination](#pageresponse-cho-pagination)
5. [Best Practices](#best-practices)
6. [Hướng Dẫn Migration](#hướng-dẫn-migration)

---

## 1. Tổng Quan

### Đây là gì?

Hệ thống response wrapper chuẩn cung cấp:
- ✅ **Format nhất quán** cho mọi API responses (success & error)
- ✅ **Type-safe** generic wrapper
- ✅ **Hỗ trợ pagination** với PageResponse
- ✅ **Dễ sử dụng** với static factory methods
- ✅ **Frontend-friendly** với indicators rõ ràng

### Tại sao dùng ApiResponse?

**❌ Không Dùng ApiResponse (Không Nhất Quán):**
```json
// Success response
{
  "id": "user123",
  "name": "John Doe"
}

// Error response (format hoàn toàn khác)
{
  "status": 404,
  "errorCode": "USER_NOT_FOUND",
  "message": "User not found"
}
```

**✅ Với ApiResponse (Nhất Quán):**
```json
// Success response
{
  "success": true,
  "message": "Lấy thông tin user thành công",
  "data": {
    "id": "user123",
    "name": "John Doe"
  },
  "timestamp": "2026-01-21T14:30:00"
}

// Error response (cùng cấu trúc)
{
  "success": false,
  "message": "Không tìm thấy user",
  "error": {
    "code": "USER_NOT_FOUND",
    "details": null
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

---

## 2. Cấu Trúc ApiResponse

### Các Trường

```java
public class ApiResponse<T> {
    private boolean success;         // true = success, false = error
    private String message;          // Thông báo bằng tiếng Việt
    private T data;                  // Dữ liệu response (chỉ khi success = true)
    private ErrorInfo error;         // Thông tin lỗi (chỉ khi success = false)
    private LocalDateTime timestamp; // Thời điểm response
    private Object metadata;         // Metadata bổ sung (pagination, etc.)
}
```

### Format Success Response

```json
{
  "success": true,
  "message": "Thao tác thành công",
  "data": { ... },
  "timestamp": "2026-01-21T14:30:00",
  "metadata": { ... }  // Optional
}
```

### Format Error Response

```json
{
  "success": false,
  "message": "Thông báo lỗi",
  "error": {
    "code": "ERROR_CODE",
    "details": { ... }  // Optional
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

---

## 3. Ví Dụ Sử Dụng

### Ví Dụ 1: GET Request Đơn Giản

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String id) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> ResourceNotFoundException.user(id));
    
    UserResponse userResponse = UserResponse.from(user);
    
    return ResponseEntity.ok(
        ApiResponse.success(userResponse, "Lấy thông tin user thành công")
    );
}
```

**Response:**
```json
{
  "success": true,
  "message": "Lấy thông tin user thành công",
  "data": {
    "id": "user123",
    "name": "John Doe",
    "email": "john@example.com",
    "avatar": "https://..."
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

### Ví Dụ 2: POST Request (Tạo Resource)

```java
@PostMapping
public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
        @Valid @RequestBody MessageRequest request) {
    
    String userId = JwtUtils.getCurrentUserId();
    Message message = messageService.sendMessage(userId, request);
    MessageResponse response = MessageResponse.from(message);
    
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Gửi tin nhắn thành công"));
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "message": "Gửi tin nhắn thành công",
  "data": {
    "id": "msg456",
    "content": "Hello World",
    "senderId": "user123",
    "createdAt": "2026-01-21T14:30:00"
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

### Ví Dụ 3: DELETE Request (Không Có Data)

```java
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<Void>> deleteMessage(@PathVariable String id) {
    String currentUserId = JwtUtils.getCurrentUserId();
    
    messageService.delete(id, currentUserId);
    
    return ResponseEntity.ok(
        ApiResponse.success("Xóa tin nhắn thành công")
    );
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Xóa tin nhắn thành công",
  "timestamp": "2026-01-21T14:30:00"
}
```

### Ví Dụ 4: List Với Metadata

```java
@GetMapping
public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations(
        @RequestParam(required = false) String filter) {
    
    String userId = JwtUtils.getCurrentUserId();
    List<Conversation> conversations = conversationService.getConversations(userId, filter);
    List<ConversationResponse> responses = conversations.stream()
        .map(ConversationResponse::from)
        .toList();
    
    Map<String, Object> metadata = Map.of(
        "total", responses.size(),
        "filter", filter != null ? filter : "all"
    );
    
    return ResponseEntity.ok(
        ApiResponse.success(responses, "Lấy danh sách hội thoại thành công", metadata)
    );
}
```

**Response:**
```json
{
  "success": true,
  "message": "Lấy danh sách hội thoại thành công",
  "data": [
    {
      "id": "conv1",
      "name": "Group Chat",
      "lastMessage": "Hello",
      "unreadCount": 3
    },
    {
      "id": "conv2",
      "name": "John Doe",
      "lastMessage": "How are you?",
      "unreadCount": 0
    }
  ],
  "metadata": {
    "total": 2,
    "filter": "all"
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

---

## 4. PageResponse Cho Pagination

### Cách Dùng

```java
@GetMapping("/messages")
public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getMessages(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<Message> messagePage = messageRepository.findByConversationId(conversationId, pageable);
    
    // Convert sang PageResponse
    Page<MessageResponse> responsePage = messagePage.map(MessageResponse::from);
    PageResponse<MessageResponse> pageResponse = PageResponse.of(responsePage);
    
    return ResponseEntity.ok(
        ApiResponse.success(pageResponse, "Lấy danh sách tin nhắn thành công")
    );
}
```

### Format Response

```json
{
  "success": true,
  "message": "Lấy danh sách tin nhắn thành công",
  "data": {
    "items": [
      {
        "id": "msg1",
        "content": "Hello",
        "senderId": "user123",
        "createdAt": "2026-01-21T14:30:00"
      },
      {
        "id": "msg2",
        "content": "Hi there",
        "senderId": "user456",
        "createdAt": "2026-01-21T14:25:00"
      }
    ],
    "pageInfo": {
      "currentPage": 0,
      "pageSize": 20,
      "hasNext": true,
      "hasPrevious": false,
      "isFirst": true,
      "isLast": false
    },
    "totalItems": 150,
    "totalPages": 8
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

### Tạo PageResponse Thủ Công

```java
// Khi không dùng Spring Data Page
List<Message> messages = messageRepository.findByUserId(userId);
List<MessageResponse> responses = messages.stream()
    .map(MessageResponse::from)
    .toList();

PageResponse<MessageResponse> pageResponse = PageResponse.of(
    responses,       // items
    0,              // currentPage
    20,             // pageSize
    messages.size() // totalItems
);

return ResponseEntity.ok(
    ApiResponse.success(pageResponse, "Lấy tin nhắn thành công")
);
```

---

## 5. Best Practices

### ✅ NÊN

**1. Luôn dùng ApiResponse để đồng nhất**
```java
// ✅ TỐT
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String id) {
    return ResponseEntity.ok(ApiResponse.success(userResponse));
}
```

**2. Dùng messages có ý nghĩa**
```java
// ✅ TỐT - Message tiếng Việt rõ ràng, cụ thể
ApiResponse.success(user, "Cập nhật thông tin user thành công")
ApiResponse.success("Xóa tin nhắn thành công")

// ❌ TỆ - Message chung chung
ApiResponse.success(user, "Success")
ApiResponse.success("OK")
```

**3. Dùng HTTP status codes phù hợp**
```java
// ✅ TỐT
return ResponseEntity
    .status(HttpStatus.CREATED)  // 201 cho tạo mới
    .body(ApiResponse.success(data, "Tạo thành công"));

return ResponseEntity
    .noContent()  // 204 cho delete (tuỳ chọn)
    .build();

return ResponseEntity.ok(  // 200 cho success
    ApiResponse.success(data)
);
```

**4. Thêm metadata khi hữu ích**
```java
// ✅ TỐT - Metadata hữu ích
Map<String, Object> metadata = Map.of(
    "page", page,
    "size", size,
    "total", totalItems,
    "filter", appliedFilter
);

return ResponseEntity.ok(
    ApiResponse.success(data, "Thành công", metadata)
);
```

### ❌ KHÔNG NÊN

**1. Đừng trộn formats**
```java
// ❌ TỆ - Không nhất quán
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    return ResponseEntity.ok(userResponse);  // Thiếu ApiResponse wrapper
}
```

**2. Đừng return ApiResponse trong error handlers**
```java
// ❌ TỆ - GlobalExceptionHandler đã xử lý errors rồi
@ExceptionHandler(AppException.class)
public ResponseEntity<ApiResponse<Void>> handleException(AppException ex) {
    return ResponseEntity.status(400)
        .body(ApiResponse.error("ERROR", ex.getMessage()));
}

// ✅ TỐT - Dùng ErrorResponse từ exception package
@ExceptionHandler(AppException.class)
public ResponseEntity<ErrorResponse> handleException(AppException ex) {
    return ResponseEntity.status(ex.getErrorCode().getStatus())
        .body(new ErrorResponse(...));
}
```

**3. Đừng thêm sensitive data vào metadata**
```java
// ❌ TỆ
Map<String, Object> metadata = Map.of(
    "jwt_secret", jwtSecret,  // Không bao giờ expose secrets!
    "database_url", dbUrl
);
```

---

## 6. Hướng Dẫn Migration

### Bước 1: Cập Nhật Controllers Hiện Tại

**Trước:**
```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    User user = userService.getUser(id);
    return ResponseEntity.ok(UserResponse.from(user));
}
```

**Sau:**
```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String id) {
    User user = userService.getUser(id);
    UserResponse response = UserResponse.from(user);
    return ResponseEntity.ok(
        ApiResponse.success(response, "Lấy thông tin user thành công")
    );
}
```

### Bước 2: Cập Nhật Frontend Integration

**Frontend parsing:**
```typescript
// TypeScript example
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  error?: {
    code: string;
    details?: any;
  };
  timestamp: string;
  metadata?: any;
}

// Sử dụng
const response = await fetch('/api/v1/users/123');
const apiResponse: ApiResponse<User> = await response.json();

if (apiResponse.success) {
  const user = apiResponse.data;
  console.log('User:', user);
} else {
  console.error('Lỗi:', apiResponse.error.code, apiResponse.message);
}
```

### Bước 3: Cập Nhật Tests

**Trước:**
```java
@Test
void getUser_NenReturnUser() throws Exception {
    mockMvc.perform(get("/api/v1/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.name").value("John Doe"));
}
```

**Sau:**
```java
@Test
void getUser_NenReturnApiResponse() throws Exception {
    mockMvc.perform(get("/api/v1/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.data.id").value(userId))
        .andExpect(jsonPath("$.data.name").value("John Doe"))
        .andExpect(jsonPath("$.timestamp").exists());
}
```

---

## 📚 Tham Khảo Nhanh

### Static Factory Methods

```java
// Success responses
ApiResponse.success(data)
ApiResponse.success(data, message)
ApiResponse.success(data, message, metadata)
ApiResponse.success(message)  // Không có data

// Error responses (dùng trong trường hợp đặc biệt, thường GlobalExceptionHandler xử lý)
ApiResponse.error(errorCode, message)
ApiResponse.error(errorCode, message, details)
```

### HTTP Status Codes

| Thao Tác | Status Code | Khi Nào Dùng |
|----------|-------------|--------------|
| GET | 200 OK | Lấy resource thành công |
| POST | 201 CREATED | Tạo resource thành công |
| PUT | 200 OK | Cập nhật resource thành công |
| DELETE | 200 OK hoặc 204 NO CONTENT | Xóa resource thành công |
| PATCH | 200 OK | Cập nhật một phần thành công |

---

## 🎯 Tóm Tắt

**Điểm Chính:**
1. ✅ Luôn wrap responses trong `ApiResponse<T>`
2. ✅ Dùng `PageResponse<T>` cho paginated data
3. ✅ Cung cấp messages tiếng Việt rõ ràng
4. ✅ Thêm metadata khi hữu ích
5. ✅ Duy trì consistency trên tất cả endpoints
6. ✅ Để GlobalExceptionHandler quản lý errors

**Lợi Ích:**
- 🚀 Frontend integration dễ dàng hơn
- 📊 API contract nhất quán
- 🎯 Type-safe responses
- 🔄 Dễ mở rộng (metadata, pagination)
- 📖 API tự document

---

**Version**: 1.0  
**Tạo ngày**: 21/01/2026  
**Người duy trì**: Fruvia Development Team
