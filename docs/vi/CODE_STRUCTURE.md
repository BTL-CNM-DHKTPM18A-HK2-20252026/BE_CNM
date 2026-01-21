# Cấu Trúc Code Fruvia Backend

> 📂 **Kiến Trúc Dự Án & Tổ Chức Package**  
> 📅 **Cập nhật lần cuối**: Tháng 1/2026

---

## 📁 Cấu Trúc Dự Án

```
fruvia/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── iuh/fit/
│   │   │       ├── configuration/      # Cấu hình Spring
│   │   │       ├── controller/         # REST API controllers
│   │   │       ├── dto/                # Data Transfer Objects
│   │   │       ├── entity/             # JPA entities (models database)
│   │   │       ├── enums/              # Các kiểu enum
│   │   │       ├── mapper/             # Chuyển đổi DTO ↔ Entity
│   │   │       ├── repository/         # JPA repositories
│   │   │       ├── service/            # Business logic
│   │   │       └── utils/              # Các class tiện ích
│   │   └── resources/
│   │       ├── application.yaml        # Cấu hình chính
│   │       ├── static/                 # Tài nguyên tĩnh
│   │       └── templates/              # Email templates, etc.
│   └── test/
│       └── java/                       # Unit & integration tests
├── docs/                               # Tài liệu (folder này)
├── test_api/                           # Script test API (.http files)
├── pom.xml                             # Maven dependencies
└── README.md                           # Tổng quan dự án
```

---

## 📦 Chi Tiết Package

### 1. `configuration/` 🔧

**Mục đích**: Cấu hình Spring Bean

| File | Mục Đích |
|------|---------|
| `SecurityConfig.java` | Spring Security, JWT, authentication |
| `CorsConfig.java` | Cấu hình CORS cho web/mobile |
| `CloudinaryConfig.java` | Thiết lập Cloudinary SDK để upload file |

**Trách nhiệm chính:**
- Định nghĩa các `@Bean` components
- Cấu hình quy tắc bảo mật
- Thiết lập tích hợp third-party

---

### 2. `controller/` 🎮

**Mục đích**: REST API endpoints (xử lý HTTP requests)

**Ví dụ cấu trúc:**
```
controller/
├── AuthController.java           # /auth/** (login, đăng ký, refresh)
├── UserController.java           # /users/** (CRUD operations)
├── MessageController.java        # /messages/** (tin nhắn chat)
├── ConversationController.java   # /conversations/** (phòng chat)
└── FileController.java           # /files/** (upload/download)
```

**Trách nhiệm:**
- Xử lý HTTP requests
- Validate đầu vào (`@Valid`)
- Gọi service layer
- Trả về HTTP responses

**Ví dụ:**
```java
@RestController
@RequestMapping("/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
```

---

### 3. `dto/` 📦

**Mục đích**: Data Transfer Objects (models request/response API)

**Quy ước đặt tên:**
- `XxxRequest.java` - Đầu vào từ client
- `XxxResponse.java` - Đầu ra cho client
- `XxxDTO.java` - Transfer object chung

**Ví dụ cấu trúc:**
```
dto/
├── request/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   └── MessageRequest.java
└── response/
    ├── AuthResponse.java
    ├── UserResponse.java
    └── MessageResponse.java
```

**Ví dụ:**
```java
@Data
public class LoginRequest {
    @NotBlank(message = "Username là bắt buộc")
    private String username;
    
    @NotBlank(message = "Password là bắt buộc")
    private String password;
}
```

---

### 4. `entity/` 🗄️

**Mục đích**: JPA entities (models bảng database)

**Annotations:**
- `@Entity` - Đánh dấu là bảng database
- `@Table(name = "...")` - Tên bảng tùy chỉnh
- `@Id` - Primary key
- `@GeneratedValue` - Tự động tăng
- `@Column` - Thuộc tính cột
- `@ManyToOne`, `@OneToMany` - Quan hệ

**Ví dụ cấu trúc:**
```
entity/
├── User.java
├── Message.java
├── Conversation.java
├── FileMetadata.java
└── UserRole.java
```

**Ví dụ:**
```java
@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @OneToMany(mappedBy = "sender")
    private List<Message> sentMessages;
}
```

---

### 5. `enums/` 🏷️

**Mục đích**: Các kiểu enum cho tập giá trị cố định

**Ví dụ cấu trúc:**
```
enums/
├── UserRole.java              # USER, ADMIN, MODERATOR
├── MessageType.java           # TEXT, IMAGE, FILE, VOICE
├── ConversationStatus.java    # ACTIVE, ARCHIVED, DELETED
└── FileUploadStatus.java      # PENDING, COMPLETED, FAILED
```

**Ví dụ:**
```java
public enum MessageType {
    TEXT,
    IMAGE,
    FILE,
    VOICE,
    VIDEO,
    STICKER,
    LOCATION
}
```

---

### 6. `mapper/` 🔄

**Mục đích**: Chuyển đổi giữa Entity ↔ DTO

**Tại sao cần?**
- Entities chứa chi tiết database (IDs, timestamps, relationships)
- DTOs chỉ chứa dữ liệu cần thiết cho API
- Tách biệt concerns

**Ví dụ cấu trúc:**
```
mapper/
├── UserMapper.java
├── MessageMapper.java
└── ConversationMapper.java
```

**Ví dụ:**
```java
@Component
public class UserMapper {
    
    public UserResponse toResponse(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .createdAt(user.getCreatedAt())
            .build();
        // Lưu ý: KHÔNG bao gồm password trong response
    }
    
    public User toEntity(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        // Password sẽ được hash trong service layer
        return user;
    }
}
```

---

### 7. `repository/` 💾

**Mục đích**: Tầng truy cập database (JPA repositories)

**Kế thừa**: `JpaRepository<Entity, ID>`

**Ví dụ cấu trúc:**
```
repository/
├── UserRepository.java
├── MessageRepository.java
├── ConversationRepository.java
└── FileMetadataRepository.java
```

**Ví dụ:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActiveUsers();
}
```

**Methods có sẵn:**
- `save(entity)` - Insert hoặc update
- `findById(id)` - Lấy theo ID
- `findAll()` - Lấy tất cả
- `deleteById(id)` - Xóa theo ID
- `count()` - Đếm số bản ghi

---

### 8. `service/` 💼

**Mục đích**: Tầng business logic

**Trách nhiệm:**
- Triển khai business rules
- Validate dữ liệu
- Gọi repositories
- Xử lý transactions
- Transform dữ liệu (sử dụng mappers)

**Ví dụ cấu trúc:**
```
service/
├── impl/
│   ├── UserServiceImpl.java
│   ├── MessageServiceImpl.java
│   └── ConversationServiceImpl.java
├── UserService.java             # Interface
├── MessageService.java          # Interface
└── ConversationService.java     # Interface
```

**Ví dụ:**
```java
@Service
@Transactional
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public UserResponse createUser(RegisterRequest request) {
        // Validate
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateException("Username đã tồn tại");
        }
        
        // Tạo entity
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        
        // Lưu
        User savedUser = userRepository.save(user);
        
        // Trả về DTO
        return userMapper.toResponse(savedUser);
    }
}
```

---

### 9. `utils/` 🛠️

**Mục đích**: Các phương thức tiện ích tái sử dụng (stateless, static)

**Files:**
- `DateTimeUtils.java` - Format ngày/giờ
- `JwtUtils.java` - Trích xuất JWT token
- `ValidationUtils.java` - Validate đầu vào
- `MessageUtils.java` - Xử lý tin nhắn
- `FileUtils.java` - Xử lý file

**Xem**: [Tài Liệu Utils](./UTILS_DOCUMENTATION.md)

---

## 🔄 Luồng Request

```
┌──────────────┐
│   Client     │ (Web/Mobile)
└──────┬───────┘
       │ HTTP Request (JSON)
       │
       ▼
┌──────────────────────┐
│  Controller          │ (@RestController)
│  - Validate @Valid   │
│  - Extract params    │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│  Service             │ (@Service)
│  - Business logic    │
│  - Validation        │
│  - Transactions      │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│  Repository          │ (@Repository)
│  - Database queries  │
│  - CRUD operations   │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│  Database            │ (PostgreSQL/MySQL)
│  - Lưu trữ dữ liệu   │
└──────────────────────┘
```

**Luồng trả về:**
```
Database → Repository → Service → Mapper → Controller → Client
   Entity  →  Entity  →  Entity  →  DTO   →   JSON
```

---

## 📝 Quy Ước Đặt Tên

### Classes
- **Controllers**: `{Resource}Controller.java` (vd: `UserController`)
- **Services**: `{Resource}Service.java`, `{Resource}ServiceImpl.java`
- **Repositories**: `{Entity}Repository.java`
- **Entities**: `{Singular}.java` (vd: `User`, `Message`)
- **DTOs**: `{Resource}Request.java`, `{Resource}Response.java`
- **Mappers**: `{Entity}Mapper.java`

### Methods
- **GET**: `get{Resource}`, `find{Resource}`, `list{Resources}`
- **POST**: `create{Resource}`, `add{Resource}`
- **PUT**: `update{Resource}`, `modify{Resource}`
- **DELETE**: `delete{Resource}`, `remove{Resource}`

### API Endpoints
- **Collection**: `/users`, `/messages`
- **Single resource**: `/users/{id}`, `/messages/{id}`
- **Nested**: `/users/{userId}/messages`
- **Actions**: `/users/{id}/activate`, `/messages/{id}/read`

---

## 🎯 Best Practices

### 1. **Tách biệt Concerns**
- Controllers chỉ xử lý HTTP
- Services chứa business logic
- Repositories xử lý truy cập dữ liệu
- Utils là helper stateless

### 2. **Không Expose Entities Trực Tiếp**
```java
// ❌ Sai
@GetMapping("/{id}")
public User getUser(@PathVariable String id) {
    return userRepository.findById(id).orElseThrow();
}

// ✅ Đúng
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable String id) {
    User user = userService.getUserById(id);
    return userMapper.toResponse(user);
}
```

### 3. **Sử Dụng Interfaces cho Services**
```java
// ✅ Tốt
public interface UserService {
    UserResponse getUserById(String id);
}

@Service
public class UserServiceImpl implements UserService {
    // Implementation
}
```

### 4. **Validate ở Nhiều Tầng**
- **Controller**: `@Valid`, `@NotBlank`, `@Email`
- **Service**: Validate business rules
- **Database**: `@Column(nullable = false, unique = true)`

---

## 🔗 Tài Liệu Liên Quan
- [Tài Liệu Utils](./UTILS_DOCUMENTATION.md)
- [Kiến Trúc Bảo Mật](./SECURITY_ARCHITECTURE.md)
- [Cấu Hình CORS](./CORS_CONFIG.md)

---

**Cập nhật lần cuối**: 21/01/2026  
**Người duy trì**: Fruvia Development Team
