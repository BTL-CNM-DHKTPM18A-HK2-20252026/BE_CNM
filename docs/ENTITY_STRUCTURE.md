# Fruvia Backend - Entity Structure Documentation

## 📋 Mục Lục
- [Tổng Quan](#tổng-quan)
- [Danh Sách Entities](#danh-sách-entities)
- [Chi Tiết Entities và Mối Quan Hệ](#chi-tiết-entities-và-mối-quan-hệ)
- [Enumerations](#enumerations)
- [Database Collections](#database-collections)

---

## 🎯 Tổng Quan

Hệ thống Fruvia Backend được thiết kế theo mô hình mạng xã hội với các tính năng:
- Quản lý người dùng và xác thực
- Kết bạn và quản lý mối quan hệ
- Nhắn tin (chat) cá nhân và nhóm
- Đăng bài và tương tác (post, comment, reaction)
- Stories (24 giờ)
- Cuộc gọi (audio/video)
- Thông báo

**Database:** MongoDB

---

## 📚 Danh Sách Entities

### 👤 User Module (5 entities)
1. **UserAuth** - Xác thực và bảo mật
2. **UserDetail** - Thông tin hồ sơ người dùng
3. **UserSetting** - Cài đặt riêng tư
4. **UserVerification** - Mã xác thực OTP
5. **UserDevice** - Quản lý thiết bị

### 👥 Friend Module (3 entities)
6. **FriendRequest** - Lời mời kết bạn
7. **FriendShip** - Quan hệ bạn bè
8. **BlockUser** - Chặn người dùng

### 💬 Message Module (4 entities)
9. **Message** - Tin nhắn
10. **MessageAttachment** - File đính kèm
11. **MessageReaction** - Reaction tin nhắn
12. **PinnedMessage** - Tin nhắn được ghim

### 🗨️ Conversation Module (2 entities)
13. **Conversations** - Cuộc hội thoại
14. **ConversationMember** - Thành viên cuộc hội thoại

### 📝 Post Module (4 entities)
15. **Post** - Bài đăng
16. **PostMedia** - Media đính kèm bài đăng
17. **PostReaction** - Reaction bài đăng
18. **PostComment** - Comment bài đăng

### 📖 Story Module (2 entities)
19. **Story** - Câu chuyện 24h
20. **StoryView** - Lượt xem story

### 📞 Call Module (2 entities)
21. **CallLog** - Lịch sử cuộc gọi
22. **CallParticipant** - Người tham gia cuộc gọi

### 🔔 Notification Module (1 entity)
23. **Notification** - Thông báo

---

## 🔗 Chi Tiết Entities và Mối Quan Hệ

### 1️⃣ User Module

#### UserAuth
**Collection:** `user_auth`

**Mối quan hệ:**
- `1:1` → UserDetail (cùng userId)
- `1:1` → UserSetting (cùng userId)
- `1:N` → UserVerification
- `1:N` → UserDevice
- `1:N` → FriendRequest (sender/receiver)
- `1:N` → FriendShip
- `1:N` → BlockUser (blocker/blocked)
- `1:N` → Message (sender)
- `1:N` → ConversationMember
- `1:N` → Post (author)
- `1:N` → Story (author)
- `1:N` → Notification (receiver/actor)

**Fields:**
```java
String userId;              // PK
String phoneNumber;
String email;
String passwordHash;
String salt;
AccountStatus accountStatus; // ACTIVE, LOCKED, BANNED
Boolean isTwoFactorEnabled;
LocalDateTime createdAt;
LocalDateTime updatedAt;
LocalDateTime lastLoginAt;
Boolean isDeleted;
```

---

#### UserDetail
**Collection:** `user_detail`

**Mối quan hệ:**
- `1:1` → UserAuth (cùng userId)

**Fields:**
```java
String userId;              // PK, FK → UserAuth
String displayName;
String firstName;
String lastName;
String avatarUrl;
String coverPhotoUrl;
String bio;
Date dob;
String gender;
String address;
String city;
String education;
String workplace;
Boolean isOrgActive;
LocalDateTime orgCode;
LocalDateTime lastUpdateProfile;
```

---

#### UserSetting
**Collection:** `user_setting`

**Mối quan hệ:**
- `1:1` → UserAuth (cùng userId)

**Fields:**
```java
String userId;                       // PK, FK → UserAuth
Boolean allowFriendRequests;
PrivacyLevel whoCanSeeProfile;       // ADMIN, PUBLIC, FRIEND_ONLY
PrivacyLevel whoCanSeePost;
PrivacyLevel whoCanTagMe;
PrivacyLevel whoCanSendMessages;
Boolean showOnlineStatus;
Boolean showReadReceipts;
```

---

#### UserVerification
**Collection:** `user_verification`

**Mối quan hệ:**
- `N:1` → UserAuth

**Fields:**
```java
String verificationId;      // PK
String userId;              // FK → UserAuth
String otpCode;
VerificationType type;      // EMAIL, PHONE, TWO_FA
LocalDateTime expiresAt;
Boolean isUsed;
LocalDateTime createdAt;
```

---

#### UserDevice
**Collection:** `user_device`

**Mối quan hệ:**
- `N:1` → UserAuth

**Fields:**
```java
String deviceId;            // PK
String userId;              // FK → UserAuth
String deviceName;
String deviceType;          // iOS, Android, Web
String deviceOs;
String fcmToken;            // Firebase Cloud Messaging
String authTokenHash;
LocalDateTime lastActiveAt;
LocalDateTime createdAt;
Boolean isActive;
```

---

### 2️⃣ Friend Module

#### FriendRequest
**Collection:** `friend_request`

**Mối quan hệ:**
- `N:1` → UserAuth (senderId)
- `N:1` → UserAuth (receiverId)

**Fields:**
```java
String id;                  // PK
String requestId;
String senderId;            // FK → UserAuth
String receiverId;          // FK → UserAuth
RequestStatus status;       // PENDING, ACCEPTED, REJECTED
String message;
LocalDateTime sentAt;
LocalDateTime responseAt;
LocalDateTime expiredAt;
```

---

#### FriendShip
**Collection:** `friend_ship`

**Mối quan hệ:**
- `N:1` → UserAuth (userId1)
- `N:1` → UserAuth (userId2)

**Fields:**
```java
String id;                  // PK
String userId1;             // FK → UserAuth
String userId2;             // FK → UserAuth
LocalDateTime createdAt;
```

**Note:** Mối quan hệ bạn bè là bidirectional (2 chiều)

---

#### BlockUser
**Collection:** `block_user`

**Mối quan hệ:**
- `N:1` → UserAuth (blockerId)
- `N:1` → UserAuth (blockedId)

**Fields:**
```java
String id;                  // PK
String blockerId;           // FK → UserAuth (who blocked)
String blockedId;           // FK → UserAuth (who was blocked)
LocalDateTime blockedAt;
String reason;
Boolean blockMessages;
Boolean blockCalls;
Boolean hidePosts;
```

---

### 3️⃣ Message Module

#### Message
**Collection:** `message`

**Mối quan hệ:**
- `N:1` → Conversations
- `N:1` → UserAuth (senderId)
- `N:1` → Message (replyToId - self reference)
- `1:N` → MessageAttachment
- `1:N` → MessageReaction

**Fields:**
```java
String messageId;           // PK
String conversationId;      // FK → Conversations
String senderId;            // FK → UserAuth
MessageType type;           // TEXT, IMAGE, VIDEO, MEDIA
String content;
String replyToId;           // FK → Message (nullable)
LocalDateTime createdAt;
Boolean isDeleted;
Boolean isRecalled;
```

---

#### MessageAttachment
**Collection:** `message_attachment`

**Mối quan hệ:**
- `N:1` → Message

**Fields:**
```java
String attachmentId;        // PK
String messageId;           // FK → Message
String url;
String fileName;
Long fileSize;
String thumbnailUrl;
```

---

#### MessageReaction
**Collection:** `message_reaction`

**Mối quan hệ:**
- `N:1` → Message
- `N:1` → UserAuth (userId)

**Composite Key:** (messageId, userId)

**Fields:**
```java
String messageId;           // PK, FK → Message
String userId;              // PK, FK → UserAuth
String icon;                // Emoji
LocalDateTime createdAt;
Integer quantity;
```

---

#### PinnedMessage
**Collection:** `pinned_message`

**Mối quan hệ:**
- `N:1` → Message
- `N:1` → Conversations

**Fields:**
```java
String id;                  // PK
String messageId;           // FK → Message
String conversationId;      // FK → Conversations
String type;
LocalDateTime pinnedAt;
```

---

### 4️⃣ Conversation Module

#### Conversations
**Collection:** `conversations`

**Mối quan hệ:**
- `1:N` → Message
- `1:N` → ConversationMember
- `N:1` → UserAuth (creatorId)
- `N:1` → Message (lastMessageId)

**Fields:**
```java
String conversationId;      // PK
ConversationType type;      // PRIVATE, GROUP
String conversationName;    // For groups
String avatarUrl;           // For groups
String creatorId;           // FK → UserAuth
LocalDateTime createdAt;
String lastMessageId;       // FK → Message
Boolean isPinned;
String groupDescription;
LocalDateTime updatedAt;
```

---

#### ConversationMember
**Collection:** `conversation_member`

**Mối quan hệ:**
- `N:1` → Conversations
- `N:1` → UserAuth

**Composite Key:** (conversationId, userId)

**Fields:**
```java
String conversationId;      // PK, FK → Conversations
String userId;              // PK, FK → UserAuth
MemberRole role;            // ADMIN, DEPUTY, MEMBER
LocalDateTime joinedAt;
String nickname;            // Custom nickname in conversation
```

---

### 5️⃣ Post Module

#### Post
**Collection:** `post`

**Mối quan hệ:**
- `N:1` → UserAuth (userId - author)
- `1:N` → PostMedia
- `1:N` → PostReaction
- `1:N` → PostComment

**Fields:**
```java
String postId;              // PK
String userId;              // FK → UserAuth
String content;
PrivacyLevel privacy;       // ADMIN, PUBLIC, FRIEND_ONLY
LocalDateTime createdAt;
LocalDateTime updatedAt;
Boolean isDeleted;
String location;
Integer commentCount;       // Denormalized
```

---

#### PostMedia
**Collection:** `post_media`

**Mối quan hệ:**
- `N:1` → Post

**Fields:**
```java
String mediaId;             // PK
String postId;              // FK → Post
String url;
String type;                // IMAGE, VIDEO
LocalDateTime createdAt;
```

---

#### PostReaction
**Collection:** `post_reaction`

**Mối quan hệ:**
- `N:1` → Post
- `N:1` → UserAuth (userId)

**Composite Key:** (postId, userId)

**Fields:**
```java
String postId;              // PK, FK → Post
String userId;              // PK, FK → UserAuth
String icon;                // like, love, haha, etc.
LocalDateTime createdAt;
```

---

#### PostComment
**Collection:** `post_comment`

**Mối quan hệ:**
- `N:1` → Post
- `N:1` → UserAuth (userId - commenter)
- `N:1` → PostComment (parentCommentId - self reference)

**Fields:**
```java
String commentId;           // PK
String postId;              // FK → Post
String userId;              // FK → UserAuth
String content;
String parentCommentId;     // FK → PostComment (nullable)
LocalDateTime createdAt;
```

**Note:** Hỗ trợ nested comments (comment trả lời comment)

---

### 6️⃣ Story Module

#### Story
**Collection:** `story`

**Mối quan hệ:**
- `N:1` → UserAuth (userId - author)
- `1:N` → StoryView

**Fields:**
```java
String storyId;             // PK
String userId;              // FK → UserAuth
String mediaUrl;
String type;                // IMAGE, VIDEO, TEXT
Integer duration;           // seconds
Date expiresAt;             // 24 hours from creation
PrivacyLevel privacy;       // Who can see
String content;             // For text stories
```

---

#### StoryView
**Collection:** `story_view`

**Mối quan hệ:**
- `N:1` → Story
- `N:1` → UserAuth (userId - viewer)

**Composite Key:** (storyId, userId)

**Fields:**
```java
String storyId;             // PK, FK → Story
String userId;              // PK, FK → UserAuth
Date viewedAt;
String reaction;            // Optional emoji reaction
```

---

### 7️⃣ Call Module

#### CallLog
**Collection:** `call_log`

**Mối quan hệ:**
- `N:1` → UserAuth (callerId)
- `N:1` → Conversations
- `1:N` → CallParticipant

**Fields:**
```java
String callId;              // PK
String callerId;            // FK → UserAuth
String conversationId;      // FK → Conversations
String type;                // AUDIO, VIDEO
String status;              // COMPLETED, MISSED, REJECTED, CANCELLED
Date startedAt;
Date endedAt;
Integer durationSeconds;
```

---

#### CallParticipant
**Collection:** `call_participant`

**Mối quan hệ:**
- `N:1` → CallLog
- `N:1` → UserAuth (userId)

**Composite Key:** (callId, userId)

**Fields:**
```java
String callId;              // PK, FK → CallLog
String userId;              // PK, FK → UserAuth
Date joinedAt;
Date leftAt;
Date startedAt;
Date endedAt;
Integer durationSeconds;    // Individual duration
```

---

### 8️⃣ Notification Module

#### Notification
**Collection:** `notification`

**Mối quan hệ:**
- `N:1` → UserAuth (userId - receiver)
- `N:1` → UserAuth (actorId - who triggered)
- Dynamic reference to entityId based on type

**Fields:**
```java
String notificationId;      // PK
String userId;              // FK → UserAuth (receiver)
String actorId;             // FK → UserAuth (actor)
String entityId;            // FK to various entities (Post, FriendRequest, etc.)
NotificationType type;      // FRIEND_REQ, LIKE_POST
Boolean isRead;
```

**Note:** entityId có thể reference đến:
- Post (khi type = LIKE_POST)
- FriendRequest (khi type = FRIEND_REQ)
- Comment, etc.

---

## 🏷️ Enumerations

### NotificationType
```java
FRIEND_REQ,  // Friend request
LIKE_POST    // Post like
```

### PrivacyLevel
```java
ADMIN,       // Admin only
PUBLIC,      // Everyone
FRIEND_ONLY  // Friends only
```

### ConversationType
```java
PRIVATE,     // 1-1 chat
GROUP        // Group chat
```

### AccountStatus
```java
ACTIVE,      // Active account
LOCKED,      // Temporarily locked
BANNED       // Permanently banned
```

### RequestStatus
```java
PENDING,     // Waiting for response
ACCEPTED,    // Accepted
REJECTED     // Rejected
```

### MessageType
```java
TEXT,        // Text message
IMAGE,       // Image
VIDEO,       // Video
MEDIA        // Other media
```

### MemberRole
```java
ADMIN,       // Group admin
DEPUTY,      // Deputy admin
MEMBER       // Regular member
```

### VerificationType
```java
EMAIL,       // Email verification
PHONE,       // Phone verification
TWO_FA       // Two-factor auth
```

---

## 📊 Database Collections Summary

| Collection | Primary Key | Indexes Recommended |
|-----------|-------------|---------------------|
| user_auth | userId | email, phoneNumber |
| user_detail | userId | displayName |
| user_setting | userId | - |
| user_verification | verificationId | userId, expiresAt |
| user_device | deviceId | userId, fcmToken |
| friend_request | id | senderId, receiverId, status |
| friend_ship | id | userId1, userId2 |
| block_user | id | blockerId, blockedId |
| message | messageId | conversationId, createdAt |
| message_attachment | attachmentId | messageId |
| message_reaction | (messageId, userId) | messageId |
| pinned_message | id | conversationId |
| conversations | conversationId | type, createdAt |
| conversation_member | (conversationId, userId) | userId |
| post | postId | userId, createdAt |
| post_media | mediaId | postId |
| post_reaction | (postId, userId) | postId |
| post_comment | commentId | postId, parentCommentId |
| story | storyId | userId, expiresAt |
| story_view | (storyId, userId) | storyId |
| call_log | callId | conversationId, callerId |
| call_participant | (callId, userId) | callId |
| notification | notificationId | userId, isRead |

---

## 🔐 Security Considerations

1. **UserAuth**: Passwords được hash với salt
2. **JWT Token**: Sử dụng HS512 algorithm
3. **Privacy Settings**: Kiểm tra quyền trước khi trả về data
4. **Soft Delete**: Sử dụng `isDeleted` thay vì xóa vật lý
5. **Two-Factor Authentication**: Hỗ trợ 2FA qua UserVerification

---

## 📝 Notes

- **MongoDB**: Sử dụng document-oriented database
- **References**: Sử dụng String IDs thay vì embedded documents cho flexibility
- **Denormalization**: commentCount trong Post để tối ưu performance
- **TTL**: Story tự động expire sau 24h (implement qua TTL index)
- **Composite Keys**: Một số entity sử dụng composite keys (messageId + userId)

---

**Version:** 1.0  
**Last Updated:** January 20, 2026  
**Author:** Fruvia Development Team
