# Fruvia Backend Documentation

> 📚 **Comprehensive Documentation for Fruvia Chat Backend**  
> 🚀 **Built with Spring Boot 3.x + Spring Security + JWT**

---

## 📖 Documentation Index

### Core Documentation
| Document | Description |
|----------|-------------|
| [Code Structure](./CODE_STRUCTURE.md) | 📂 Project architecture & package organization |
| [Security Architecture](./SECURITY_ARCHITECTURE.md) | 🔐 Authentication, JWT, authorization flow |
| [CORS Configuration](./CORS_CONFIG.md) | 🌐 Cross-origin setup for web & mobile |
| [Utils Documentation](./UTILS_DOCUMENTATION.md) | 🛠️ Utility classes and helper methods |

---

## 🎯 Quick Start

### Prerequisites
- ☕ **Java 17+**
- 📦 **Maven 3.8+**
- 🗄️ **PostgreSQL** or **MySQL**
- 🔑 **Cloudinary Account** (for file uploads)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd backend/fruvia
   ```

2. **Configure `application.yaml`**
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

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

4. **Access Swagger UI**
   ```
   http://localhost:8080/api/v1/swagger-ui.html
   ```

---

## 📂 Project Structure Overview

```
fruvia/
├── src/main/java/iuh/fit/
│   ├── configuration/      # 🔧 Spring configs (Security, CORS, Cloudinary)
│   ├── controller/         # 🎮 REST API endpoints
│   ├── dto/                # 📦 Request/Response models
│   ├── entity/             # 🗄️ JPA entities (database models)
│   ├── enums/              # 🏷️ Enum types
│   ├── mapper/             # 🔄 DTO ↔ Entity converters
│   ├── repository/         # 💾 Database access layer
│   ├── service/            # 💼 Business logic
│   └── utils/              # 🛠️ Utility classes
├── src/main/resources/
│   ├── application.yaml    # ⚙️ Configuration
│   └── templates/          # 📧 Email templates
├── docs/                   # 📚 This documentation
├── test_api/               # 🧪 API test scripts
└── pom.xml                 # 📋 Maven dependencies
```

**Detailed breakdown**: [Code Structure](./CODE_STRUCTURE.md)

---

## 🔐 Authentication & Security

### JWT-based Authentication
- ✅ Stateless authentication
- ✅ Role-based authorization (ROLE_USER, ROLE_ADMIN)
- ✅ BCrypt password hashing
- ✅ HMAC-SHA512 JWT signing

### Authentication Flow
```
1. POST /auth/login → Get JWT token
2. Include token in requests: Authorization: Bearer <token>
3. Backend validates token on every request
4. Token expires after configured time
5. Refresh token endpoint for renewal
```

**Full details**: [Security Architecture](./SECURITY_ARCHITECTURE.md)

---

## 🌐 CORS Configuration

### Supported Origins
- 🖥️ **Web**: `http://localhost:5173` (Vite)
- 📱 **Mobile**: `http://localhost:8081` (Expo)
- 🌍 **LAN Testing**: `http://192.168.1.100:8081`

### Key Settings
```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:5173
  allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
  allowed-headers: *
  exposed-headers: Authorization,Content-Type,X-Total-Count,X-User-Id
  allow-credentials: true
  max-age: 3600
```

**Full details**: [CORS Configuration](./CORS_CONFIG.md)

---

## 🛠️ Utility Classes

### Available Utils
| Class | Purpose |
|-------|---------|
| `DateTimeUtils` | Format timestamps for chat ("14:30", "Hôm qua 14:30") |
| `JwtUtils` | Extract user info from JWT (userId, username, roles) |
| `ValidationUtils` | Validate emails, phones, passwords, messages |
| `MessageUtils` | Message preview, URL detection, mentions |
| `FileUtils` | File validation, size formatting, unique names |

**Usage examples**: [Utils Documentation](./UTILS_DOCUMENTATION.md)

---

## 📡 API Endpoints

### Public Endpoints (No Auth)
```
POST   /auth/login           # User login
POST   /auth/register        # User registration
POST   /auth/refresh-token   # Refresh JWT
GET    /swagger-ui.html      # API documentation
```

### Protected Endpoints (Requires JWT)
```
# Users
GET    /users/{id}           # Get user profile
PUT    /users/{id}           # Update user
DELETE /users/{id}           # Delete user

# Messages
GET    /messages             # List messages
POST   /messages             # Send message
DELETE /messages/{id}        # Delete message

# Conversations
GET    /conversations        # List conversations
POST   /conversations        # Create conversation
GET    /conversations/{id}   # Get conversation details
```

---

## 🧪 Testing

### Test with cURL
```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password123"}'

# Get user (with token)
curl http://localhost:8080/api/v1/users/123 \
  -H "Authorization: Bearer eyJhbGc..."
```

### Test with Swagger UI
1. Open http://localhost:8080/api/v1/swagger-ui.html
2. Click "Authorize" button
3. Enter token: `Bearer <your-jwt-token>`
4. Test endpoints directly in browser

### Test Files
Check `test_api/` folder for `.http` files:
```
test_api/
├── api-test.http            # General API tests
└── api-test-file-upload.http # File upload tests
```

---

## 🔧 Configuration

### Environment Variables
Set these in production:
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

### Core Dependencies
- **Spring Boot 3.x** - Framework
- **Spring Security** - Authentication & authorization
- **Spring Data JPA** - Database ORM
- **PostgreSQL/MySQL Driver** - Database connection
- **Lombok** - Reduce boilerplate code
- **Validation** - Input validation
- **Cloudinary** - File upload service

### See `pom.xml` for complete list

---

## 🚀 Deployment

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

## 🐛 Troubleshooting

### Common Issues

**1. CORS Error**
- ✅ Check `application.yaml` → `cors.allowed-origins`
- ✅ Add your frontend URL to the list
- 📚 See: [CORS Configuration](./CORS_CONFIG.md)

**2. 401 Unauthorized**
- ✅ Check JWT token in `Authorization` header
- ✅ Verify token format: `Bearer <token>`
- ✅ Check token expiration
- 📚 See: [Security Architecture](./SECURITY_ARCHITECTURE.md)

**3. Database Connection Failed**
- ✅ Verify database is running
- ✅ Check credentials in `application.yaml`
- ✅ Test connection: `psql -U username -d database`

**4. File Upload Failed**
- ✅ Check Cloudinary credentials
- ✅ Verify file size limits (10MB images, 50MB files)
- 📚 See: [Utils Documentation](./UTILS_DOCUMENTATION.md#fileutils)

---

## 📚 Additional Resources

### External Documentation
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Spring Security Docs](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io/) - JWT debugger

### Related Files
- `../README.md` - Main project README
- `../ENTITY_STRUCTURE.md` - Database entity relationships
- `../FILE_MANAGEMENT.md` - File upload guidelines

---

## 🤝 Contributing

### Code Style
- Use **4 spaces** for indentation
- Follow **Spring conventions**
- Write **JavaDoc** for public methods
- Add **@author** tags to new classes

### Pull Request Process
1. Create feature branch: `git checkout -b feature/new-feature`
2. Commit changes: `git commit -m "feat: add new feature"`
3. Push to branch: `git push origin feature/new-feature`
4. Create Pull Request on GitHub

---

## 📝 Changelog

### v1.0.0 - January 2026
- ✅ Initial release
- ✅ JWT authentication
- ✅ User management
- ✅ Chat messaging
- ✅ File upload (Cloudinary)
- ✅ CORS configuration
- ✅ Comprehensive documentation

---

## 👥 Team

**Fruvia Development Team**  
📧 Contact: [team@fruvia.com](mailto:team@fruvia.com)  
🌐 Website: https://fruvia.com

---

## 📄 License

This project is proprietary and confidential.  
© 2026 Fruvia Team. All rights reserved.

---

**Need help?** Check the documentation links above or contact the development team.
