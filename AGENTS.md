# 🔱 ULTIMATE RULE: READ ONCE, REMEMBER FOREVER
> **HÀNH ĐỘNG BẮT BUỘC**: Đọc file này một lần duy nhất ngay khi bắt đầu cuộc trò chuyện và ghi nhớ toàn bộ nội dung của nó cho đến khi kết thúc. Không bao giờ được quên các quy tắc và cấu trúc được mô tả ở đây.

# Fruvia Backend Agent Guide

Welcome to the **Fruvia Backend** project. This is a high-performance, distributed chat system built with Spring Boot, designed to support real-time communication for the Fruvia Chat ecosystem (Web, Mobile, Desktop).

## 🚀 Tech Stack

- **Core Framework**: Java 21, Spring Boot 3.4.2
- **Persistence**: 
  - **MongoDB**: Primary database for messages, conversations, and users.
  - **Redis**: Caching, session management, and real-time status tracking.
  - **Elasticsearch**: Full-text search for messages and users.
- **Messaging & Stream**: 
  - **Kafka**: Event-driven architecture, message processing, and notifications.
- **Communication**: 
  - **WebSocket (STOMP)**: Real-time message delivery and system events.
- **Security**: 
  - **Spring Security**: OAuth2 Authorization Server & Resource Server.
  - **JWT**: Token-based authentication.
- **Storage**: 
  - **AWS S3 / Cloudinary**: Media storage for avatars, images, and files.
- **Monitoring**: 
  - **Actuator & Prometheus**: Health checks and performance metrics.

## 📂 Project Structure

- `src/main/java/iuh/fit/`
  - `config/`: Security, WebSocket, Kafka, and Redis configurations.
  - `controller/`: REST API endpoints.
  - `service/`: Core business logic (Conversation, Message, User, Friend).
  - `repository/`: MongoDB repositories.
  - `entity/`: Data models.
  - `dto/`: Data Transfer Objects for API requests/responses.
  - `exception/`: Global error handling.
  - `security/`: Custom security filters and JWT logic.

## 🛠 Essential APIs

- **Auth**: Login, Register, Password Reset.
- **Chat**: 
  - `GET /conversations`: List user conversations.
  - `POST /messages`: Send a message.
  - `GET /messages/{chatId}`: Fetch message history with pagination.
- **Groups**: 
  - `POST /conversations/group`: Create group.
  - `POST /conversations/join/{id}`: Join group via invitation link.
- **Friends**: 
  - `POST /friends/request`: Send friend request.
  - `GET /friends`: List friends.

## 💡 Developer Notes

- **Kafka Events**: Most write operations (like sending a message) trigger a Kafka event to ensure asynchronous processing (e.g., updating unread counts, sending push notifications).
- **WebSocket**: Use the `/topic/messages` prefix for real-time delivery.
- **Indexing**: Ensure critical fields in MongoDB (like `senderId`, `receiverId`, `chatId`) are indexed to maintain performance.
- **Documentation**: Access Swagger UI at `/swagger-ui/index.html` for API testing.

## 🛠 Build & Test Commands

- **Run Development**: `./mvnw spring-boot:run`
- **Build Project**: `./mvnw clean install -DskipTests`
- **Run Tests**: `./mvnw test`
- **Docker Deployment**: `docker-compose up --build`

## 📏 Code Convention

- **Naming**: 
  - Classes: `PascalCase` (e.g., `ConversationService`)
  - Methods/Variables: `camelCase` (e.g., `joinGroup`, `isPrivate`)
  - Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY`)
- **Format**: Follow Google Java Style Guide. Use Lombok to reduce boilerplate.
- **DTOs**: Always use DTOs for API requests/responses; never expose Entities directly.

## ⚠️ Important Rules

1. **NO FORCE PUSH**: Never force push to protected branches.
2. **ENV SAFETY**: Never commit `.env` files. Update `.env.example` when adding new variables.
3. **MIGRATIONS**: Be extremely careful when modifying MongoDB schemas to avoid data loss.
4. **DOCUMENTATION**: Every new API must be documented with `@Operation` and `@ApiResponse` for Swagger.

---
*Maintained by Fruvia AI Agents.*
