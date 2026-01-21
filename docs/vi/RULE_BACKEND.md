# Quy Tắc Phát Triển Backend - Fruvia Chat

> 🎯 **Mục đích**: Đảm bảo chất lượng code, tính nhất quán, và khả năng bảo trì  
> 👥 **Áp dụng cho**: Tất cả developers làm việc với Fruvia Chat Backend  
> ⚖️ **Status**: Living Document - Cập nhật thường xuyên

---

## 📋 Mục Lục
1. [Naming Conventions](#naming-conventions)
2. [Package Structure](#package-structure)
3. [API Design Rules](#api-design-rules)
4. [Error Handling](#error-handling)
5. [Security Standards](#security-standards)
6. [Database Guidelines](#database-guidelines)
7. [Testing Requirements](#testing-requirements)
8. [Documentation Standards](#documentation-standards)

---

## 1. Naming Conventions

### Classes

#### Controllers
```java
// ✅ ĐÚNG
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController { }

// ❌ SAI
public class MessageAPI { }
public class MessageREST { }
```

#### Services
```java
// ✅ ĐÚNG
@Service
public class MessageService { }

// ❌ SAI
public class MessageManager { }
public class MessageHandler { }
```

#### Repositories
```java
// ✅ ĐÚNG
@Repository
public interface MessageRepository extends JpaRepository<Message, String> { }

// ❌ SAI
public interface MessageDAO { }
```

### Methods

#### Controllers
```java
// ✅ ĐÚNG - RESTful verbs
public ResponseEntity<MessageResponse> createMessage()
public ResponseEntity<MessageResponse> getMessage()
public ResponseEntity<MessageResponse> updateMessage()
public ResponseEntity<Void> deleteMessage()

// ❌ SAI - Không rõ ràng
public ResponseEntity<MessageResponse> process()
public ResponseEntity<MessageResponse> handle()
```

#### Services
```java
// ✅ ĐÚNG - Business logic verbs
public Message sendMessage(String userId, MessageRequest request)
public List<Message> getMessagesByConversation(String conversationId)
public void markAsRead(String messageId, String userId)

// ❌ SAI
public Message doSomething()
public void process()
```

### Variables
```java
// ✅ ĐÚNG
private final MessageService messageService;
private List<Message> unreadMessages;
private String conversationId;

// ❌ SAI
private MessageService ms;
private List<Message> list;
private String id; // Quá chung chung
```

---

## 2. Package Structure

### Tổ Chức Chuẩn
```
com.fruvia.chat/
├── configuration/       # Spring configuration classes
│   ├── SecurityConfig
│   ├── CorsConfig
│   └── CloudinaryConfig
├── controller/          # REST controllers
│   ├── AuthController
│   ├── MessageController
│   └── UserController
├── dto/                 # Data Transfer Objects
│   ├── request/
│   └── response/
├── entity/              # JPA entities
├── enums/               # Enumerations
├── exception/           # Custom exceptions
├── mapper/              # Entity ↔ DTO mappers
├── repository/          # Data access layer
├── service/             # Business logic
│   └── impl/           # Service implementations
└── utils/              # Utility classes
```

### Quy Tắc Đặt File

1. **Một class public duy nhất mỗi file**
2. **Tên file phải khớp với tên class**
3. **Sắp xếp theo mục đích, không phải theo loại entity**

```java
// ✅ ĐÚNG - Grouped by feature
com.fruvia.chat.message.controller.MessageController
com.fruvia.chat.message.service.MessageService
com.fruvia.chat.message.repository.MessageRepository

// ❌ SAI - Grouped by type (quá khó maintain)
com.fruvia.chat.controller.message.MessageController
com.fruvia.chat.controller.user.UserController
```

---

## 3. API Design Rules

### REST Endpoints

#### Cấu Trúc URL
```java
// ✅ ĐÚNG
@RequestMapping("/api/v1/messages")
@GetMapping("/{id}")                           // GET /api/v1/messages/{id}
@GetMapping("/conversation/{conversationId}")  // GET /api/v1/messages/conversation/{id}
@PostMapping                                   // POST /api/v1/messages

// ❌ SAI
@RequestMapping("/api/v1/getMessages")  // Không dùng verbs trong URL
@GetMapping("/message-detail/{id}")     // Không dùng dấu gạch ngang
```

#### HTTP Methods
- **GET**: Lấy dữ liệu (idempotent, không có body)
- **POST**: Tạo resource mới
- **PUT**: Update toàn bộ resource
- **PATCH**: Update một phần resource
- **DELETE**: Xóa resource

#### Response Status Codes
```java
// ✅ ĐÚNG
@PostMapping
public ResponseEntity<MessageResponse> createMessage(@RequestBody MessageRequest request) {
    MessageResponse response = messageService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);  // 201
}

@GetMapping("/{id}")
public ResponseEntity<MessageResponse> getMessage(@PathVariable String id) {
    return messageService.findById(id)
        .map(ResponseEntity::ok)                    // 200
        .orElse(ResponseEntity.notFound().build()); // 404
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
    messageService.delete(id);
    return ResponseEntity.noContent().build();  // 204
}
```

### Request/Response DTOs

```java
// ✅ ĐÚNG - Tách riêng Request và Response
public class MessageRequest {
    @NotBlank(message = "Content cannot be blank")
    private String content;
    
    @NotNull(message = "Conversation ID is required")
    private String conversationId;
    
    // getters, setters
}

public class MessageResponse {
    private String id;
    private String content;
    private String senderId;
    private String senderName;
    private LocalDateTime createdAt;
    private boolean isRead;
    
    // getters, setters
}

// ❌ SAI - Dùng Entity trực tiếp
@PostMapping
public ResponseEntity<Message> createMessage(@RequestBody Message message) { }
```

---

## 4. Error Handling

### Custom Exceptions

```java
// ✅ ĐÚNG - Exceptions cụ thể cho từng case
public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(String messageId) {
        super("Message not found: " + messageId);
    }
}

public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotFound(MessageNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }
}
```

### Error Response Format

```java
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
    
    // constructors, getters, setters
}

// Response JSON:
{
    "status": 404,
    "message": "Message not found: msg123",
    "timestamp": "2026-01-21T14:30:00"
}
```

---

## 5. Security Standards

### Authentication

```java
// ✅ ĐÚNG - Lấy user từ SecurityContext
@PostMapping
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    String userId = JwtUtils.getCurrentUserId();  // Từ JWT token
    MessageResponse response = messageService.sendMessage(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

// ❌ SAI - Nhận userId từ request body (không bảo mật)
@PostMapping
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    String userId = request.getUserId();  // Client có thể giả mạo!
    // ...
}
```

### Authorization

```java
// ✅ ĐÚNG - Verify ownership
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
    String currentUserId = JwtUtils.getCurrentUserId();
    Message message = messageService.findById(id)
        .orElseThrow(() -> new MessageNotFoundException(id));
    
    if (!message.getSender().getId().equals(currentUserId)) {
        throw new UnauthorizedAccessException("You can only delete your own messages");
    }
    
    messageService.delete(id);
    return ResponseEntity.noContent().build();
}
```

### Input Validation

```java
// ✅ ĐÚNG - Validation ở DTO
public class MessageRequest {
    @NotBlank(message = "Content cannot be blank")
    @Size(max = 5000, message = "Message too long")
    private String content;
    
    @NotNull(message = "Conversation ID is required")
    private String conversationId;
}

// Trong Controller
@PostMapping
public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest request) {
    // Validation tự động, throw exception nếu invalid
}
```

### Sanitization

```java
// ✅ ĐÚNG - Sanitize user input để ngăn XSS
String safeContent = ValidationUtils.sanitizeMessage(request.getContent());
message.setContent(safeContent);
```

---

## 6. Database Guidelines

### Entity Design

```java
// ✅ ĐÚNG - Entity hoàn chỉnh
@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, length = 5000)
    private String content;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "is_read")
    private boolean isRead = false;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Repository Queries

```java
// ✅ ĐÚNG - Method names tự động query
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId);
    
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
           "AND m.isRead = false AND m.sender.id != :userId")
    List<Message> findUnreadMessages(@Param("conversationId") String conversationId, 
                                     @Param("userId") String userId);
}

// ❌ SAI - Queries không rõ ràng
List<Message> getData(String id);
List<Message> getStuff(String id, String userId);
```

### Transactions

```java
// ✅ ĐÚNG - Sử dụng @Transactional cho operations phức tạp
@Service
@Transactional
public class MessageService {
    
    @Transactional(readOnly = true)
    public List<Message> getMessages(String conversationId) {
        return messageRepository.findByConversationId(conversationId);
    }
    
    @Transactional
    public Message sendMessage(String userId, MessageRequest request) {
        // Multiple DB operations - need transaction
        Message message = new Message();
        message.setContent(request.getContent());
        message = messageRepository.save(message);
        
        // Update conversation last message
        conversationService.updateLastMessage(request.getConversationId(), message);
        
        return message;
    }
}
```

---

## 7. Testing Requirements

### Unit Tests

```java
// ✅ ĐÚNG - Test business logic
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    
    @Mock
    private MessageRepository messageRepository;
    
    @Mock
    private ConversationRepository conversationRepository;
    
    @InjectMocks
    private MessageService messageService;
    
    @Test
    void sendMessage_WithValidInput_ShouldReturnMessage() {
        // Given
        String userId = "user123";
        MessageRequest request = new MessageRequest();
        request.setContent("Hello");
        request.setConversationId("conv123");
        
        Message expectedMessage = new Message();
        expectedMessage.setId("msg123");
        expectedMessage.setContent("Hello");
        
        when(messageRepository.save(any(Message.class))).thenReturn(expectedMessage);
        
        // When
        Message result = messageService.sendMessage(userId, request);
        
        // Then
        assertNotNull(result);
        assertEquals("Hello", result.getContent());
        verify(messageRepository, times(1)).save(any(Message.class));
    }
}
```

### Integration Tests

```java
// ✅ ĐÚNG - Test API endpoints
@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @WithMockUser(username = "user123")
    void createMessage_WithValidInput_ShouldReturn201() throws Exception {
        MessageRequest request = new MessageRequest();
        request.setContent("Hello World");
        request.setConversationId("conv123");
        
        mockMvc.perform(post("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("Hello World"));
    }
}
```

### Coverage Requirements
- **Controllers**: 80% minimum
- **Services**: 90% minimum  
- **Repositories**: Custom queries phải test
- **Utils**: 100%

---

## 8. Documentation Standards

### JavaDoc cho Public Methods

```java
/**
 * Gửi tin nhắn mới trong conversation.
 *
 * @param userId ID của người gửi (từ JWT)
 * @param request Thông tin tin nhắn cần gửi
 * @return Message đã được tạo và lưu
 * @throws ConversationNotFoundException nếu conversation không tồn tại
 * @throws UnauthorizedAccessException nếu user không phải member của conversation
 */
public Message sendMessage(String userId, MessageRequest request) {
    // Implementation
}
```

### Comments cho Logic Phức Tạp

```java
// ✅ ĐÚNG - Giải thích WHY, không phải WHAT
// Calculate unread count excluding messages sent by current user
// to avoid showing "1 unread" for messages they just sent themselves
long unreadCount = messages.stream()
    .filter(m -> !m.getSender().getId().equals(currentUserId))
    .filter(m -> !m.isRead())
    .count();

// ❌ SAI - Chỉ mô tả code
// Loop through messages and filter
long unreadCount = messages.stream()
    .filter(m -> !m.getSender().getId().equals(currentUserId))
    .filter(m -> !m.isRead())
    .count();
```

---

## 🔍 Code Review Checklist

### Trước khi commit:
- [ ] Code tuân thủ naming conventions
- [ ] Tất cả methods có JavaDoc
- [ ] Unit tests cho business logic mới
- [ ] Không có hardcoded values (dùng application.yaml)
- [ ] Input validation đầy đủ
- [ ] Error handling phù hợp
- [ ] No commented-out code
- [ ] No System.out.println() (dùng logger)

### Security checks:
- [ ] Không expose sensitive data trong responses
- [ ] Authentication được verify
- [ ] Authorization được check cho mọi operations
- [ ] User input được sanitize
- [ ] SQL injection prevention (dùng @Param)

---

## 📚 Resources

- [Spring Boot Best Practices](https://spring.io/guides)
- [RESTful API Design](https://restfulapi.net/)
- [Java Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)

---

**Version**: 1.0  
**Last Updated**: 21/01/2026  
**Maintained By**: Fruvia Development Team
