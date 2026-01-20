# Fruvia Backend

Ứng dụng backend mạng xã hội hiện đại được xây dựng với Spring Boot 3.5.9, MongoDB và JWT authentication.

## 🚀 Tính Năng

- ✅ Xác thực & phân quyền dựa trên JWT
- ✅ Đăng ký và quản lý người dùng
- ✅ Tích hợp cơ sở dữ liệu MongoDB
- ✅ Thiết kế RESTful API
- ✅ Bảo mật với Spring Security
- ✅ Các thực thể mạng xã hội đầy đủ (Bài viết, Tin nhắn, Story, Cuộc gọi, v.v.)
- ✅ Hệ thống quản lý bạn bè
- ✅ Hỗ trợ nhắn tin thời gian thực
- ✅ Tính năng Story (nội dung 24 giờ)
- ✅ Ghi nhận cuộc gọi Video/Audio

## 📋 Yêu Cầu Hệ Thống

- Java 21 trở lên
- Maven 3.6+
- MongoDB 4.4+
- Git

## 🛠️ Công Nghệ Sử Dụng

- **Framework:** Spring Boot 3.5.9
- **Ngôn ngữ:** Java 21
- **Cơ sở dữ liệu:** MongoDB
- **Bảo mật:** Spring Security với JWT (HS512)
- **Build Tool:** Maven
- **Cache:** Redis (đã cấu hình)

## 📁 Cấu Trúc Dự Án

```
fruvia/
├── src/main/java/iuh/fit/
│   ├── configuration/      # Cấu hình Security & App
│   ├── controller/         # Các REST API Controller
│   ├── dto/               # Data Transfer Objects
│   ├── entity/            # MongoDB Entities (23 thực thể)
│   ├── enums/             # Các Enumeration
│   ├── repository/        # MongoDB Repositories
│   └── service/           # Business Logic Services
├── src/main/resources/
│   └── application.yaml   # Cấu hình ứng dụng
├── ENTITY_STRUCTURE.md    # Tài liệu chi tiết các thực thể
├── api-test.http          # Các request test API
└── pom.xml               # Maven dependencies
```

## 🗂️ Thực Thể Cơ Sở Dữ Liệu

### Các Module Chính (23 Thực Thể)
- **Module Người Dùng:** UserAuth, UserDetail, UserSetting, UserVerification, UserDevice
- **Module Bạn Bè:** FriendRequest, FriendShip, BlockUser
- **Module Tin Nhắn:** Message, MessageAttachment, MessageReaction, PinnedMessage
- **Module Hội Thoại:** Conversations, ConversationMember
- **Module Bài Viết:** Post, PostMedia, PostReaction, PostComment
- **Module Story:** Story, StoryView
- **Module Cuộc Gọi:** CallLog, CallParticipant
- **Module Thông Báo:** Notification

Xem [ENTITY_STRUCTURE.md](ENTITY_STRUCTURE.md) để biết chi tiết về mối quan hệ các thực thể.

## ⚙️ Cài Đặt & Khởi Chạy

### 1. Clone repository
```bash
git clone <repository-url>
cd fruvia
```

### 2. Cấu hình Environment Variables
Copy file `.env.example` thành `.env` và cập nhật các giá trị:
```bash
cp .env.example .env
```

Chỉnh sửa file `.env` với thông tin của bạn:
```env
# Database
MONGODB_HOST=localhost
MONGODB_PORT=27017
MONGODB_DATABASE=fruvia_db

# Email (Gmail App Password)
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password

# Cloudinary (for file storage)
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# Xem .env.example để biết tất cả các biến cần thiết
```

### 3. Cấu hình MongoDB
Đảm bảo MongoDB đang chạy trên `localhost:27017` hoặc cập nhật kết nối trong `.env`:
```env
MONGODB_HOST=localhost
MONGODB_PORT=27017
MONGODB_DATABASE=fruvia_db
```

### 4. Build dự án
```bash
mvn clean install
```

### 5. Chạy ứng dụng
```bash
mvn spring-boot:run
```

Server sẽ khởi động tại `http://localhost:8080/api/v1`

## 🔐 Xác Thực

### Cấu Hình JWT
- **Thuật toán:** HS512
- **Loại Token:** Bearer
- **Thời gian hết hạn:** 30 ngày
- **Claim:** scope (cho roles)

### Swagger API Documentation

Swagger UI đã được tích hợp để test và xem tài liệu API:
- **Swagger UI:** http://localhost:8080/api/v1/swagger-ui.html
- **API Docs:** http://localhost:8080/api/v1/v3/api-docs

Để sử dụng các endpoint cần xác thực trong Swagger:
1. Đăng nhập qua endpoint `/auth/login`
2. Copy `access_token` từ response
3. Click nút **"Authorize"** ở góc trên bên phải
4. Nhập token với format: `Bearer <your-token>`
5. Click **"Authorize"**

### Endpoints

#### Endpoints Công Khai
- `POST /api/v1/users` - Đăng ký người dùng mới
- `POST /api/v1/auth/login` - Đăng nhập
- `POST /api/v1/auth/introspect` - Xác thực token
- `POST /api/v1/auth/logout` - Đăng xuất

#### Endpoints Được Bảo Vệ
- `GET /api/v1/users/me` - Lấy thông tin người dùng hiện tại
- `GET /api/v1/users/{userId}` - Lấy thông tin người dùng theo ID (tạm thời public để test)

## 📝 Ví Dụ API

### Đăng Ký Người Dùng
```bash
POST /api/v1/users
Content-Type: application/json

{
  "phoneNumber": "0123456789",
  "email": "user@example.com",
  "password": "password123",
  "displayName": "Nguyễn Văn A",
  "firstName": "A",
  "lastName": "Nguyễn Văn"
}
```

### Đăng Nhập
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "password123"
}
```

### Phản Hồi
```json
{
  "access_token": "eyJhbGciOiJIUzUxMiJ9...",
  "expires_in": 2592000,
  "token_type": "Bearer"
}
```

## 🧪 Kiểm Thử

### Sử Dụng REST Client (VS Code)
Mở file `api-test.http` và chạy các request trực tiếp.

### Sử Dụng PowerShell
```powershell
.\test-registration.ps1
```

### Sử Dụng Bash
```bash
chmod +x test-registration.sh
./test-registration.sh
```

### Sử Dụng Batch (Windows)
```cmd
test-registration.bat
```

## 🔧 Cấu Hình

### Environment Variables (.env)
Dự án sử dụng file `.env` để quản lý các biến môi trường. Tất cả các biến cần thiết được liệt kê trong file `.env.example`.

**Các biến quan trọng:**

```env
# JWT Secret Key (Bắt buộc)
JWT_SIGNER_KEY=your-secret-key-here

# MongoDB
MONGODB_DATABASE=fruvia_db

# Email (Gmail App Password)
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password

# Cloudinary (File storage)
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# Google OAuth2
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret

# VNPay Payment
VNPAY_TMN_CODE=your-tmn-code
VNPAY_HASH_SECRET=your-hash-secret

# Gemini AI
GEMINI_API_KEY=your-gemini-api-key
```

### application.yaml
```yaml
server:
  port: 8080
  servlet:
    context-path: /api/v1

jwt:
  signer-key: "your-secret-key-here"

spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: fruvia_db
```

## 📊 Cài Đặt Quyền Riêng Tư Mặc Định

Khi người dùng đăng ký, các cài đặt quyền riêng tư mặc định được áp dụng:
- **Cho phép Lời mời kết bạn:** `true`
- **Hiển thị Hồ sơ:** `PUBLIC` (Công khai)
- **Hiển thị Bài viết:** `FRIEND_ONLY` (Chỉ bạn bè)
- **Quyền Gắn thẻ:** `FRIEND_ONLY` (Chỉ bạn bè)
- **Quyền Nhắn tin:** `PUBLIC` (Công khai)
- **Hiện Trạng thái online:** `true`
- **Hiện Thông báo đã xem:** `true`

## 🔒 Tính Năng Bảo Mật

- ✅ Mã hóa mật khẩu với BCrypt
- ✅ Xác thực dựa trên JWT token
- ✅ Hỗ trợ blacklist token (Redis)
- ✅ Cấu hình CORS
- ✅ Phân quyền dựa trên vai trò
- ✅ Hỗ trợ 2FA (đã chuẩn bị)

## 🌐 Cấu Hình CORS

Nguồn được phép:
- `http://localhost:5173`
- `http://localhost:3000`

## 📦 Thư Viện Phụ Thuộc

Các thư viện chính:
- Spring Boot Starter Web
- Spring Boot Starter Data MongoDB
- Spring Boot Starter Security
- Spring Boot Starter OAuth2 Resource Server
- Spring Boot Starter OAuth2 Authorization Server
- Spring Boot Starter Validation
- Spring Boot Starter WebSocket
- Spring Boot Starter Data Redis
- Lombok
- Spring Boot DevTools

## 🚧 TODO

- [ ] Triển khai Redis token blacklisting
- [ ] Thêm xác thực email
- [ ] Triển khai xác thực số điện thoại
- [ ] Thêm tính năng đặt lại mật khẩu
- [ ] Triển khai logic refresh token
- [x] ~~Thêm tài liệu API với Swagger~~ ✅ Hoàn thành
- [ ] Thêm integration tests
- [ ] Triển khai giới hạn tốc độ request (rate limiting)
- [ ] Thêm dịch vụ upload file
- [ ] Triển khai WebSocket cho chat thời gian thực

## 📄 Giấy Phép

Dự án này được cấp phép theo giấy phép MIT.

## 👥 Nhóm Phát Triển

Fruvia Development Team

## 📧 Liên Hệ

Để biết thêm thông tin hoặc hỗ trợ, vui lòng liên hệ: fruvia@example.com

---

**Phiên bản:** 1.0.0  
**Cập nhật lần cuối:** 20 tháng 1, 2026
