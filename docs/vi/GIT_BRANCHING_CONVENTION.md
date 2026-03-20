# Quy định đặt tên nhánh (Git Branching Convention) - FruviaChat

Tài liệu này hướng dẫn cách đặt tên nhánh và quản lý quy trình làm việc với Git cho dự án **FruviaChat**. Việc tuân thủ quy định giúp nhóm dễ dàng theo dõi tiến độ, tránh xung đột code và quản lý các tính năng hiệu quả.

---

## 1. Các nhánh chính (Main Branches)

Các nhánh này được bảo vệ và không được commit trực tiếp.

| Tên nhánh | Mục đích |
| :--- | :--- |
| `main` | Chứa mã nguồn ổn định nhất. Chỉ merge từ `develop` khi chuẩn bị demo hoặc kết thúc một cột mốc (milestone). |
| `develop` | Nhánh tích hợp chính. Mọi tính năng mới sau khi hoàn thành sẽ được merge vào đây để kiểm tra tính tương thích. |

---

## 2. Các nhánh tạm thời (Temporary Branches)

Dùng để phát triển tính năng hoặc sửa lỗi, sau khi xong sẽ xóa đi.

### Cấu trúc khuyến khích: `feature/<module>-<action>`
*Nên chia nhỏ nhánh để mỗi PR (Pull Request) không quá lớn, giúp review nhanh và merge sớm.*

| Module | Nhánh nhỏ khuyến nghị | Chi tiết tính năng (Zalo Clone) |
| :--- | :--- | :--- |
| **1. Auth & Security** | `feature/auth-register` | Đăng ký, OTP xác thực, tạo salt/password |
| | `feature/auth-login` | Đăng nhập JWT, Refresh Token, Logout |
| | `feature/auth-forgot-password` | Quên mật khẩu, reset qua Email/OTP |
| **2. Profile & Settings** | `feature/user-profile-info` | Xem/Sửa tên, ngày sinh, giới tính |
| | `feature/user-avatar-cover` | Đổi ảnh đại diện và ảnh bìa (Cover) |
| | `feature/user-qr-code` | Tạo và quét mã QR cá nhân để kết bạn |
| | `feature/user-settings` | Cấu hình quyền riêng tư, thông báo, ngôn ngữ |
| **3. Contact (Bạn bè)** | `feature/friend-search` | Tìm bạn qua số điện thoại |
| | `feature/friend-request` | Gửi/Nhận/Hủy lời mời, Danh sách chờ |
| | `feature/friend-list` | Danh sách bạn bè, Phân loại danh bạ (A-Z) |
| | `feature/friend-block` | Chặn người dùng, quản lý danh sách đen |
| **4. Chat 1-1** | `feature/chat-text-msg` | Nhắn tin văn bản, Emoji, Tin nhắn nhanh |
| | `feature/chat-media-msg` | Gửi ảnh, Video, File, Ghi âm (Voice) |
| | `feature/chat-reaction-reply` | Thả tim (Reaction), Trả lời tin nhắn (Reply) |
| | `feature/chat-recall-delete` | Thu hồi tin nhắn (Recall), Xóa phía tôi |
| | `feature/chat-pin-info` | Ghim tin nhắn, xem thông tin hội thoại |
| **5. Group Chat** | `feature/group-create` | Tạo nhóm, đặt tên, ảnh nhóm |
| | `feature/group-members` | Thêm/Xóa thành viên, rời nhóm |
| | `feature/group-permissions` | Quyền Admin/Phó nhóm, chặn thành viên nói |
| | `feature/group-poll` | Tạo bình chọn (Poll) trong nhóm |
| **6. Social (Nhật ký)** | `feature/post-create` | Đăng bài viết (Text, nhiều ảnh, Video) |
| | `feature/post-interact` | Like, Comment, Xem danh sách tương tác |
| | `feature/story-post` | Đăng tin (Story) biến mất sau 24h |
| **7. Real-time & System** | `feature/socket-base` | Setup Socket.io/WebSocket, Online status |
| | `feature/notif-push` | Thông báo tin nhắn mới, lời mời kết bạn |
| | `feature/cloud-cloud-of-me` | Hội thoại "Truyền File" (Cloud của tôi) |
| **8. Search** | `feature/search-global` | Tìm kiếm tin nhắn, bạn bè, nhóm |
| **9. Call (Optional)** | `feature/call-audio-video` | Gọi điện, gọi video (WebRTC/Zego) |

---

## 4. Quy tắc Commit Message

Để lịch sử Git sạch đẹp, hãy áp dụng chuẩn **Conventional Commits**:
`type: description`

- `feat`: Tính năng mới (ví dụ: `feat: add api for user registration`)
- `fix`: Sửa lỗi (ví dụ: `fix: resolve token expiration logic`)
- `docs`: Thay đổi tài liệu
- `style`: Định dạng code (whitespace, format, không đổi logic)
- `refactor`: Thay đổi code nhưng không sửa lỗi cũng không thêm tính năng
- `test`: Thêm hoặc sửa test case

---

## 5. Quy trình làm việc (Workflow)

1.  Từ nhánh `develop`, tạo nhánh mới: `git checkout -b feature/ten-tinh-nang`.
2.  Thực hiện code và commit đều đặn.
3.  Sau khi xong, đẩy nhánh lên cloud: `git push origin feature/ten-tinh-nang`.
4.  Tạo **Pull Request (PR)** trên GitHub từ `feature/ten-tinh-nang` vào `develop`.
5.  Thành viên khác review, đảm bảo code chạy ổn thì mới **Approve & Merge**.
6.  Xóa nhánh đã merge để tránh rác repo.
