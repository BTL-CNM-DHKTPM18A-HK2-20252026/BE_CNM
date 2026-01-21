# Fruvia Backend Code Structure

> 📂 **Project Architecture & Package Organization**  
> 📅 **Last Updated**: January 2026

---

## 📁 Project Structure

```
fruvia/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── iuh/fit/
│   │   │       ├── configuration/      # Spring configurations
│   │   │       ├── controller/         # REST API controllers
│   │   │       ├── dto/                # Data Transfer Objects
│   │   │       ├── entity/             # JPA entities (database models)
│   │   │       ├── enums/              # Enum types
│   │   │       ├── mapper/             # DTO ↔ Entity mappers
│   │   │       ├── repository/         # JPA repositories
│   │   │       ├── service/            # Business logic
│   │   │       └── utils/              # Utility classes
│   │   └── resources/
│   │       ├── application.yaml        # Main configuration
│   │       ├── static/                 # Static resources
│   │       └── templates/              # Email templates, etc.
│   └── test/
│       └── java/                       # Unit & integration tests
├── docs/                               # Documentation (this folder)
├── test_api/                           # API test scripts (.http files)
├── pom.xml                             # Maven dependencies
└── README.md                           # Project overview
```

---

## 📦 Package Details

### 1. `configuration/` 🔧

**Purpose**: Spring Bean configurations

| File | Purpose |
|------|---------|
| `SecurityConfig.java` | Spring Security, JWT, authentication |
| `CorsConfig.java` | CORS configuration for web/mobile |
| `CloudinaryConfig.java` | Cloudinary SDK setup for file uploads |

**Key Responsibilities:**
- Define `@Bean` components
- Configure security rules
- Set up third-party integrations

---

### 2. `controller/` 🎮

**Purpose**: REST API endpoints (HTTP request handlers)

**Example Structure:**
```
controller/
├── AuthController.java           # /auth/** (login, register, refresh)
├── UserController.java           # /users/** (CRUD operations)
├── MessageController.java        # /messages/** (chat messages)
├── ConversationController.java   # /conversations/** (chat rooms)
└── FileController.java           # /files/** (upload/download)
```

**Responsibilities:**
- Handle HTTP requests
- Validate input (`@Valid`)
- Call service layer
- Return HTTP responses

**Example:**
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

**Purpose**: Data Transfer Objects (API request/response models)

**Naming Convention:**
- `XxxRequest.java` - Input from client
- `XxxResponse.java` - Output to client
- `XxxDTO.java` - General transfer object

**Example Structure:**
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

**Example:**
```java
@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
}
```

---

### 4. `entity/` 🗄️

**Purpose**: JPA entities (database table models)

**Annotations:**
- `@Entity` - Marks as database table
- `@Table(name = "...")` - Custom table name
- `@Id` - Primary key
- `@GeneratedValue` - Auto-increment
- `@Column` - Column properties
- `@ManyToOne`, `@OneToMany` - Relationships

**Example Structure:**
```
entity/
├── User.java
├── Message.java
├── Conversation.java
├── FileMetadata.java
└── UserRole.java
```

**Example:**
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

**Purpose**: Enumeration types for fixed value sets

**Example Structure:**
```
enums/
├── UserRole.java              # USER, ADMIN, MODERATOR
├── MessageType.java           # TEXT, IMAGE, FILE, VOICE
├── ConversationStatus.java    # ACTIVE, ARCHIVED, DELETED
└── FileUploadStatus.java      # PENDING, COMPLETED, FAILED
```

**Example:**
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

**Purpose**: Convert between Entity ↔ DTO

**Why needed?**
- Entities contain database details (IDs, timestamps, relationships)
- DTOs contain only data needed for API
- Separation of concerns

**Example Structure:**
```
mapper/
├── UserMapper.java
├── MessageMapper.java
└── ConversationMapper.java
```

**Example:**
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
        // Note: Password NOT included in response
    }
    
    public User toEntity(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        // Password will be hashed in service layer
        return user;
    }
}
```

---

### 7. `repository/` 💾

**Purpose**: Database access layer (JPA repositories)

**Extends**: `JpaRepository<Entity, ID>`

**Example Structure:**
```
repository/
├── UserRepository.java
├── MessageRepository.java
├── ConversationRepository.java
└── FileMetadataRepository.java
```

**Example:**
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

**Built-in Methods:**
- `save(entity)` - Insert or update
- `findById(id)` - Get by ID
- `findAll()` - Get all
- `deleteById(id)` - Delete by ID
- `count()` - Count records

---

### 8. `service/` 💼

**Purpose**: Business logic layer

**Responsibilities:**
- Implement business rules
- Validate data
- Call repositories
- Handle transactions
- Transform data (using mappers)

**Example Structure:**
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

**Example:**
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
            throw new DuplicateException("Username already exists");
        }
        
        // Create entity
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        
        // Save
        User savedUser = userRepository.save(user);
        
        // Return DTO
        return userMapper.toResponse(savedUser);
    }
}
```

---

### 9. `utils/` 🛠️

**Purpose**: Reusable utility methods (stateless, static)

**Files:**
- `DateTimeUtils.java` - Date/time formatting
- `JwtUtils.java` - JWT token extraction
- `ValidationUtils.java` - Input validation
- `MessageUtils.java` - Message processing
- `FileUtils.java` - File handling

**See**: [Utils Documentation](./UTILS_DOCUMENTATION.md)

---

## 🔄 Request Flow

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
│  - Store data        │
└──────────────────────┘
```

**Return Flow:**
```
Database → Repository → Service → Mapper → Controller → Client
   Entity  →  Entity  →  Entity  →  DTO   →   JSON
```

---

## 📝 Naming Conventions

### Classes
- **Controllers**: `{Resource}Controller.java` (e.g., `UserController`)
- **Services**: `{Resource}Service.java`, `{Resource}ServiceImpl.java`
- **Repositories**: `{Entity}Repository.java`
- **Entities**: `{Singular}.java` (e.g., `User`, `Message`)
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

### 1. **Separation of Concerns**
- Controllers handle HTTP only
- Services contain business logic
- Repositories handle data access
- Utils are stateless helpers

### 2. **Don't Expose Entities Directly**
```java
// ❌ Bad
@GetMapping("/{id}")
public User getUser(@PathVariable String id) {
    return userRepository.findById(id).orElseThrow();
}

// ✅ Good
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable String id) {
    User user = userService.getUserById(id);
    return userMapper.toResponse(user);
}
```

### 3. **Use Interfaces for Services**
```java
// ✅ Good
public interface UserService {
    UserResponse getUserById(String id);
}

@Service
public class UserServiceImpl implements UserService {
    // Implementation
}
```

### 4. **Validate in Multiple Layers**
- **Controller**: `@Valid`, `@NotBlank`, `@Email`
- **Service**: Business rule validation
- **Database**: `@Column(nullable = false, unique = true)`

---

## 🔗 Related Documentation
- [Utils Documentation](./UTILS_DOCUMENTATION.md)
- [Security Architecture](./SECURITY_ARCHITECTURE.md)
- [CORS Configuration](./CORS_CONFIG.md)

---

**Last Updated**: January 21, 2026  
**Maintainer**: Fruvia Development Team
