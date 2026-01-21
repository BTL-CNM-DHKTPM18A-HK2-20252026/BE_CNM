# Backend Development Rules & Guidelines

> 📋 **Coding Standards & Best Practices for Fruvia Backend**  
> 📅 **Last Updated**: January 2026

---

## 📚 Table of Contents
1. [Project Structure Rules](#project-structure-rules)
2. [Naming Conventions](#naming-conventions)
3. [Code Organization](#code-organization)
4. [API Design Rules](#api-design-rules)
5. [Database & Entity Rules](#database--entity-rules)
6. [Security Rules](#security-rules)
7. [Error Handling](#error-handling)
8. [Testing Requirements](#testing-requirements)
9. [Documentation Standards](#documentation-standards)
10. [Git Workflow](#git-workflow)

---

## 1. Project Structure Rules

### ✅ DO

**Package Organization:**
```
iuh.fit/
├── configuration/    # Spring @Configuration classes ONLY
├── controller/       # REST API endpoints ONLY
├── dto/              # Request/Response classes ONLY
├── entity/           # JPA entities ONLY
├── enums/            # Enum types ONLY
├── mapper/           # Entity ↔ DTO converters ONLY
├── repository/       # JPA repositories ONLY
├── service/          # Business logic ONLY
│   └── impl/         # Service implementations
└── utils/            # Stateless utility methods ONLY
```

**Rules:**
- ✅ Each package has ONE specific responsibility
- ✅ Create interface + implementation for services
- ✅ Keep utils stateless (no instance variables)
- ✅ Configuration classes in `configuration/` package only

### ❌ DON'T

- ❌ Mix business logic in controllers
- ❌ Put configuration beans in random packages
- ❌ Create "helper" packages with mixed responsibilities
- ❌ Store state in utility classes

---

## 2. Naming Conventions

### Classes

| Type | Pattern | Example |
|------|---------|---------|
| Controller | `{Resource}Controller` | `UserController` |
| Service Interface | `{Resource}Service` | `UserService` |
| Service Impl | `{Resource}ServiceImpl` | `UserServiceImpl` |
| Repository | `{Entity}Repository` | `UserRepository` |
| Entity | `{Singular}` | `User`, `Message` |
| DTO Request | `{Action}{Resource}Request` | `CreateUserRequest`, `LoginRequest` |
| DTO Response | `{Resource}Response` | `UserResponse`, `AuthResponse` |
| Mapper | `{Entity}Mapper` | `UserMapper` |
| Enum | `{Concept}` | `UserRole`, `MessageType` |
| Exception | `{Concept}Exception` | `UserNotFoundException` |
| Config | `{Feature}Config` | `SecurityConfig`, `CorsConfig` |

### Methods

**Controllers:**
```java
// ✅ Good
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUserById(@PathVariable String id)

@PostMapping
public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request)

@PutMapping("/{id}")
public ResponseEntity<UserResponse> updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request)

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteUser(@PathVariable String id)
```

**Services:**
```java
// ✅ Good naming
UserResponse getUserById(String id);
UserResponse createUser(CreateUserRequest request);
UserResponse updateUser(String id, UpdateUserRequest request);
void deleteUser(String id);
List<UserResponse> getAllUsers();
Page<UserResponse> getUsersWithPagination(Pageable pageable);
boolean existsByUsername(String username);
```

**Repositories:**
```java
// ✅ Use Spring Data JPA conventions
Optional<User> findByUsername(String username);
Optional<User> findByEmail(String email);
boolean existsByUsername(String username);
List<User> findByRole(UserRole role);
@Query("SELECT u FROM User u WHERE u.isActive = true")
List<User> findAllActiveUsers();
```

### Variables

```java
// ✅ Good
private final UserService userService;
private String username;
private List<Message> messages;
private Optional<User> optionalUser;

// ❌ Bad
private final UserService us;
private String usr;
private List<Message> msgs;
private Optional<User> user; // Confusing with User entity
```

### Constants

```java
// ✅ Good
public static final String DEFAULT_PAGE_SIZE = "20";
public static final int MAX_FILE_SIZE_MB = 10;
public static final long TOKEN_EXPIRATION_TIME = 3600000L;

// ❌ Bad
public static final String pageSize = "20";
public static final int MaxFileSize = 10;
```

---

## 3. Code Organization

### Controller Layer

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    // ✅ Keep controllers thin - delegate to service
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
    
    // ✅ Use @Valid for validation
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    // ❌ DON'T put business logic in controller
    @PostMapping("/bad-example")
    public ResponseEntity<UserResponse> badExample(@RequestBody CreateUserRequest request) {
        // ❌ NO business logic here
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateException("Username exists");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        // ... more logic
        userRepository.save(user);
        return ResponseEntity.ok(mapper.toResponse(user));
    }
}
```

### Service Layer

```java
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    
    // ✅ Business logic goes here
    @Override
    public UserResponse createUser(CreateUserRequest request) {
        // Validation
        validateUsername(request.getUsername());
        
        // Business logic
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setActive(true);
        
        // Save
        User savedUser = userRepository.save(user);
        
        // Return DTO
        return userMapper.toResponse(savedUser);
    }
    
    // ✅ Extract validation to private methods
    private void validateUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException("Username already exists: " + username);
        }
    }
}
```

### Repository Layer

```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    // ✅ Use method naming conventions
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    // ✅ Use @Query for complex queries
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.role = :role")
    List<User> findActiveUsersByRole(@Param("role") UserRole role);
    
    // ✅ Use native query when needed
    @Query(value = "SELECT * FROM users WHERE created_at > :date", nativeQuery = true)
    List<User> findUsersCreatedAfter(@Param("date") LocalDateTime date);
}
```

---

## 4. API Design Rules

### REST Endpoints

```java
// ✅ Good API design
GET    /users              // List all users
GET    /users/{id}         // Get specific user
POST   /users              // Create user
PUT    /users/{id}         // Update user (full)
PATCH  /users/{id}         // Update user (partial)
DELETE /users/{id}         // Delete user

// ✅ Nested resources
GET    /users/{userId}/messages           // Get user's messages
POST   /users/{userId}/messages           // Create message for user
GET    /conversations/{id}/messages       // Get messages in conversation

// ✅ Actions on resources
POST   /users/{id}/activate               // Activate user
POST   /users/{id}/deactivate             // Deactivate user
POST   /messages/{id}/mark-read           // Mark message as read

// ❌ Bad API design
GET    /getAllUsers                       // Use GET /users
POST   /createUser                        // Use POST /users
GET    /user/{id}                         // Use plural: /users/{id}
POST   /users/delete/{id}                 // Use DELETE /users/{id}
```

### HTTP Status Codes

```java
// ✅ Use appropriate status codes
return ResponseEntity.ok(data);                          // 200 OK
return ResponseEntity.status(HttpStatus.CREATED)         // 201 Created
    .body(data);
return ResponseEntity.noContent().build();               // 204 No Content
return ResponseEntity.badRequest()                       // 400 Bad Request
    .body(error);
return ResponseEntity.status(HttpStatus.UNAUTHORIZED)    // 401 Unauthorized
    .build();
return ResponseEntity.status(HttpStatus.FORBIDDEN)       // 403 Forbidden
    .build();
return ResponseEntity.notFound().build();                // 404 Not Found
return ResponseEntity.status(HttpStatus.CONFLICT)        // 409 Conflict
    .body(error);
```

### Request/Response DTOs

```java
// ✅ Separate DTOs for different operations
@Data
public class CreateUserRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 20)
    private String username;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}

@Data
public class UpdateUserRequest {
    @Size(max = 100)
    private String fullName;
    
    @Size(max = 500)
    private String bio;
    
    private String avatarUrl;
}

@Data
@Builder
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private LocalDateTime createdAt;
    // ❌ NEVER include password or sensitive data
}
```

---

## 5. Database & Entity Rules

### Entity Design

```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    // ✅ Use UUID for IDs
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    // ✅ Add constraints at DB level
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    // ✅ Use appropriate column names
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    // ✅ Use enums with @Enumerated
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;
    
    // ✅ Audit fields
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // ✅ Soft delete
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // ✅ Use appropriate fetch types
    @OneToMany(mappedBy = "sender", fetch = FetchType.LAZY)
    private List<Message> sentMessages;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;
}
```

### Migration Strategy

```java
// ✅ Use Flyway or Liquibase for migrations
// src/main/resources/db/migration/V1__create_users_table.sql

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
```

---

## 6. Security Rules

### Authentication

```java
// ✅ Always use JwtUtils to get current user
@PostMapping("/messages")
public ResponseEntity<MessageResponse> sendMessage(@RequestBody MessageRequest request) {
    String userId = JwtUtils.getCurrentUserId();
    return ResponseEntity.ok(messageService.create(userId, request));
}

// ❌ NEVER trust user ID from request body
@PostMapping("/messages/bad")
public ResponseEntity<MessageResponse> badExample(@RequestBody MessageRequest request) {
    // ❌ User can fake their ID!
    return ResponseEntity.ok(messageService.create(request.getUserId(), request));
}
```

### Authorization

```java
// ✅ Check ownership before operations
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
    String currentUserId = JwtUtils.getCurrentUserId();
    messageService.deleteMessage(id, currentUserId); // Will check ownership
    return ResponseEntity.noContent().build();
}

// ✅ Use @PreAuthorize for role-based access
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteAnyMessage(@PathVariable String id) {
    messageService.deleteMessageByAdmin(id);
    return ResponseEntity.noContent().build();
}
```

### Password Handling

```java
// ✅ Always hash passwords
String hashedPassword = passwordEncoder.encode(plainPassword);
user.setPasswordHash(hashedPassword);

// ✅ Verify passwords
boolean matches = passwordEncoder.matches(plainPassword, user.getPasswordHash());

// ❌ NEVER store plain passwords
user.setPassword(request.getPassword()); // ❌ WRONG!
```

### Input Validation

```java
// ✅ Validate and sanitize user input
@PostMapping("/messages")
public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest request) {
    String safeContent = ValidationUtils.sanitizeMessage(request.getContent());
    request.setContent(safeContent);
    return ResponseEntity.ok(messageService.create(request));
}

// ✅ Use Bean Validation
@Data
public class MessageRequest {
    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 5000, message = "Content must be 1-5000 characters")
    private String content;
}
```

---

## 7. Error Handling

### Custom Exceptions

```java
// ✅ Create specific exception classes
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String userId) {
        super("User not found with ID: " + userId);
    }
}

public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String message) {
        super(message);
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .message("Validation failed")
            .errors(errors)
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.badRequest().body(error);
    }
}
```

---

## 8. Testing Requirements

### Unit Tests

```java
@SpringBootTest
class UserServiceTest {
    
    @MockBean
    private UserRepository userRepository;
    
    @MockBean
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private UserService userService;
    
    @Test
    void testCreateUser_Success() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        
        // When
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        
        // Then
        UserResponse response = userService.createUser(request);
        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
    }
    
    @Test
    void testCreateUser_DuplicateUsername() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("existing");
        
        // When
        when(userRepository.existsByUsername("existing")).thenReturn(true);
        
        // Then
        assertThrows(DuplicateUserException.class, 
            () -> userService.createUser(request));
    }
}
```

---

## 9. Documentation Standards

### JavaDoc

```java
/**
 * Service for managing user operations.
 * Handles user creation, updates, and retrieval.
 *
 * @author Fruvia Team
 * @since 1.0.0
 */
@Service
public class UserServiceImpl implements UserService {
    
    /**
     * Creates a new user with the provided information.
     * Username and email must be unique.
     *
     * @param request the user creation request containing username, email, and password
     * @return UserResponse containing the created user's information
     * @throws DuplicateUserException if username or email already exists
     * @throws IllegalArgumentException if request contains invalid data
     */
    @Override
    public UserResponse createUser(CreateUserRequest request) {
        // Implementation
    }
}
```

### API Documentation (Swagger)

```java
@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "APIs for user operations")
public class UserController {
    
    @Operation(
        summary = "Get user by ID",
        description = "Retrieves user information by user ID. Requires authentication."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID") @PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
```

---

## 10. Git Workflow

### Branch Naming

```bash
# ✅ Good branch names
feature/user-registration
feature/chat-messaging
bugfix/login-error
hotfix/security-patch
refactor/service-layer
docs/api-documentation

# ❌ Bad branch names
dev
test
my-branch
fix
```

### Commit Messages

```bash
# ✅ Good commit messages
feat: add user registration endpoint
fix: resolve JWT token expiration issue
refactor: extract validation logic to service layer
docs: update API documentation for user endpoints
test: add unit tests for UserService
chore: update dependencies to latest versions

# ❌ Bad commit messages
update
fix bug
changes
wip
```

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `refactor`: Code refactoring
- `test`: Tests
- `chore`: Maintenance

---

## ⚠️ Common Mistakes to Avoid

### ❌ DON'T

1. **Don't expose entities directly**
   ```java
   // ❌ Bad
   @GetMapping("/{id}")
   public User getUser(@PathVariable String id) {
       return userRepository.findById(id).orElseThrow();
   }
   ```

2. **Don't put business logic in controllers**
   ```java
   // ❌ Bad
   @PostMapping
   public User createUser(@RequestBody CreateUserRequest request) {
       if (userRepository.existsByUsername(request.getUsername())) {
           throw new DuplicateException();
       }
       // ... more logic
   }
   ```

3. **Don't ignore transactions**
   ```java
   // ❌ Bad - No @Transactional
   public void updateUser(String id, UpdateUserRequest request) {
       User user = userRepository.findById(id).orElseThrow();
       user.setFullName(request.getFullName());
       // Changes might not be saved!
   }
   ```

4. **Don't hardcode values**
   ```java
   // ❌ Bad
   String secret = "my-secret-key-12345";
   int maxFileSize = 10485760;
   
   // ✅ Good
   @Value("${jwt.signer-key}")
   private String secret;
   
   @Value("${file.max-size}")
   private int maxFileSize;
   ```

---

## 📚 References

- [Code Structure](./CODE_STRUCTURE.md)
- [Security Architecture](./SECURITY_ARCHITECTURE.md)
- [Utils Documentation](./UTILS_DOCUMENTATION.md)
- [Spring Boot Best Practices](https://spring.io/guides)

---

**Last Updated**: January 21, 2026  
**Maintainer**: Fruvia Development Team
