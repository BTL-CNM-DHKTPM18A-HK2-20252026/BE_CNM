# Tài Liệu Fruvia Backend

> 📚 **Tài liệu toàn diện cho Fruvia Chat Backend**  
> 🚀 **Xây dựng với Spring Boot 3.x + Spring Security + JWT**

---

## 📖 Mục Lục Tài Liệu

### Tài Liệu Cốt Lõi
| Tài Liệu | Mô Tả |
|----------|-------|
| [Cấu Trúc Code](./CODE_STRUCTURE.md) | 📂 Kiến trúc dự án & tổ chức package |
| [Kiến Trúc Bảo Mật](./SECURITY_ARCHITECTURE.md) | 🔐 Xác thực, JWT, luồng phân quyền |
| [Cấu Hình CORS](./CORS_CONFIG.md) | 🌐 Thiết lập cross-origin cho web & mobile |
| [Tài Liệu Utils](./UTILS_DOCUMENTATION.md) | 🛠️ Các class utility và phương thức hỗ trợ |
| [Quy Tắc Backend](./RULE_BACKEND.md) | 📋 Chuẩn phát triển backend |
| [Hướng Dẫn AI Agent](./RULE_PROMPT_AGENT.md) | 🤖 Làm việc hiệu quả với AI |

---

## 🎯 Bắt Đầu Nhanh

### Yêu Cầu
- ☕ **Java 17+**
- 📦 **Maven 3.8+**
- 🗄️ **PostgreSQL** hoặc **MySQL**
- 🔑 **Tài khoản Cloudinary** (để upload file)

### Cài Đặt

1. **Clone repository**
   ```bash
   git clone <repository-url>
   cd backend/fruvia
   ```

2. **Cấu hình `application.yaml`**
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/fruvia_db
       username: your_username
       password: your_password
   
   jwt:
     signer-key: your-secret-key-here
   
   cloudinary:
     cloud-name: your-cloud-name
     api-key: your-api-key
     api-secret: your-api-secret
   ```

3. **Chạy ứng dụng**
   ```bash
   mvn spring-boot:run
   ```

4. **Truy cập Swagger UI**
   ```
   http://localhost:8080/api/v1/swagger-ui.html
   ```

---

## 📂 Tổng Quan Cấu Trúc Dự Án

```
fruvia/
├── src/main/java/iuh/fit/
│   ├── configuration/      # 🔧 Cấu hình Spring (Security, CORS, Cloudinary)
│   ├── controller/         # 🎮 REST API endpoints
│   ├── dto/                # 📦 Request/Response models
│   ├── entity/             # 🗄️ JPA entities (các model database)
│   ├── enums/              # 🏷️ Các kiểu enum
│   ├── mapper/             # 🔄 Chuyển đổi DTO ↔ Entity
│   ├── repository/         # 💾 Tầng truy cập database
│   ├── service/            # 💼 Business logic
│   └── utils/              # 🛠️ Các class tiện ích
├── src/main/resources/
│   ├── application.yaml    # ⚙️ Cấu hình
│   └── templates/          # 📧 Email templates
├── docs/                   # 📚 Tài liệu này
├── test_api/               # 🧪 Script test API
└── pom.xml                 # 📋 Maven dependencies
```

**Chi tiết đầy đủ**: [Cấu Trúc Code](./CODE_STRUCTURE.md)

---

## 🔐 Xác Thực & Bảo Mật

### Xác thực dựa trên JWT
- ✅ Xác thực stateless
- ✅ Phân quyền dựa trên vai trò (ROLE_USER, ROLE_ADMIN)
- ✅ Mã hóa mật khẩu BCrypt
- ✅ Ký JWT bằng HMAC-SHA512

### Luồng Xác Thực
```
1. POST /auth/login → Nhận JWT token
2. Gửi token trong mỗi request: Authorization: Bearer <token>
3. Backend xác thực token ở mỗi request
4. Token hết hạn sau thời gian cấu hình
5. Endpoint refresh token để làm mới
```

**Chi tiết đầy đủ**: [Kiến Trúc Bảo Mật](./SECURITY_ARCHITECTURE.md)

---

## 🌐 Cấu Hình CORS

### Origins Được Hỗ Trợ
- 🖥️ **Web**: `http://localhost:5173` (Vite)
- 📱 **Mobile**: `http://localhost:8081` (Expo)
- 🌍 **Test LAN**: `http://192.168.1.100:8081`

### Cài Đặt Chính
```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173
  allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
  allowed-headers: *
  exposed-headers: Authorization,Content-Type,X-Total-Count,X-User-Id
  allow-credentials: true
  max-age: 3600
```

**Chi tiết đầy đủ**: [Cấu Hình CORS](./CORS_CONFIG.md)

---

## 🛠️ Các Class Tiện Ích

### Utils Có Sẵn
| Class | Mục Đích |
|-------|---------|
| `DateTimeUtils` | Format timestamp cho chat ("14:30", "Hôm qua 14:30") |
| `JwtUtils` | Trích xuất thông tin user từ JWT (userId, username, roles) |
| `ValidationUtils` | Validate email, số điện thoại, mật khẩu, tin nhắn |
| `MessageUtils` | Xem trước tin nhắn, phát hiện URL, mentions |
| `FileUtils` | Validate file, format kích thước, tên file unique |

**Ví dụ sử dụng**: [Tài Liệu Utils](./UTILS_DOCUMENTATION.md)

---

## 📡 API Endpoints

### Endpoints Công Khai (Không Cần Xác Thực)
```
POST   /auth/login           # Đăng nhập
POST   /auth/register        # Đăng ký
POST   /auth/refresh-token   # Làm mới JWT
GET    /swagger-ui.html      # Tài liệu API
```

### Endpoints Được Bảo Vệ (Cần JWT)
```
# Users
GET    /users/{id}           # Lấy thông tin user
PUT    /users/{id}           # Cập nhật user
DELETE /users/{id}           # Xóa user

# Messages
GET    /messages             # Danh sách tin nhắn
POST   /messages             # Gửi tin nhắn
DELETE /messages/{id}        # Xóa tin nhắn

# Conversations
GET    /conversations        # Danh sách hội thoại
POST   /conversations        # Tạo hội thoại
GET    /conversations/{id}   # Chi tiết hội thoại
```

---

## 🧪 Testing

### Test với cURL
```bash
# Đăng nhập
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password123"}'

# Lấy thông tin user (với token)
curl http://localhost:8080/api/v1/users/123 \
  -H "Authorization: Bearer eyJhbGc..."
```

### Test với Swagger UI
1. Mở http://localhost:8080/api/v1/swagger-ui.html
2. Click nút "Authorize"
3. Nhập token: `Bearer <your-jwt-token>`
4. Test endpoints trực tiếp trên browser

### Test Files
Kiểm tra folder `test_api/` cho các file `.http`:
```
test_api/
├── api-test.http                # Test API tổng quát
└── api-test-file-upload.http    # Test upload file
```

---

## 🔧 Cấu Hình

### Biến Môi Trường
Thiết lập các biến này trong production:
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/fruvia_db
SPRING_DATASOURCE_USERNAME=prod_user
SPRING_DATASOURCE_PASSWORD=secure_password

# JWT
JWT_SIGNER_KEY=your-production-secret-key

# Cloudinary
CLOUDINARY_CLOUD_NAME=your-cloud
CLOUDINARY_API_KEY=your-key
CLOUDINARY_API_SECRET=your-secret

# CORS
CORS_ALLOWED_ORIGINS=https://fruvia.com,https://app.fruvia.com

# Server
SERVER_PORT=8080
```

---

## 📦 Dependencies

### Dependencies Chính
- **Spring Boot 3.x** - Framework
- **Spring Security** - Xác thực & phân quyền
- **Spring Data JPA** - Database ORM
- **PostgreSQL/MySQL Driver** - Kết nối database
- **Lombok** - Giảm boilerplate code
- **Validation** - Validate đầu vào
- **Cloudinary** - Dịch vụ upload file

### Xem `pom.xml` để biết danh sách đầy đủ

---

## 🚀 Deploy

### Development
```bash
mvn spring-boot:run
```

### Production (JAR)
```bash
mvn clean package
java -jar target/fruvia-backend-0.0.1-SNAPSHOT.jar
```

### Docker
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## 🐛 Khắc Phục Sự Cố

### Các Vấn Đề Thường Gặp

**1. Lỗi CORS**
- ✅ Kiểm tra `application.yaml` → `cors.allowed-origins`
- ✅ Thêm URL frontend của bạn vào danh sách
- 📚 Xem: [Cấu Hình CORS](./CORS_CONFIG.md)

**2. 401 Unauthorized**
- ✅ Kiểm tra JWT token trong header `Authorization`
- ✅ Xác minh format token: `Bearer <token>`
- ✅ Kiểm tra token đã hết hạn chưa
- 📚 Xem: [Kiến Trúc Bảo Mật](./SECURITY_ARCHITECTURE.md)

**3. Kết Nối Database Thất Bại**
- ✅ Xác minh database đang chạy
- ✅ Kiểm tra credentials trong `application.yaml`
- ✅ Test kết nối: `psql -U username -d database`

**4. Upload File Thất Bại**
- ✅ Kiểm tra credentials Cloudinary
- ✅ Xác minh giới hạn kích thước file (10MB ảnh, 50MB file)
- 📚 Xem: [Tài Liệu Utils](./UTILS_DOCUMENTATION.md#fileutils)

---

## 📚 Tài Nguyên Bổ Sung

### Tài Liệu Bên Ngoài
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Spring Security Docs](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io/) - JWT debugger

### Files Liên Quan
- `../README.md` - README chính của dự án
- `../ENTITY_STRUCTURE.md` - Quan hệ entity database
- `../FILE_MANAGEMENT.md` - Hướng dẫn upload file

---

## 🤝 Đóng Góp

### Code Style
- Dùng **4 spaces** để indent
- Tuân theo **quy ước Spring**
- Viết **JavaDoc** cho các public methods
- Thêm **@author** tags vào các class mới

### Quy Trình Pull Request
1. Tạo feature branch: `git checkout -b feature/tinh-nang-moi`
2. Commit thay đổi: `git commit -m "feat: thêm tính năng mới"`
3. Push branch: `git push origin feature/tinh-nang-moi`
4. Tạo Pull Request trên GitHub

---

## 📝 Lịch Sử Thay Đổi

### v1.0.0 - Tháng 1/2026
- ✅ Phiên bản đầu tiên
- ✅ Xác thực JWT
- ✅ Quản lý user
- ✅ Chat messaging
- ✅ Upload file (Cloudinary)
- ✅ Cấu hình CORS
- ✅ Tài liệu toàn diện

---

## 👥 Team

**Fruvia Development Team**  
📧 Liên hệ: [team@fruvia.com](mailto:team@fruvia.com)  
🌐 Website: https://fruvia.com

---

## 📄 License

Dự án này là tài sản riêng và bảo mật.  
© 2026 Fruvia Team. Mọi quyền được bảo lưu.

---

**Cần trợ giúp?** Kiểm tra các link tài liệu ở trên hoặc liên hệ team phát triển.
