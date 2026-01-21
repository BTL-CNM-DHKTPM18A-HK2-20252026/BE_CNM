# File Management System

Hệ thống quản lý file upload cho Fruvia Backend, hỗ trợ upload ảnh, video, tài liệu lên Cloudinary và các dịch vụ lưu trữ khác.

## 🗂️ Cấu Trúc

### Entity: FileUpload
Lưu trữ thông tin về các file đã upload:
- **fileId**: ID duy nhất của file
- **originalFileName**: Tên file gốc
- **storedFileName**: Tên file đã lưu
- **fileType**: Loại file (IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER)
- **mimeType**: MIME type (image/jpeg, video/mp4, etc.)
- **fileSize**: Kích thước file (bytes)
- **fileUrl**: URL công khai để truy cập
- **thumbnailUrl**: URL thumbnail (cho ảnh/video)
- **storageProvider**: Nơi lưu trữ (CLOUDINARY, FIREBASE, AWS_S3, etc.)
- **resourceId**: Public ID từ storage provider
- **folderPath**: Đường dẫn folder lưu trữ
- **uploadedBy**: ID người upload
- **uploadedAt**: Thời gian upload
- **description**: Mô tả file
- **tags**: Tags để tìm kiếm
- **status**: Trạng thái (ACTIVE, DELETED, ARCHIVED)

### Enums

#### FileType
- `IMAGE`: Ảnh (jpg, png, gif, webp)
- `VIDEO`: Video (mp4, avi, mov)
- `AUDIO`: Audio (mp3, wav)
- `DOCUMENT`: Tài liệu (pdf, doc, txt)
- `ARCHIVE`: File nén (zip, rar)
- `OTHER`: Các loại khác

#### StorageProvider
- `CLOUDINARY`: Cloudinary service
- `FIREBASE`: Firebase Storage
- `AWS_S3`: Amazon S3
- `AZURE_BLOB`: Azure Blob Storage
- `GOOGLE_DRIVE`: Google Drive
- `LOCAL`: Local file system
- `OTHER`: Các dịch vụ khác

## 🎯 API Endpoints

### 1. Upload Single File
```http
POST /api/v1/files/upload
Content-Type: multipart/form-data
Authorization: Bearer {token}

Parameters:
- file: MultipartFile (required)
- folderPath: String (optional, default: "general")
- description: String (optional)
- tags: String[] (optional)
- generateThumbnail: Boolean (optional, default: false)
- optimize: Boolean (optional, default: true)
```

**Response:**
```json
{
  "fileId": "uuid",
  "originalFileName": "image.jpg",
  "storedFileName": "fruvia/avatars/abc123",
  "fileType": "IMAGE",
  "mimeType": "image/jpeg",
  "fileSize": 1024000,
  "fileUrl": "https://res.cloudinary.com/.../image.jpg",
  "thumbnailUrl": null,
  "storageProvider": "CLOUDINARY",
  "resourceId": "fruvia/avatars/abc123",
  "folderPath": "avatars",
  "uploadedBy": "user-id",
  "uploadedAt": "2026-01-20T21:00:00",
  "status": "ACTIVE"
}
```

### 2. Upload Multiple Files
```http
POST /api/v1/files/upload/multiple
Content-Type: multipart/form-data
Authorization: Bearer {token}

Parameters:
- files: MultipartFile[] (required)
- folderPath, description, tags, generateThumbnail, optimize (same as single upload)
```

### 3. Get File by ID
```http
GET /api/v1/files/{fileId}
```

### 4. Get My Files
```http
GET /api/v1/files/my-files
Authorization: Bearer {token}
```

### 5. Delete File
```http
DELETE /api/v1/files/{fileId}
Authorization: Bearer {token}
```

### 6. Delete Multiple Files
```http
DELETE /api/v1/files/batch
Content-Type: application/json
Authorization: Bearer {token}

Body:
["file-id-1", "file-id-2", "file-id-3"]
```

### 7. Get Temporary URL
```http
GET /api/v1/files/{fileId}/temporary-url?duration=3600
```

## 🔧 Configuration

### Environment Variables (.env)
```env
# Cloudinary
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# File Upload
FILE_MAX_SIZE=10MB
```

### application.yaml
```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: ${FILE_MAX_SIZE:10MB}
      max-request-size: ${FILE_MAX_SIZE:10MB}

cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME}
  api-key: ${CLOUDINARY_API_KEY}
  api-secret: ${CLOUDINARY_API_SECRET}
```

## 📁 Folder Structure
Các folder phổ biến để lưu trữ file:
- `avatars`: Ảnh đại diện người dùng
- `posts`: Media trong bài viết
- `messages`: File trong tin nhắn
- `stories`: Media trong story
- `documents`: Tài liệu
- `general`: Mặc định cho các file khác

## 🚀 Features

### ✅ Đã Triển Khai
- Upload single/multiple files lên Cloudinary
- Hỗ trợ nhiều loại file (IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE)
- Tự động phát hiện file type từ MIME type
- Optimize ảnh (quality auto, format auto)
- Generate thumbnail cho video
- Organize file theo folder
- Lưu metadata vào MongoDB
- Delete file từ Cloudinary và MongoDB
- Get file info by ID
- Get all files của user

### 🔜 Tính Năng Tương Lai
- Support Firebase Storage
- Support AWS S3
- Support Azure Blob Storage
- Image transformation (resize, crop, filter)
- Video processing (transcode, compress)
- File compression
- Virus scanning
- Quota management (giới hạn dung lượng)
- CDN integration
- Private file access với signed URLs
- Batch operations

## 🔒 Security

### File Validation
- Validate file size (max 10MB mặc định)
- Validate file type
- Check MIME type
- Sanitize filename

### Access Control
- Upload: Yêu cầu authentication
- Delete: Chỉ owner mới được xóa
- View: Public access cho GET endpoints

### Best Practices
- Không lưu file nhạy cảm trên Cloudinary
- Sử dụng signed URLs cho private files
- Enable virus scanning trong production
- Set up backup cho file quan trọng
- Monitor storage usage

## 💡 Usage Examples

### Upload Avatar
```bash
curl -X POST http://localhost:8080/api/v1/files/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@avatar.jpg" \
  -F "folderPath=avatars" \
  -F "description=User avatar" \
  -F "optimize=true"
```

### Upload Video Story
```bash
curl -X POST http://localhost:8080/api/v1/files/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@story.mp4" \
  -F "folderPath=stories" \
  -F "generateThumbnail=true" \
  -F "tags=story,video"
```

### Delete File
```bash
curl -X DELETE http://localhost:8080/api/v1/files/{fileId} \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 🐛 Troubleshooting

### File Upload Failed
- Check Cloudinary credentials trong .env
- Verify file size không vượt quá limit
- Check network connection
- Verify MIME type được hỗ trợ

### File Not Found
- Verify fileId is correct
- Check if file status is ACTIVE
- Ensure file exists in database

### Permission Denied
- Verify JWT token is valid
- Check if user is the owner of the file
- Ensure proper authorization headers

## 📊 Database Indexes

Recommended indexes for FileUpload collection:
```javascript
db.file_uploads.createIndex({ "uploadedBy": 1 })
db.file_uploads.createIndex({ "fileType": 1 })
db.file_uploads.createIndex({ "storageProvider": 1 })
db.file_uploads.createIndex({ "status": 1 })
db.file_uploads.createIndex({ "uploadedAt": -1 })
db.file_uploads.createIndex({ "tags": 1 })
db.file_uploads.createIndex({ "folderPath": 1 })
```

## 📈 Monitoring

Track these metrics:
- Total file uploads per day
- Total storage used per user
- Average file size
- Upload success rate
- Most used file types
- Storage provider usage
