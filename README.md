# Fruvia Backend

A modern social media backend application built with Spring Boot 3.5.9, MongoDB, and JWT authentication.

## 🚀 Features

- ✅ JWT-based authentication & authorization
- ✅ User registration and management
- ✅ MongoDB database integration
- ✅ RESTful API design
- ✅ Security with Spring Security
- ✅ Complete social media entities (Posts, Messages, Stories, Calls, etc.)
- ✅ Friend management system
- ✅ Real-time messaging support
- ✅ Story features (24-hour content)
- ✅ Video/Audio call logging

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.6+
- MongoDB 4.4+
- Git

## 🛠️ Tech Stack

- **Framework:** Spring Boot 3.5.9
- **Language:** Java 21
- **Database:** MongoDB
- **Security:** Spring Security with JWT (HS512)
- **Build Tool:** Maven
- **Cache:** Redis (configured)

## 📁 Project Structure

```
fruvia/
├── src/main/java/iuh/fit/
│   ├── configuration/      # Security & App configurations
│   ├── controller/         # REST API Controllers
│   ├── dto/               # Data Transfer Objects
│   ├── entity/            # MongoDB Entities (23 entities)
│   ├── enums/             # Enumerations
│   ├── repository/        # MongoDB Repositories
│   └── service/           # Business Logic Services
├── src/main/resources/
│   └── application.yaml   # Application configurations
├── ENTITY_STRUCTURE.md    # Complete entity documentation
├── api-test.http          # API test requests
└── pom.xml               # Maven dependencies
```

## 🗂️ Database Entities

### Core Modules (23 Entities)
- **User Module:** UserAuth, UserDetail, UserSetting, UserVerification, UserDevice
- **Friend Module:** FriendRequest, FriendShip, BlockUser
- **Message Module:** Message, MessageAttachment, MessageReaction, PinnedMessage
- **Conversation Module:** Conversations, ConversationMember
- **Post Module:** Post, PostMedia, PostReaction, PostComment
- **Story Module:** Story, StoryView
- **Call Module:** CallLog, CallParticipant
- **Notification Module:** Notification

See [ENTITY_STRUCTURE.md](ENTITY_STRUCTURE.md) for detailed entity relationships.

## ⚙️ Installation & Setup

### 1. Clone the repository
```bash
git clone <repository-url>
cd fruvia
```

### 2. Configure MongoDB
Ensure MongoDB is running on `localhost:27017` or update connection in `application.yaml`:
```yaml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: fruvia_db
```

### 3. Build the project
```bash
mvn clean install
```

### 4. Run the application
```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8080/api/v1`

## 🔐 Authentication

### JWT Configuration
- **Algorithm:** HS512
- **Token Type:** Bearer
- **Expiration:** 30 days
- **Claim:** scope (for roles)

### Endpoints

#### Public Endpoints
- `POST /api/v1/users` - Register new user
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/introspect` - Validate token
- `POST /api/v1/auth/logout` - User logout

#### Protected Endpoints
- `GET /api/v1/users/me` - Get current user profile
- `GET /api/v1/users/{userId}` - Get user by ID (temporary public for testing)

## 📝 API Examples

### Register User
```bash
POST /api/v1/users
Content-Type: application/json

{
  "phoneNumber": "0123456789",
  "email": "user@example.com",
  "password": "password123",
  "displayName": "John Doe",
  "firstName": "John",
  "lastName": "Doe"
}
```

### Login
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "password123"
}
```

### Response
```json
{
  "access_token": "eyJhbGciOiJIUzUxMiJ9...",
  "expires_in": 2592000,
  "token_type": "Bearer"
}
```

## 🧪 Testing

### Using REST Client (VS Code)
Open `api-test.http` and run the requests directly.

### Using PowerShell
```powershell
.\test-registration.ps1
```

### Using Bash
```bash
chmod +x test-registration.sh
./test-registration.sh
```

### Using Batch (Windows)
```cmd
test-registration.bat
```

## 🔧 Configuration

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

## 📊 Default Privacy Settings

When a user registers, default privacy settings are applied:
- **Allow Friend Requests:** `true`
- **Profile Visibility:** `PUBLIC`
- **Post Visibility:** `FRIEND_ONLY`
- **Tag Permissions:** `FRIEND_ONLY`
- **Message Permissions:** `PUBLIC`
- **Show Online Status:** `true`
- **Show Read Receipts:** `true`

## 🔒 Security Features

- ✅ Password hashing with BCrypt
- ✅ JWT token-based authentication
- ✅ Token blacklisting support (Redis)
- ✅ CORS configuration
- ✅ Role-based access control
- ✅ 2FA support (prepared)

## 🌐 CORS Configuration

Allowed origins:
- `http://localhost:5173`
- `http://localhost:3000`

## 📦 Dependencies

Key dependencies:
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

- [ ] Implement Redis token blacklisting
- [ ] Add email verification
- [ ] Implement phone number verification
- [ ] Add password reset functionality
- [ ] Implement refresh token logic
- [ ] Add API documentation with Swagger
- [ ] Add integration tests
- [ ] Implement rate limiting
- [ ] Add file upload service
- [ ] WebSocket implementation for real-time chat

## 📄 License

This project is licensed under the MIT License.

## 👥 Team

Fruvia Development Team

## 📧 Contact

For questions or support, please contact: fruvia@example.com

---

**Version:** 1.0.0  
**Last Updated:** January 20, 2026
