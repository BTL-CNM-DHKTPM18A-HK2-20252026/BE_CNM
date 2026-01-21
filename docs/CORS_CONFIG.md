# CORS Configuration Guide

> 🌐 **Cross-Origin Resource Sharing Configuration**  
> 📅 **Last Updated**: January 2026

---

## 📋 What is CORS?

**CORS (Cross-Origin Resource Sharing)** is a security mechanism that allows or restricts web applications running at one origin to access resources from a different origin.

### Example Scenario
```
Frontend: http://localhost:5173 (Vite)
Backend:  http://localhost:8080 (Spring Boot)

❌ Without CORS: Browser blocks the request
✅ With CORS: Backend allows the request
```

---

## 🎯 Our CORS Configuration

### Location
```
src/main/java/iuh/fit/configuration/CorsConfig.java
```

### Configuration Class
```java
@Configuration
public class CorsConfig {
    
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;
    
    @Value("${cors.allowed-methods}")
    private String[] allowedMethods;
    
    @Value("${cors.allowed-headers}")
    private String[] allowedHeaders;
    
    @Value("${cors.exposed-headers}")
    private String[] exposedHeaders;
    
    @Value("${cors.allow-credentials}")
    private boolean allowCredentials;
    
    @Value("${cors.max-age}")
    private long maxAge;
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Configuration details...
    }
}
```

---

## ⚙️ Configuration Breakdown

### 1. Allowed Origins

**What**: Which domains can call our API

**Configuration** (`application.yaml`):
```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173,http://localhost:8081,http://192.168.1.100:8081}
```

**Origins explained:**
| Origin | Purpose |
|--------|---------|
| `http://localhost:3000` | Expo Native development |
| `http://localhost:5173` | Vite dev server (Web) |
| `http://localhost:8081` | Expo Metro Bundler |
| `http://192.168.1.100:8081` | Mobile device testing on LAN |

**Code:**
```java
configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
configuration.setAllowedOriginPatterns(List.of("*")); // For mobile apps
```

**Why `setAllowedOriginPatterns("*")`?**
- Mobile apps don't send `Origin` header
- Allows all origins as fallback
- Still secure because of `allowCredentials`

---

### 2. Allowed Methods

**What**: Which HTTP methods are permitted

**Configuration**:
```yaml
cors:
  allowed-methods: ${CORS_ALLOWED_METHODS:GET,POST,PUT,DELETE,PATCH,OPTIONS}
```

**Code:**
```java
configuration.setAllowedMethods(Arrays.asList(allowedMethods));
```

**Methods explained:**
- `GET`: Fetch data
- `POST`: Create new resources
- `PUT`: Update entire resource
- `PATCH`: Partial update
- `DELETE`: Remove resource
- `OPTIONS`: Preflight request (automatic)

---

### 3. Allowed Headers

**What**: Which headers client can send in requests

**Configuration**:
```yaml
cors:
  allowed-headers: ${CORS_ALLOWED_HEADERS:*}
```

**`*` means**: Allow ALL headers

**Common headers:**
- `Authorization`: JWT token
- `Content-Type`: Request body type
- `X-Device-Type`: Custom header
- `X-User-Id`: Custom header

**Code:**
```java
configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
```

---

### 4. Exposed Headers

**What**: Which response headers JavaScript can read

**Configuration**:
```yaml
cors:
  exposed-headers: ${CORS_EXPOSED_HEADERS:Authorization,Content-Type,X-Total-Count,X-User-Id}
```

**Why needed?**
By default, browsers only allow JavaScript to read these headers:
- `Cache-Control`
- `Content-Language`
- `Content-Type`
- `Expires`
- `Last-Modified`
- `Pragma`

**Our exposed headers:**
| Header | Purpose |
|--------|---------|
| `Authorization` | New JWT token (refresh) |
| `Content-Type` | Response format |
| `X-Total-Count` | Pagination total |
| `X-User-Id` | Current user ID |

**Frontend usage:**
```javascript
const response = await fetch('/api/v1/products');
const totalCount = response.headers.get('X-Total-Count'); // ✅ Works
const userId = response.headers.get('X-User-Id'); // ✅ Works
```

**Code:**
```java
configuration.setExposedHeaders(Arrays.asList(exposedHeaders));
```

---

### 5. Allow Credentials

**What**: Allow sending cookies and Authorization headers

**Configuration**:
```yaml
cors:
  allow-credentials: ${CORS_ALLOW_CREDENTIALS:true}
```

**When `true`:**
- Frontend can send cookies
- Frontend can send `Authorization` header
- **CANNOT** use `allowed-origins: *` (must specify domains)

**Frontend usage:**
```javascript
// Must set credentials: 'include'
fetch('http://localhost:8080/api/v1/users', {
    credentials: 'include',  // ✅ Send cookies + Authorization
    headers: {
        'Authorization': 'Bearer eyJhbGc...'
    }
});
```

**Code:**
```java
configuration.setAllowCredentials(allowCredentials);
```

---

### 6. Max Age (Preflight Cache)

**What**: How long browsers cache preflight responses

**Configuration**:
```yaml
cors:
  max-age: ${CORS_MAX_AGE:3600}
```

**Value**: `3600` seconds = 1 hour

**What is preflight?**
Before certain requests (PUT, DELETE, POST with custom headers), browsers send an **OPTIONS** request to check if CORS allows the actual request.

**Without caching:**
```
OPTIONS /api/v1/users → Check CORS
GET /api/v1/users → Actual request

OPTIONS /api/v1/products → Check CORS
GET /api/v1/products → Actual request
```

**With caching (1 hour):**
```
OPTIONS /api/v1/users → Check CORS (cached)
GET /api/v1/users → Actual request

GET /api/v1/products → Actual request (no OPTIONS needed)
```

**Code:**
```java
configuration.setMaxAge(maxAge);
```

---

## 🚀 How It Works

### Request Flow

```
┌─────────────────┐
│   Frontend      │
│ localhost:5173  │
└────────┬────────┘
         │
         │ 1. POST /api/v1/messages
         │    Origin: http://localhost:5173
         │    Authorization: Bearer xxx
         ▼
┌─────────────────────────┐
│  Spring Security        │
│  - Check CORS           │
│  - Check Origin         │
│  - Check Method         │
│  - Check Headers        │
└────────┬────────────────┘
         │
         │ 2. CORS ✅ Allowed
         ▼
┌─────────────────────────┐
│  Controller             │
│  - Process request      │
└────────┬────────────────┘
         │
         │ 3. Response
         │    Access-Control-Allow-Origin: http://localhost:5173
         │    Access-Control-Allow-Credentials: true
         │    Access-Control-Expose-Headers: Authorization,X-Total-Count
         ▼
┌─────────────────┐
│   Frontend      │
│ Receives data   │
└─────────────────┘
```

---

## 🔧 Environment-Specific Configuration

### Development
```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173,http://localhost:8081
```

### Production
```yaml
cors:
  allowed-origins: https://fruvia.com,https://app.fruvia.com
```

### Using Environment Variables
```bash
# .env file
CORS_ALLOWED_ORIGINS=https://fruvia.com,https://app.fruvia.com
CORS_ALLOW_CREDENTIALS=true
```

---

## 🐛 Troubleshooting

### Error: "CORS policy: No 'Access-Control-Allow-Origin' header"

**Cause**: Origin not in allowed list

**Solution**:
```yaml
cors:
  allowed-origins: http://localhost:5173  # Add your frontend URL
```

---

### Error: "Credential is not supported if the CORS header is '*'"

**Cause**: `allowCredentials: true` with `allowed-origins: *`

**Solution**:
```java
// ❌ Don't do this
configuration.setAllowedOrigins(List.of("*"));
configuration.setAllowCredentials(true);

// ✅ Do this
configuration.setAllowedOrigins(List.of("http://localhost:5173"));
configuration.setAllowCredentials(true);
```

---

### Error: "Method PUT is not allowed"

**Cause**: PUT not in allowed methods

**Solution**:
```yaml
cors:
  allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
```

---

## 📱 Mobile App Considerations

### Why `setAllowedOriginPatterns("*")`?

Mobile apps (React Native, Expo) **don't send Origin header**, so:

```java
configuration.setAllowedOriginPatterns(List.of("*"));
```

This allows mobile requests without Origin header.

### Security Note
Still secure because:
1. `allowCredentials: true` requires valid JWT
2. JWT validates user identity
3. Backend validates all requests

---

## ✅ Best Practices

### DO
- ✅ Specify exact origins in production
- ✅ Use environment variables for configuration
- ✅ Set `allowCredentials: true` for auth
- ✅ Expose only necessary headers
- ✅ Cache preflight responses (`max-age`)

### DON'T
- ❌ Use `allowed-origins: *` in production
- ❌ Expose sensitive headers
- ❌ Allow all methods if not needed
- ❌ Disable CORS (security risk)

---

## 🔗 Related Documentation
- [Security Architecture](./SECURITY_ARCHITECTURE.md)
- [API Guidelines](./API_GUIDELINES.md)

---

**Last Updated**: January 21, 2026  
**Maintainer**: Fruvia Development Team
