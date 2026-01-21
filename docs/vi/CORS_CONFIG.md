# Hướng Dẫn Cấu Hình CORS

> 🌐 **Cấu Hình Cross-Origin Resource Sharing**  
> 📅 **Cập nhật lần cuối**: Tháng 1/2026

---

## 📋 CORS Là Gì?

**CORS (Cross-Origin Resource Sharing)** là cơ chế bảo mật cho phép hoặc hạn chế các ứng dụng web chạy ở một origin truy cập tài nguyên từ origin khác.

### Ví Dụ Tình Huống
```
Frontend: http://localhost:5173 (Vite)
Backend:  http://localhost:8080 (Spring Boot)

❌ Không có CORS: Browser chặn request
✅ Có CORS: Backend cho phép request
```

---

## 🎯 Cấu Hình CORS Của Chúng Ta

### Vị Trí
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
        // Chi tiết cấu hình...
    }
}
```

---

## ⚙️ Phân Tích Cấu Hình

### 1. Allowed Origins

**Là gì**: Domain nào được phép gọi API của chúng ta

**Cấu hình** (`application.yaml`):
```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173,http://localhost:8081,http://192.168.1.100:8081}
```

**Giải thích origins:**
| Origin | Mục Đích |
|--------|---------|
| `http://localhost:3000` | Expo Native development |
| `http://localhost:5173` | Vite dev server (Web) |
| `http://localhost:8081` | Expo Metro Bundler |
| `http://192.168.1.100:8081` | Test mobile trên LAN |

**Code:**
```java
configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
configuration.setAllowedOriginPatterns(List.of("*")); // Cho mobile apps
```

**Tại sao `setAllowedOriginPatterns("*")`?**
- Mobile apps không gửi `Origin` header
- Cho phép tất cả origins làm fallback
- Vẫn bảo mật vì có `allowCredentials`

---

### 2. Allowed Methods

**Là gì**: HTTP methods nào được phép

**Cấu hình**:
```yaml
cors:
  allowed-methods: ${CORS_ALLOWED_METHODS:GET,POST,PUT,DELETE,PATCH,OPTIONS}
```

**Code:**
```java
configuration.setAllowedMethods(Arrays.asList(allowedMethods));
```

**Giải thích methods:**
- `GET`: Lấy dữ liệu
- `POST`: Tạo tài nguyên mới
- `PUT`: Cập nhật toàn bộ tài nguyên
- `PATCH`: Cập nhật một phần
- `DELETE`: Xóa tài nguyên
- `OPTIONS`: Preflight request (tự động)

---

### 3. Allowed Headers

**Là gì**: Headers nào client có thể gửi trong requests

**Cấu hình**:
```yaml
cors:
  allowed-headers: ${CORS_ALLOWED_HEADERS:*}
```

**`*` có nghĩa**: Cho phép TẤT CẢ headers

**Headers phổ biến:**
- `Authorization`: JWT token
- `Content-Type`: Kiểu request body
- `X-Device-Type`: Custom header
- `X-User-Id`: Custom header

**Code:**
```java
configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
```

---

### 4. Exposed Headers

**Là gì**: Response headers nào JavaScript có thể đọc

**Cấu hình**:
```yaml
cors:
  exposed-headers: ${CORS_EXPOSED_HEADERS:Authorization,Content-Type,X-Total-Count,X-User-Id}
```

**Tại sao cần?**
Mặc định, browsers chỉ cho phép JavaScript đọc các headers:
- `Cache-Control`
- `Content-Language`
- `Content-Type`
- `Expires`
- `Last-Modified`
- `Pragma`

**Exposed headers của chúng ta:**
| Header | Mục Đích |
|--------|---------|
| `Authorization` | JWT token mới (refresh) |
| `Content-Type` | Format response |
| `X-Total-Count` | Tổng số bản ghi (pagination) |
| `X-User-Id` | ID user hiện tại |

**Sử dụng frontend:**
```javascript
const response = await fetch('/api/v1/products');
const totalCount = response.headers.get('X-Total-Count'); // ✅ Hoạt động
const userId = response.headers.get('X-User-Id'); // ✅ Hoạt động
```

**Code:**
```java
configuration.setExposedHeaders(Arrays.asList(exposedHeaders));
```

---

### 5. Allow Credentials

**Là gì**: Cho phép gửi cookies và Authorization headers

**Cấu hình**:
```yaml
cors:
  allow-credentials: ${CORS_ALLOW_CREDENTIALS:true}
```

**Khi `true`:**
- Frontend có thể gửi cookies
- Frontend có thể gửi `Authorization` header
- **KHÔNG THỂ** dùng `allowed-origins: *` (phải chỉ định domains)

**Sử dụng frontend:**
```javascript
// Phải set credentials: 'include'
fetch('http://localhost:8080/api/v1/users', {
    credentials: 'include',  // ✅ Gửi cookies + Authorization
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

### 6. Max Age (Cache Preflight)

**Là gì**: Thời gian browsers cache preflight responses

**Cấu hình**:
```yaml
cors:
  max-age: ${CORS_MAX_AGE:3600}
```

**Giá trị**: `3600` giây = 1 giờ

**Preflight là gì?**
Trước một số requests (PUT, DELETE, POST với custom headers), browsers gửi **OPTIONS request** để kiểm tra xem CORS có cho phép request thực sự không.

**Không có caching:**
```
OPTIONS /api/v1/users → Kiểm tra CORS
GET /api/v1/users → Request thực

OPTIONS /api/v1/products → Kiểm tra CORS
GET /api/v1/products → Request thực
```

**Có caching (1 giờ):**
```
OPTIONS /api/v1/users → Kiểm tra CORS (được cache)
GET /api/v1/users → Request thực

GET /api/v1/products → Request thực (không cần OPTIONS)
```

**Code:**
```java
configuration.setMaxAge(maxAge);
```

---

## 🚀 Cách Hoạt Động

### Luồng Request

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
│  - Kiểm tra CORS        │
│  - Kiểm tra Origin      │
│  - Kiểm tra Method      │
│  - Kiểm tra Headers     │
└────────┬────────────────┘
         │
         │ 2. CORS ✅ Được phép
         ▼
┌─────────────────────────┐
│  Controller             │
│  - Xử lý request        │
└────────┬────────────────┘
         │
         │ 3. Response
         │    Access-Control-Allow-Origin: http://localhost:5173
         │    Access-Control-Allow-Credentials: true
         │    Access-Control-Expose-Headers: Authorization,X-Total-Count
         ▼
┌─────────────────┐
│   Frontend      │
│ Nhận dữ liệu    │
└─────────────────┘
```

---

## 🔧 Cấu Hình Theo Môi Trường

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

### Sử Dụng Biến Môi Trường
```bash
# .env file
CORS_ALLOWED_ORIGINS=https://fruvia.com,https://app.fruvia.com
CORS_ALLOW_CREDENTIALS=true
```

---

## 🐛 Khắc Phục Sự Cố

### Lỗi: "CORS policy: No 'Access-Control-Allow-Origin' header"

**Nguyên nhân**: Origin không có trong danh sách cho phép

**Giải pháp**:
```yaml
cors:
  allowed-origins: http://localhost:5173  # Thêm URL frontend của bạn
```

---

### Lỗi: "Credential is not supported if the CORS header is '*'"

**Nguyên nhân**: `allowCredentials: true` với `allowed-origins: *`

**Giải pháp**:
```java
// ❌ Đừng làm thế này
configuration.setAllowedOrigins(List.of("*"));
configuration.setAllowCredentials(true);

// ✅ Làm thế này
configuration.setAllowedOrigins(List.of("http://localhost:5173"));
configuration.setAllowCredentials(true);
```

---

### Lỗi: "Method PUT is not allowed"

**Nguyên nhân**: PUT không có trong allowed methods

**Giải pháp**:
```yaml
cors:
  allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
```

---

## 📱 Lưu Ý Cho Mobile App

### Tại sao `setAllowedOriginPatterns("*")`?

Mobile apps (React Native, Expo) **không gửi Origin header**, nên:

```java
configuration.setAllowedOriginPatterns(List.of("*"));
```

Điều này cho phép requests từ mobile không có Origin header.

### Lưu Ý Bảo Mật
Vẫn bảo mật vì:
1. `allowCredentials: true` yêu cầu JWT hợp lệ
2. JWT validate danh tính user
3. Backend validate tất cả requests

---

## ✅ Best Practices

### NÊN
- ✅ Chỉ định origins chính xác trong production
- ✅ Dùng biến môi trường cho cấu hình
- ✅ Set `allowCredentials: true` cho auth
- ✅ Expose chỉ các headers cần thiết
- ✅ Cache preflight responses (`max-age`)

### KHÔNG NÊN
- ❌ Dùng `allowed-origins: *` trong production
- ❌ Expose headers nhạy cảm
- ❌ Cho phép tất cả methods nếu không cần
- ❌ Tắt CORS (rủi ro bảo mật)

---

## 🔗 Tài Liệu Liên Quan
- [Kiến Trúc Bảo Mật](./SECURITY_ARCHITECTURE.md)
- [Quy Tắc Backend](./RULE_BACKEND.md)

---

**Cập nhật lần cuối**: 21/01/2026  
**Người duy trì**: Fruvia Development Team
