# 📊 BÁO CÁO KIỂM TRA CODE - FRUVIA BACKEND

**Ngày kiểm tra**: 21/01/2026  
**Người kiểm tra**: GitHub Copilot  
**Status**: ⚠️ **CHƯA PRODUCTION-READY**

---

## 🔴 VẤN ĐỀ NGHIÊM TRỌNG (Priority 0 - SỬA NGAY)

### 1. AuthenticationServiceImpl - Chỉ Có Template Code

**File**: `src/main/java/iuh/fit/service/auth/AuthenticationServiceImpl.java`  
**Dòng**: 37, 48, 88, 116

**Vấn đề**:
```java
// TODO: Inject necessary repositories here (line 37)
// UserRepository userRepository;
// PasswordEncoder passwordEncoder;
// RedisTokenService redisTokenService;

// TODO: Implement authentication logic (line 48)
// 1. Find user by username or email
// 2. Verify password
// 3. Generate JWT token

// TODO: Check Redis blacklist if implemented (line 88)
// TODO: Implement token blacklisting with Redis (line 116)
```

**Service này đang trả dummy data**:
```java
String accessToken = generateToken("user-id", "username", "ROLE_USER", 30, ChronoUnit.DAYS);
```

**Cần làm**:
- [ ] Inject `UserAuthRepository` (đã có sẵn trong project)
- [ ] Inject `PasswordEncoder` từ SecurityConfig
- [ ] Implement logic tìm user theo username/email
- [ ] Verify password với BCrypt
- [ ] Trả về user thật, không phải dummy data
- [ ] (Optional) Implement Redis token blacklist cho logout

**Ước tính**: 3-4 giờ

---

### 2. FileUploadController - Hardcoded User ID

**File**: `src/main/java/iuh/fit/controller/FileUploadController.java`  
**Dòng**: 72, 110, 156, 180, 206 (5 chỗ)

**Vấn đề**:
```java
// TODO: Get userId from JWT token
String userId = "temp-user-id"; // ❌ HARDCODED
```

**Ảnh hưởng**: 
- Tất cả file uploads đều gán cho cùng 1 user giả
- Không biết ai upload file
- Không thể phân quyền delete/update

**Giải pháp**:
```java
// Tạo method helper trong controller:
private String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
        return jwt.getSubject(); // hoặc jwt.getClaim("userId")
    }
    throw new UnauthorizedException("User not authenticated");
}

// Thay thế tất cả 5 chỗ:
String userId = getCurrentUserId();
```

**Ước tính**: 30 phút

---

### 3. UserController.getCurrentUser() - Trả Fake Data

**File**: `src/main/java/iuh/fit/controller/UserController.java`  
**Dòng**: 99

**Vấn đề**:
```java
// TODO: Get userId from JWT token in SecurityContext
return ResponseEntity.ok(UserResponse.builder()
        .userId("current-user-id")  // ❌ FAKE
        .email("user@example.com")  // ❌ FAKE
        .displayName("Current User") // ❌ FAKE
        .build());
```

**Giải pháp**:
```java
@GetMapping("/me")
public ResponseEntity<UserResponse> getCurrentUser() {
    String userId = getCurrentUserId(); // Extract từ JWT
    UserResponse response = userService.getUserById(userId);
    return ResponseEntity.ok(response);
}
```

**Note**: `UserService.getUserById()` đã có sẵn trong project.

**Ước tính**: 15 phút

---

### 4. SecurityConfig - File Endpoints Public

**File**: `src/main/java/iuh/fit/configuration/SecurityConfig.java`  
**Dòng**: 43

**Vấn đề**:
```java
// Upload & view files (TODO: Add auth for POST/DELETE)
.requestMatchers("/files/**").permitAll() // ⚠️ AI CŨNG UPLOAD/DELETE ĐƯỢC
```

**Rủi ro bảo mật**:
- Ai cũng upload files được → Spam, malware
- Ai cũng delete files được → Mất dữ liệu
- Không audit trail

**Giải pháp**:
```java
// Cho phép GET public (xem file), nhưng POST/DELETE cần auth
.requestMatchers(HttpMethod.GET, "/files/**").permitAll()
.requestMatchers(HttpMethod.POST, "/files/**").authenticated()
.requestMatchers(HttpMethod.PUT, "/files/**").authenticated()
.requestMatchers(HttpMethod.DELETE, "/files/**").authenticated()
```

**Ước tính**: 10 phút

---

## 🟡 VẤN ĐỀ TRUNG BÌNH (Priority 1)

### 5. Wildcard Imports

**Files**:
- `dto/request/auth/AuthenticationRequest.java`
- `dto/request/auth/IntrospectRequest.java`
- `dto/request/auth/LogoutRequest.java`
- `dto/response/auth/AuthenticationResponse.java`
- `dto/response/auth/IntrospectResponse.java`
- `service/auth/AuthenticationServiceImpl.java`

**Vấn đề**:
```java
import lombok.*;  // ❌ SAI
import com.nimbusds.jose.*;  // ❌ SAI
```

**Sửa lại**:
```java
// Lombok
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

// Nimbusds
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
```

**Cách sửa nhanh trong IntelliJ IDEA**:
1. Settings → Editor → Code Style → Java → Imports
2. Set "Class count to use import with '*'" = 999
3. Code → Optimize Imports (Ctrl+Alt+O)

**Ước tính**: 15 phút

---

## 🟢 VẤN ĐỀ NHỎ (Priority 2 - Tối ưu thêm)

### 6. Response DTOs Nên Immutable

**Files**:
- `dto/response/auth/AuthenticationResponse.java`
- `dto/response/auth/IntrospectResponse.java`
- Và các Response DTOs khác

**Hiện tại**:
```java
@Data              // Tạo setters → mutable
@NoArgsConstructor
@AllArgsConstructor
@Builder
```

**Nên đổi thành**:
```java
@Getter            // Chỉ getters → immutable
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
```

**Lý do**: Response objects không nên thay đổi sau khi tạo.

**Ước tính**: 20 phút

---

## 📊 TỔNG KẾT

### Thống Kê Vấn Đề

| Mức độ | Số lượng | Thời gian sửa | Ưu tiên |
|--------|----------|---------------|---------|
| 🔴 Critical | 4 vấn đề | 4-5 giờ | **P0** |
| 🟡 Medium | 1 vấn đề | 15 phút | P1 |
| 🟢 Low | 1 vấn đề | 20 phút | P2 |
| **TỔNG** | **6 vấn đề** | **~5-6 giờ** | - |

### Chi Tiết Vấn đề P0

| # | Vấn đề | File | Dòng | Thời gian |
|---|--------|------|------|-----------|
| 1 | AuthenticationServiceImpl chưa implement | AuthenticationServiceImpl.java | 37, 48, 88, 116 | 3-4h |
| 2 | Hardcoded user ID | FileUploadController.java | 72, 110, 156, 180, 206 | 30m |
| 3 | Fake user response | UserController.java | 99 | 15m |
| 4 | File endpoints public | SecurityConfig.java | 43 | 10m |

---

## 🎯 ROADMAP SỬA LỖI

### Tuần 1 (Priority 0)

**Ngày 1-2: Authentication Core (3-4h)**
- [ ] Implement AuthenticationServiceImpl.authenticate()
  - Inject UserAuthRepository
  - Inject PasswordEncoder
  - Find user by username/email
  - Verify password
  - Generate real JWT token
- [ ] Implement AuthenticationServiceImpl.introspect()
  - Verify token signature
  - Check expiration
- [ ] Implement AuthenticationServiceImpl.logout()
  - Token blacklist (optional)

**Ngày 3: JWT Extraction (1h)**
- [ ] Tạo JwtUtils.getCurrentUserId() helper
- [ ] Fix FileUploadController (5 chỗ)
- [ ] Fix UserController.getCurrentUser()
- [ ] Test các endpoints với JWT thật

**Ngày 4: Security Fix (10m)**
- [ ] Fix SecurityConfig - authenticate POST/DELETE /files/**
- [ ] Test authorization

### Tuần 2 (Priority 1-2)

**Ngày 5: Code Quality (35m)**
- [ ] Remove wildcard imports (6 files)
- [ ] Make Response DTOs immutable (5 files)
- [ ] Run full test suite

---

## 🔍 CHECKLIST TRƯỚC KHI DEPLOY

### Authentication ✅/❌
- [ ] Login trả về JWT token thật (không phải dummy)
- [ ] JWT token có chứa userId, username, roles
- [ ] Token expiration hoạt động đúng
- [ ] Logout blacklist token (optional)
- [ ] Refresh token (optional)

### Authorization ✅/❌
- [ ] Tất cả endpoints đều extract userId từ JWT
- [ ] File upload gán đúng userId
- [ ] Không còn hardcoded "temp-user-id"
- [ ] GET /users/me trả về user thật

### Security ✅/❌
- [ ] POST/PUT/DELETE /files/** require authentication
- [ ] CORS config đúng
- [ ] Password được hash với BCrypt
- [ ] Không log sensitive data (password, token)

### Code Quality ✅/❌
- [ ] Không còn wildcard imports
- [ ] Không còn TODO comments
- [ ] Response DTOs immutable
- [ ] Tất cả service methods có unit tests

---

## 📚 TÀI LIỆU THAM KHẢO

### Code Đã Có Sẵn (Dùng được luôn)

1. **UserAuthRepository** - `repository/UserAuthRepository.java`
   ```java
   Optional<UserAuth> findByEmail(String email);
   Optional<UserAuth> findByPhoneNumber(String phoneNumber);
   boolean existsByEmail(String email);
   ```

2. **PasswordEncoder** - Đã config trong `SecurityConfig.java`
   ```java
   @Bean
   public PasswordEncoder passwordEncoder() {
       return new BCryptPasswordEncoder();
   }
   ```

3. **UserService** - `service/user/UserServiceImpl.java`
   ```java
   UserResponse getUserById(String userId);
   UserResponse register(RegisterRequest request);
   ```

### Các Utils Có Sẵn

- `utils/JwtUtils.java` - Extract info từ JWT
- `utils/ValidationUtils.java` - Validate input
- `utils/DateTimeUtils.java` - Format datetime

### Documentation

- [CODE_STRUCTURE.md](./CODE_STRUCTURE.md) - Cấu trúc project
- [RULE_BACKEND.md](./RULE_BACKEND.md) - Coding conventions
- [SECURITY_ARCHITECTURE.md](./SECURITY_ARCHITECTURE.md) - Security design

---

## 💡 GỢI Ý IMPLEMENT

### 1. AuthenticationServiceImpl.authenticate()

```java
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthenticationServiceImpl implements AuthenticationService {

    UserAuthRepository userAuthRepository;
    PasswordEncoder passwordEncoder;
    
    @NonFinal
    @Value("${jwt.signer-key}")
    protected String SIGNER_KEY;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) throws JOSEException {
        log.info("Authenticating user: {}", request.getUsername());
        
        // 1. Find user by email or phone
        UserAuth user = userAuthRepository.findByEmail(request.getUsername())
                .or(() -> userAuthRepository.findByPhoneNumber(request.getUsername()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        
        // 2. Verify password
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!authenticated) {
            throw new UnauthorizedException("Invalid credentials");
        }
        
        // 3. Check account status
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("Account is not active");
        }
        
        // 4. Generate real JWT token
        String accessToken = generateToken(
            user.getUserId(), 
            user.getEmail(), 
            "ROLE_USER",  // Hoặc lấy từ user.getRole()
            30, 
            ChronoUnit.DAYS
        );
        
        log.info("User {} authenticated successfully", user.getUserId());
        
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .expiresIn(30 * 24 * 3600) // 30 days in seconds
                .tokenType("Bearer")
                .build();
    }
}
```

### 2. JWT Extraction Helper

```java
// Trong các Controllers
private String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
        return jwt.getSubject(); // hoặc jwt.getClaim("userId").toString()
    }
    throw new UnauthorizedException("User not authenticated");
}
```

### 3. SecurityConfig Update

```java
.authorizeHttpRequests(auth -> auth
    // Public endpoints
    .requestMatchers("/auth/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/users").permitAll()
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
    
    // File endpoints - GET public, others authenticated
    .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/files/**").authenticated()
    .requestMatchers(HttpMethod.PUT, "/files/**").authenticated()
    .requestMatchers(HttpMethod.DELETE, "/files/**").authenticated()
    
    // WebSocket
    .requestMatchers("/ws/**").permitAll()
    
    // All others require auth
    .anyRequest().authenticated()
)
```

---

## ⚠️ LƯU Ý QUAN TRỌNG

1. **KHÔNG deploy production** cho đến khi hoàn thành Priority 0
2. **Test kỹ authentication** trước khi fix các vấn đề khác
3. **Backup code** trước khi sửa
4. **Commit sau mỗi vấn đề** được fix
5. **Update documentation** sau khi hoàn thành

---

## 📞 HỖ TRỢ

Nếu gặp vấn đề khi implement:

1. Xem code tham khảo trong `UserServiceImpl.java` - đã implement authentication pattern
2. Đọc [SECURITY_ARCHITECTURE.md](./SECURITY_ARCHITECTURE.md)
3. Check logs với `@Slf4j` để debug
4. Test với Postman/api-test.http

---

**Ngày tạo báo cáo**: 21/01/2026  
**Cập nhật lần cuối**: 21/01/2026  
**Người kiểm tra**: GitHub Copilot
