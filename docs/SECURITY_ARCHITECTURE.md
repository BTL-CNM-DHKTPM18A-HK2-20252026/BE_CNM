# Security Architecture Documentation

> 🔐 **Spring Security Configuration for Fruvia Chat**  
> 📅 **Last Updated**: January 2026

---

## 📋 Table of Contents
1. [Overview](#overview)
2. [Authentication Flow](#authentication-flow)
3. [SecurityConfig Explained](#securityconfig-explained)
4. [JWT Structure](#jwt-structure)
5. [Endpoint Authorization](#endpoint-authorization)
6. [CORS Configuration](#cors-configuration)

---

## 1. Overview

### Technology Stack
- **Spring Security 6.x**
- **OAuth2 Resource Server** (JWT)
- **BCrypt Password Encoder**
- **HMAC-SHA512** for JWT signing

### Security Features
✅ JWT-based authentication  
✅ Role-based authorization (ROLE_USER, ROLE_ADMIN)  
✅ CSRF protection disabled (stateless API)  
✅ CORS enabled for web + mobile  
✅ Password hashing with BCrypt  

---

## 2. Authentication Flow

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
│  - Generate JWT     │
└──────┬──────────────┘
       │
       │ 2. Response
       │    { token: "eyJhbGc..." }
       ▼
┌─────────────┐
│   Client    │
│ (Save token)│
└──────┬──────┘
       │
       │ 3. GET /users/profile
       │    Authorization: Bearer eyJhbGc...
       ▼
┌─────────────────────────┐
│  Spring Security        │
│  - JwtDecoder           │
│  - Validate signature   │
│  - Check expiration     │
│  - Extract user info    │
└──────┬──────────────────┘
       │
       │ 4. Authenticated Request
       ▼
┌─────────────────────┐
│  UserController     │
│  - Get user profile │
└─────────────────────┘
```

---

## 3. SecurityConfig Explained

### 3.1 Security Filter Chain

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(Customizer.withDefaults())  // Enable CORS
        .csrf(AbstractHttpConfigurer::disable)  // Disable CSRF (JWT-based)
        .authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/users").permitAll()
            
            // Protected endpoints
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );
    
    return http.build();
}
```

**Key Points:**
- ✅ **CORS enabled**: Allows frontend calls
- ❌ **CSRF disabled**: Not needed for stateless APIs
- 🔓 **Public paths**: `/auth/**`, `/swagger-ui/**`
- 🔒 **Protected paths**: All others require JWT

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

**How it works:**
1. Takes `jwt.signer-key` from `application.yaml`
2. Creates secret key with **HMAC-SHA512** algorithm
3. Validates JWT signature on every request
4. Throws `401 Unauthorized` if invalid

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

**Converts JWT payload to Spring Security authorities:**
```json
// JWT Payload
{
  "sub": "user123",
  "scope": "USER ADMIN",
  "exp": 1737468900
}

// ↓ Converts to ↓

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

**Usage:**
```java
// During registration
String hashedPassword = passwordEncoder.encode("password123");
// Save: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

// During login
boolean matches = passwordEncoder.matches("password123", hashedPassword);
// Returns: true
```

---

## 4. JWT Structure

### Token Format
```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyMTIzIiwic2NvcGUiOiJVU0VSIiwiZXhwIjoxNzM3NDY4OTAwfQ.signature
```

### Decoded Payload
```json
{
  "sub": "user123",           // User ID
  "scope": "USER ADMIN",      // Roles (space-separated)
  "username": "johndoe",      // Username (optional)
  "exp": 1737468900,          // Expiration timestamp
  "iat": 1737465300           // Issued at timestamp
}
```

### Claims Mapping
| Claim | Description | Used By |
|-------|-------------|---------|
| `sub` | User ID | `JwtUtils.getCurrentUserId()` |
| `scope` | User roles | Authorization checks |
| `username` | Display name | `JwtUtils.getCurrentUsername()` |
| `exp` | Expiration | JWT validation |

---

## 5. Endpoint Authorization

### Public Endpoints (No JWT Required)

| Path | Methods | Purpose |
|------|---------|---------|
| `/auth/**` | ALL | Login, register, refresh token |
| `POST /users` | POST | User registration |
| `/swagger-ui/**` | ALL | API documentation |
| `/files/**` | ALL | File upload/download (temp) |
| `/ws/**` | ALL | WebSocket connections |

### Protected Endpoints (JWT Required)

| Path | Methods | Purpose |
|------|---------|---------|
| `/users/{id}` | PUT, DELETE | Update/delete user |
| `/messages/**` | ALL | Chat messages |
| `/conversations/**` | ALL | Conversations |
| All others | ALL | Requires authentication |

---

## 6. CORS Configuration

**Location**: `CorsConfig.java` (centralized)

### Allowed Origins
```yaml
cors:
  allowed-origins: 
    - http://localhost:3000      # Expo Native
    - http://localhost:5173      # Vite (Web)
    - http://localhost:8081      # Expo Metro
    - http://192.168.1.100:8081  # Mobile device
```

### Allowed Methods
```
GET, POST, PUT, DELETE, PATCH, OPTIONS
```

### Allowed Headers
```
* (all headers)
```

### Exposed Headers
```
Authorization, Content-Type, X-Total-Count, X-User-Id
```

Frontend can read these headers from response.

### Credentials
```yaml
allow-credentials: true
```
Allows sending cookies and Authorization headers.

---

## 🔒 Security Best Practices

### ✅ DO
- Store JWT in **HttpOnly cookies** (web) or **secure storage** (mobile)
- Set appropriate **token expiration** (15-30 minutes for access token)
- Implement **refresh token** mechanism
- Use **HTTPS** in production
- Validate **all user inputs**
- Use **@PreAuthorize** for method-level security

### ❌ DON'T
- Store JWT in **localStorage** (XSS vulnerable)
- Use **weak secrets** for signing
- Expose **sensitive data** in JWT payload
- Keep **long-lived** access tokens
- Trust **client-side** validation only

---

## 🛠️ Testing Security

### Test Authentication
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

### Test Protected Endpoint
```bash
# Without token (401)
curl http://localhost:8080/api/v1/users/profile

# With token (200)
curl http://localhost:8080/api/v1/users/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

---

## 📚 Related Documentation
- [CORS Configuration](./CORS_CONFIG.md)
- [JWT Utils Usage](./UTILS_DOCUMENTATION.md#jwtutils)
- [API Guidelines](./API_GUIDELINES.md)

---

**Last Updated**: January 21, 2026  
**Maintainer**: Fruvia Development Team
