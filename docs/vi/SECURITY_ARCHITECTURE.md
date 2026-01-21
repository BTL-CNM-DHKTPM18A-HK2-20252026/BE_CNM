# Tài Liệu Kiến Trúc Bảo Mật

> 🔐 **Cấu Hình Spring Security cho Fruvia Chat**  
> 📅 **Cập nhật lần cuối**: Tháng 1/2026

---

## 📋 Mục Lục
1. [Tổng Quan](#tổng-quan)
2. [Luồng Xác Thực](#luồng-xác-thực)
3. [Giải Thích SecurityConfig](#giải-thích-securityconfig)
4. [Cấu Trúc JWT](#cấu-trúc-jwt)
5. [Phân Quyền Endpoint](#phân-quyền-endpoint)
6. [Cấu Hình CORS](#cấu-hình-cors)

---

## 1. Tổng Quan

### Technology Stack
- **Spring Security 6.x**
- **OAuth2 Resource Server** (JWT)
- **BCrypt Password Encoder**
- **HMAC-SHA512** để ký JWT

### Tính Năng Bảo Mật
✅ Xác thực dựa trên JWT  
✅ Phân quyền theo vai trò (ROLE_USER, ROLE_ADMIN)  
✅ Tắt CSRF protection (stateless API)  
✅ CORS được bật cho web + mobile  
✅ Hash mật khẩu với BCrypt  

---

## 2. Luồng Xác Thực

```
┌─────────────┐
│   Client    │
│ (Web/Mobile)│
└──────┬──────┘
       │
       │ 1. POST /auth/login
       │    { username, password }
       ▼
┌─────────────────────┐
│  AuthController     │
│  - Validate user    │
│  - Tạo JWT          │
└──────┬──────────────┘
       │
       │ 2. Response
       │    { token: "eyJhbGc..." }
       ▼
┌─────────────┐
│   Client    │
│ (Lưu token) │
└──────┬──────┘
       │
       │ 3. GET /users/profile
       │    Authorization: Bearer eyJhbGc...
       ▼
┌─────────────────────────┐
│  Spring Security        │
│  - JwtDecoder           │
│  - Validate signature   │
│  - Kiểm tra hết hạn     │
│  - Trích xuất user info │
└──────┬──────────────────┘
       │
       │ 4. Authenticated Request
       ▼
┌─────────────────────┐
│  UserController     │
│  - Lấy user profile │
└─────────────────────┘
```

---

## 3. Giải Thích SecurityConfig

### 3.1 Security Filter Chain

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(Customizer.withDefaults())  // Bật CORS
        .csrf(AbstractHttpConfigurer::disable)  // Tắt CSRF (dựa trên JWT)
        .authorizeHttpRequests(auth -> auth
            // Endpoints công khai
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/users").permitAll()
            
            // Endpoints được bảo vệ
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
    
    return http.build();
}
```

**Điểm chính:**
- ✅ **CORS được bật**: Cho phép gọi từ frontend
- ❌ **CSRF tắt**: Không cần cho stateless APIs
- 🔓 **Public paths**: `/auth/**`, `/swagger-ui/**`
- 🔒 **Protected paths**: Tất cả các path khác cần JWT

---

### 3.2 JWT Decoder

```java
@Bean
public JwtDecoder jwtDecoder() {
    SecretKeySpec secretKeySpec = new SecretKeySpec(
        signerKey.getBytes(), 
        "HmacSHA512"
    );
    return NimbusJwtDecoder
        .withSecretKey(secretKeySpec)
        .macAlgorithm(MacAlgorithm.HS512)
        .build();
}
```

**Cách hoạt động:**
1. Lấy `jwt.signer-key` từ `application.yaml`
2. Tạo secret key với thuật toán **HMAC-SHA512**
3. Validate chữ ký JWT trên mỗi request
4. Throw `401 Unauthorized` nếu không hợp lệ

---

### 3.3 JWT Authentication Converter

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = 
        new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
    grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
    
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

**Chuyển đổi JWT payload thành Spring Security authorities:**
```json
// JWT Payload
{
  "sub": "user123",
  "scope": "USER ADMIN",
  "exp": 1737468900
}

// ↓ Chuyển thành ↓

// Spring Security Authorities
["ROLE_USER", "ROLE_ADMIN"]
```

---

### 3.4 Password Encoder

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Sử dụng:**
```java
// Khi đăng ký
String hashedPassword = passwordEncoder.encode("password123");
// Lưu: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

// Khi đăng nhập
boolean matches = passwordEncoder.matches("password123", hashedPassword);
// Trả về: true
```

---

## 4. Cấu Trúc JWT

### Format Token
```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMTIzIiwic2NvcGUiOiJVU0VSIiwiZXhwIjoxNzM3NDY4OTAwfQ.signature
```

### Payload Đã Decode
```json
{
  "sub": "user123",           // User ID
  "scope": "USER ADMIN",      // Roles (phân cách bằng space)
  "username": "johndoe",      // Username (tùy chọn)
  "exp": 1737468900,          // Timestamp hết hạn
  "iat": 1737465300           // Timestamp tạo
}
```

### Mapping Claims
| Claim | Mô Tả | Được Sử Dụng Bởi |
|-------|-------|------------------|
| `sub` | User ID | `JwtUtils.getCurrentUserId()` |
| `scope` | User roles | Kiểm tra authorization |
| `username` | Tên hiển thị | `JwtUtils.getCurrentUsername()` |
| `exp` | Hết hạn | JWT validation |

---

## 5. Phân Quyền Endpoint

### Endpoints Công Khai (Không Cần JWT)

| Path | Methods | Mục Đích |
|------|---------|---------|
| `/auth/**` | ALL | Login, đăng ký, refresh token |
| `POST /users` | POST | Đăng ký user |
| `/swagger-ui/**` | ALL | Tài liệu API |
| `/files/**` | ALL | Upload/download file (tạm thời) |
| `/ws/**` | ALL | Kết nối WebSocket |

### Endpoints Được Bảo Vệ (Cần JWT)

| Path | Methods | Mục Đích |
|------|---------|---------|
| `/users/{id}` | PUT, DELETE | Cập nhật/xóa user |
| `/messages/**` | ALL | Tin nhắn chat |
| `/conversations/**` | ALL | Cuộc hội thoại |
| Tất cả khác | ALL | Yêu cầu xác thực |

---

## 6. Cấu Hình CORS

**Vị trí**: `CorsConfig.java` (tập trung)

### Allowed Origins
```yaml
cors:
  allowed-origins: 
    - http://localhost:3000      # Expo Native
    - http://localhost:5173      # Vite (Web)
    - http://localhost:8081      # Expo Metro
    - http://192.168.1.100:8081  # Thiết bị mobile
```

### Allowed Methods
```
GET, POST, PUT, DELETE, PATCH, OPTIONS
```

### Allowed Headers
```
* (tất cả headers)
```

### Exposed Headers
```
Authorization, Content-Type, X-Total-Count, X-User-Id
```

Frontend có thể đọc các headers này từ response.

### Credentials
```yaml
allow-credentials: true
```
Cho phép gửi cookies và Authorization headers.

---

## 🔒 Best Practices Bảo Mật

### ✅ NÊN
- Lưu JWT trong **HttpOnly cookies** (web) hoặc **secure storage** (mobile)
- Đặt **thời gian hết hạn** phù hợp (15-30 phút cho access token)
- Triển khai cơ chế **refresh token**
- Sử dụng **HTTPS** trong production
- **Validate tất cả** đầu vào user
- Dùng **@PreAuthorize** cho bảo mật cấp method

### ❌ KHÔNG NÊN
- Lưu JWT trong **localStorage** (dễ bị XSS)
- Dùng **secret key yếu** để ký
- Expose **dữ liệu nhạy cảm** trong JWT payload
- Giữ **access token sống lâu**
- Chỉ tin vào **validation phía client**

---

## 🛠️ Testing Bảo Mật

### Test Xác Thực
```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password123"}'

# Response
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "expiresIn": 3600
}
```

### Test Endpoint Được Bảo Vệ
```bash
# Không có token (401)
curl http://localhost:8080/api/v1/users/profile

# Có token (200)
curl http://localhost:8080/api/v1/users/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

---

## 📚 Tài Liệu Liên Quan
- [Cấu Hình CORS](./CORS_CONFIG.md)
- [Sử Dụng JWT Utils](./UTILS_DOCUMENTATION.md#jwtutils)
- [Hướng Dẫn API](./RULE_BACKEND.md)

---

**Cập nhật lần cuối**: 21/01/2026  
**Người duy trì**: Fruvia Development Team
