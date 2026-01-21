# API Response Management - Fruvia Chat

> 📦 **Package**: `iuh.fit.response`  
> 🎯 **Purpose**: Standardized response format for all API endpoints  
> 🚀 **Status**: Production Ready

---

## 📋 Table of Contents
1. [Overview](#overview)
2. [ApiResponse Structure](#apiresponse-structure)
3. [Usage Examples](#usage-examples)
4. [PageResponse for Pagination](#pageresponse-for-pagination)
5. [Best Practices](#best-practices)
6. [Migration Guide](#migration-guide)

---

## 1. Overview

### What is this?

A standardized response wrapper system that provides:
- ✅ **Consistent format** for all API responses (success & error)
- ✅ **Type-safe** generic wrapper
- ✅ **Pagination support** with PageResponse
- ✅ **Easy to use** static factory methods
- ✅ **Frontend-friendly** with clear success/error indicators

### Why use ApiResponse?

**❌ Without ApiResponse (Inconsistent):**
```json
// Success response
{
  "id": "user123",
  "name": "John Doe"
}

// Error response (completely different format)
{
  "status": 404,
  "errorCode": "USER_NOT_FOUND",
  "message": "User not found"
}
```

**✅ With ApiResponse (Consistent):**
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

// Error response (same structure)
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

## 2. ApiResponse Structure

### Fields

```java
public class ApiResponse<T> {
    private boolean success;         // true = success, false = error
    private String message;          // Human-readable message (Vietnamese)
    private T data;                  // Response data (only when success = true)
    private ErrorInfo error;         // Error info (only when success = false)
    private LocalDateTime timestamp; // Response timestamp
    private Object metadata;         // Optional metadata (pagination, etc.)
}
```

### Success Response Format

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "timestamp": "2026-01-21T14:30:00",
  "metadata": { ... }  // Optional
}
```

### Error Response Format

```json
{
  "success": false,
  "message": "Error message",
  "error": {
    "code": "ERROR_CODE",
    "details": { ... }  // Optional
  },
  "timestamp": "2026-01-21T14:30:00"
}
```

---

## 3. Usage Examples

### Example 1: Simple GET Request

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

### Example 2: POST Request (Create Resource)

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

### Example 3: DELETE Request (No Data)

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

### Example 4: List with Metadata

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

## 4. PageResponse for Pagination

### Usage

```java
@GetMapping("/messages")
public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getMessages(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<Message> messagePage = messageRepository.findByConversationId(conversationId, pageable);
    
    // Convert to PageResponse
    Page<MessageResponse> responsePage = messagePage.map(MessageResponse::from);
    PageResponse<MessageResponse> pageResponse = PageResponse.of(responsePage);
    
    return ResponseEntity.ok(
        ApiResponse.success(pageResponse, "Lấy danh sách tin nhắn thành công")
    );
}
```

### Response Format

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

### Manual PageResponse Creation

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

### ✅ DO

**1. Always use ApiResponse for consistency**
```java
// ✅ GOOD
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable String id) {
    return ResponseEntity.ok(ApiResponse.success(userResponse));
}
```

**2. Use meaningful messages**
```java
// ✅ GOOD - Clear, specific Vietnamese message
ApiResponse.success(user, "Cập nhật thông tin user thành công")
ApiResponse.success("Xóa tin nhắn thành công")

// ❌ BAD - Generic English message
ApiResponse.success(user, "Success")
ApiResponse.success("OK")
```

**3. Use appropriate HTTP status codes**
```java
// ✅ GOOD
return ResponseEntity
    .status(HttpStatus.CREATED)  // 201 for creation
    .body(ApiResponse.success(data, "Tạo thành công"));

return ResponseEntity
    .noContent()  // 204 for delete (alternative)
    .build();

return ResponseEntity.ok(  // 200 for success
    ApiResponse.success(data)
);
```

**4. Include metadata when useful**
```java
// ✅ GOOD - Helpful metadata
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

### ❌ DON'T

**1. Don't mix formats**
```java
// ❌ BAD - Inconsistent
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    return ResponseEntity.ok(userResponse);  // Missing ApiResponse wrapper
}
```

**2. Don't return ApiResponse in error handlers**
```java
// ❌ BAD - GlobalExceptionHandler already handles errors
@ExceptionHandler(AppException.class)
public ResponseEntity<ApiResponse<Void>> handleException(AppException ex) {
    return ResponseEntity.status(400)
        .body(ApiResponse.error("ERROR", ex.getMessage()));
}

// ✅ GOOD - Use ErrorResponse from exception package
@ExceptionHandler(AppException.class)
public ResponseEntity<ErrorResponse> handleException(AppException ex) {
    return ResponseEntity.status(ex.getErrorCode().getStatus())
        .body(new ErrorResponse(...));
}
```

**3. Don't include sensitive data in metadata**
```java
// ❌ BAD
Map<String, Object> metadata = Map.of(
    "jwt_secret", jwtSecret,  // Never expose secrets!
    "database_url", dbUrl
);
```

---

## 6. Migration Guide

### Step 1: Update Existing Controllers

**Before:**
```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
    User user = userService.getUser(id);
    return ResponseEntity.ok(UserResponse.from(user));
}
```

**After:**
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

### Step 2: Update Frontend Integration

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

// Usage
const response = await fetch('/api/v1/users/123');
const apiResponse: ApiResponse<User> = await response.json();

if (apiResponse.success) {
  const user = apiResponse.data;
  console.log('User:', user);
} else {
  console.error('Error:', apiResponse.error.code, apiResponse.message);
}
```

### Step 3: Update Tests

**Before:**
```java
@Test
void getUser_ShouldReturnUser() throws Exception {
    mockMvc.perform(get("/api/v1/users/{id}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId))
        .andExpect(jsonPath("$.name").value("John Doe"));
}
```

**After:**
```java
@Test
void getUser_ShouldReturnApiResponse() throws Exception {
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

## 📚 Quick Reference

### Static Factory Methods

```java
// Success responses
ApiResponse.success(data)
ApiResponse.success(data, message)
ApiResponse.success(data, message, metadata)
ApiResponse.success(message)  // No data

// Error responses (use in special cases, usually GlobalExceptionHandler handles this)
ApiResponse.error(errorCode, message)
ApiResponse.error(errorCode, message, details)
```

### HTTP Status Codes

| Operation | Status Code | When to Use |
|-----------|-------------|-------------|
| GET | 200 OK | Successfully retrieved resource |
| POST | 201 CREATED | Successfully created resource |
| PUT | 200 OK | Successfully updated resource |
| DELETE | 200 OK or 204 NO CONTENT | Successfully deleted resource |
| PATCH | 200 OK | Successfully partially updated |

---

## 🎯 Summary

**Key Points:**
1. ✅ Always wrap responses in `ApiResponse<T>`
2. ✅ Use `PageResponse<T>` for paginated data
3. ✅ Provide clear Vietnamese messages
4. ✅ Include metadata when helpful
5. ✅ Maintain consistency across all endpoints
6. ✅ Let GlobalExceptionHandler manage errors

**Benefits:**
- 🚀 Easier frontend integration
- 📊 Consistent API contract
- 🎯 Type-safe responses
- 🔄 Easy to extend (metadata, pagination)
- 📖 Self-documenting API

---

**Version**: 1.0  
**Created**: 21/01/2026  
**Maintained By**: Fruvia Development Team
