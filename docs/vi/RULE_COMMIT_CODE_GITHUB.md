# Quy Tắc Git Commit & GitHub Workflow

> 🎯 **Mục đích**: Duy trì lịch sử git sạch sẽ và tối ưu hóa collaboration  
> 👥 **Áp dụng cho**: Tất cả developers làm việc với Fruvia Chat Backend  
> 📦 **Version Control**: Git + GitHub

---

## 📋 Mục Lục
1. [Định Dạng Commit Message](#định-dạng-commit-message)
2. [Quy Tắc Đặt Tên Branch](#quy-tắc-đặt-tên-branch)
3. [Git Workflow](#git-workflow)
4. [Hướng Dẫn Pull Request](#hướng-dẫn-pull-request)
5. [Quy Trình Code Review](#quy-trình-code-review)
6. [Các Lệnh Thường Dùng](#các-lệnh-thường-dùng)

---

## 1. Định Dạng Commit Message

### Chuẩn Conventional Commits

Tuân theo [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Các Loại Commit

| Type | Mô Tả | Ví Dụ |
|------|-------|-------|
| `feat` | Tính năng mới | `feat(messages): thêm reactions cho tin nhắn` |
| `fix` | Sửa bug | `fix(auth): khắc phục lỗi JWT expiration` |
| `docs` | Chỉ thay đổi documentation | `docs: cập nhật tài liệu API` |
| `style` | Thay đổi style code (format, dấu chấm phẩy) | `style(controller): sửa indentation` |
| `refactor` | Refactor code (không thay đổi behavior) | `refactor(service): đơn giản hóa logic message` |
| `perf` | Cải thiện performance | `perf(query): tối ưu query conversation` |
| `test` | Thêm hoặc cập nhật tests | `test(message): thêm unit tests cho service` |
| `chore` | Build process, dependencies, tools | `chore: update Spring Boot lên 3.2.1` |
| `ci` | Thay đổi CI/CD | `ci: thêm GitHub Actions workflow` |
| `revert` | Revert commit trước | `revert: revert "feat: thêm reactions"` |

### Scopes (Tùy Chọn Nhưng Khuyến Khích)

Các scope phổ biến cho Fruvia Chat:
- `auth` - Authentication/Authorization
- `messages` - Tính năng tin nhắn
- `conversations` - Quản lý hội thoại
- `users` - Quản lý user
- `friends` - Hệ thống bạn bè
- `files` - Upload/download file
- `config` - Cấu hình
- `security` - Thay đổi liên quan bảo mật
- `db` - Thay đổi database

### Quy Tắc Subject Line

**✅ NÊN:**
- Dùng thể mệnh lệnh ("add" không phải "added" hoặc "adds")
- Bắt đầu bằng chữ thường
- Không có dấu chấm ở cuối
- Giữ dưới 50 ký tự
- Cụ thể và mô tả rõ ràng

```bash
# ✅ TỐT
git commit -m "feat(messages): thêm emoji reactions cho tin nhắn"
git commit -m "fix(auth): ngăn race condition khi refresh token"
git commit -m "docs: thêm tài liệu API cho user endpoints"
```

**❌ KHÔNG NÊN:**
```bash
# ❌ TỆ
git commit -m "sửa bug"
git commit -m "cập nhật files"
git commit -m "thay đổi"
git commit -m "WIP"
git commit -m "asdfasdf"
```

### Body (Tùy Chọn)

Cung cấp context thêm khi cần:
- Giải thích **tại sao** thay đổi được thực hiện
- Giải quyết vấn đề gì
- Các side effects hoặc lưu ý

```bash
git commit -m "fix(auth): khắc phục lỗi JWT token validation

JWT decoder đang dùng sai algorithm (RS256 thay vì HS256),
gây ra lỗi validation ngẫu nhiên. Fix này đảm bảo token
validation nhất quán trên tất cả requests.

Fixes #123"
```

### Footer (Tùy Chọn)

Reference issues, breaking changes:

```bash
# Reference issue
Fixes #123
Closes #456
Related to #789

# Breaking change
BREAKING CHANGE: API endpoint /api/messages đổi thành /api/v1/messages
```

---

## 2. Quy Tắc Đặt Tên Branch

### Định Dạng

```
<type>/<ticket-id>-<mô-tả-ngắn>
```

### Các Loại Branch

- `feature/` - Tính năng mới
- `fix/` - Sửa bug
- `hotfix/` - Sửa lỗi production khẩn cấp
- `refactor/` - Refactor code
- `docs/` - Cập nhật documentation
- `test/` - Thêm/cập nhật tests
- `chore/` - Công việc maintenance

### Ví Dụ

```bash
# ✅ TỐT
feature/MSG-123-them-message-reactions
fix/AUTH-456-loi-jwt-expiration
hotfix/CRIT-789-database-connection-leak
refactor/SVC-101-don-gian-hoa-message-service
docs/DOC-202-cap-nhat-api-docs
test/MSG-303-them-unit-tests

# ❌ TỆ
tinh-nang-moi
sua-bug
branch-cua-john
test123
```

### Protected Branches

- `main` / `master` - Code production-ready
- `develop` - Branch tích hợp features
- `staging` - Testing trước production

**Quy tắc:**
- ❌ Không commit trực tiếp vào protected branches
- ✅ Mọi thay đổi qua Pull Requests
- ✅ Yêu cầu code review trước khi merge
- ✅ CI/CD phải pass

---

## 3. Git Workflow

### Feature Development Flow

```bash
# 1. Bắt đầu từ develop mới nhất
git checkout develop
git pull origin develop

# 2. Tạo feature branch
git checkout -b feature/MSG-123-them-reactions

# 3. Thực hiện thay đổi và commit thường xuyên
git add src/main/java/iuh/fit/controller/MessageController.java
git commit -m "feat(messages): thêm reaction endpoint"

git add src/main/java/iuh/fit/service/MessageService.java
git commit -m "feat(messages): implement reaction business logic"

git add src/test/java/iuh/fit/service/MessageServiceTest.java
git commit -m "test(messages): thêm unit tests cho reactions"

# 4. Giữ branch cập nhật với develop
git fetch origin
git rebase origin/develop

# 5. Push lên remote
git push origin feature/MSG-123-them-reactions

# 6. Tạo Pull Request trên GitHub
# 7. Sau khi approved, merge qua GitHub
```

### Hotfix Flow

```bash
# 1. Branch từ main
git checkout main
git pull origin main
git checkout -b hotfix/CRIT-789-loi-nghiem-trong

# 2. Sửa bug
git add .
git commit -m "fix(auth): khắc phục lỗ hổng bảo mật nghiêm trọng"

# 3. Push và tạo PR
git push origin hotfix/CRIT-789-loi-nghiem-trong

# 4. Merge vào main VÀ develop
```

---

## 4. Hướng Dẫn Pull Request

### Định Dạng PR Title

Tuân theo định dạng giống commit messages:

```
<type>(<scope>): <mô tả>
```

**Ví dụ:**
```
feat(messages): Thêm tính năng message reactions
fix(auth): Khắc phục lỗi JWT token expiration
docs: Cập nhật tài liệu API cho v1 endpoints
```

### Template Mô Tả PR

```markdown
## Mô Tả
Mô tả ngắn gọn các thay đổi được thực hiện.

## Loại Thay Đổi
- [ ] Tính năng mới
- [ ] Sửa bug
- [ ] Breaking change
- [ ] Cập nhật documentation
- [ ] Refactor code

## Issues Liên Quan
Fixes #123
Related to #456

## Thay Đổi Đã Thực Hiện
- Thêm reaction endpoint tại POST /api/v1/messages/{id}/reactions
- Implement ReactionService với business logic
- Tạo Reaction entity và repository
- Thêm unit tests (coverage: 92%)

## Testing
- [ ] Unit tests đã thêm/cập nhật
- [ ] Integration tests đã thêm/cập nhật
- [ ] Manual testing hoàn tất
- [ ] Tất cả tests pass

## Screenshots (nếu có)
[Thêm screenshots cho UI changes]

## Checklist
- [ ] Code tuân theo project conventions (docs/vi/RULE_BACKEND.md)
- [ ] Self-review hoàn tất
- [ ] Comments thêm cho logic phức tạp
- [ ] Documentation đã cập nhật
- [ ] Không có warnings mới
- [ ] Tests pass ở local
```

### Trước Khi Tạo PR

**Checklist:**
- [ ] Code sạch và tuân theo conventions
- [ ] Tất cả tests pass (`mvn test`)
- [ ] Không có merge conflicts với target branch
- [ ] Commit messages tuân theo conventions
- [ ] Documentation cập nhật nếu cần
- [ ] Self-review hoàn tất

---

## 5. Quy Trình Code Review

### Cho Authors

**Trước khi request review:**
1. Self-review code của mình
2. Chạy tests ở local
3. Kiểm tra console errors/warnings
4. Cập nhật documentation
5. Thêm mô tả PR rõ ràng

**Trong quá trình review:**
- Trả lời tất cả comments
- Thực hiện thay đổi được yêu cầu nhanh chóng
- Re-request review sau khi cập nhật
- Không cảm thấy bị xúc phạm bởi feedback

### Cho Reviewers

**Cần kiểm tra:**
- [ ] Chất lượng code và khả năng đọc
- [ ] Tuân theo project conventions
- [ ] Vấn đề bảo mật
- [ ] Vấn đề performance
- [ ] Test coverage
- [ ] Error handling
- [ ] Documentation

**Định dạng review comments:**

```markdown
# Issues phải fix (must fix)
🔴 **CRITICAL:** Thiếu authorization check ở dòng 45
User có thể xóa messages của người khác.

# Đề xuất (should fix)
🟡 **SUGGESTION:** Cân nhắc extract logic này thành method riêng
để tái sử dụng tốt hơn.

# Nitpicks (tùy chọn)
🟢 **NITPICK:** Tên biến nên mô tả hơn.
Cân nhắc `unreadMessageCount` thay vì `count`.

# Khen ngợi (luôn được đánh giá cao!)
✅ **NICE:** Test coverage tuyệt vời! Xử lý edge cases rất kỹ lưỡng.
```

**Thời Gian Response Review:**
- Critical bugs: Trong vòng 2 giờ
- Features: Trong vòng 1 ngày làm việc
- Docs: Trong vòng 2 ngày làm việc

---

## 6. Các Lệnh Thường Dùng

### Workflow Hằng Ngày

```bash
# Kiểm tra status hiện tại
git status

# Xem thay đổi
git diff
git diff --staged

# Stage changes
git add <file>
git add .

# Commit
git commit -m "feat(scope): mô tả"

# Push
git push origin <branch-name>

# Pull changes mới nhất
git pull origin develop
```

### Quản Lý Branch

```bash
# List branches
git branch
git branch -a  # Bao gồm remote branches

# Chuyển branch
git checkout <branch-name>
git checkout -b <new-branch>  # Tạo và chuyển

# Xóa branch
git branch -d <branch-name>  # Local
git push origin --delete <branch-name>  # Remote

# Đổi tên branch
git branch -m <tên-cũ> <tên-mới>
```

### Đồng Bộ Với Remote

```bash
# Fetch mới nhất từ remote (không merge)
git fetch origin

# Pull và merge
git pull origin develop

# Pull và rebase (lịch sử sạch hơn)
git pull --rebase origin develop

# Push changes
git push origin <branch-name>

# Force push (dùng cẩn thận!)
git push --force-with-lease origin <branch-name>
```

### Sửa Lỗi

```bash
# Undo commit cuối (giữ changes)
git reset --soft HEAD~1

# Undo commit cuối (loại bỏ changes)
git reset --hard HEAD~1

# Sửa commit message cuối
git commit --amend -m "message mới"

# Sửa commit cuối (thêm files quên)
git add <file-quên>
git commit --amend --no-edit

# Loại bỏ local changes
git checkout -- <file>
git restore <file>

# Stash changes tạm thời
git stash
git stash pop

# Revert một commit (tạo commit mới)
git revert <commit-hash>
```

### Lịch Sử & Logs

```bash
# Xem commit history
git log
git log --oneline
git log --graph --oneline --all

# Xem changes trong file
git log -p <file>

# Tìm ai thay đổi dòng
git blame <file>

# Tìm kiếm commits
git log --grep="keyword"
git log --author="tên"
```

### Rebasing

```bash
# Rebase branch hiện tại lên develop
git rebase develop

# Interactive rebase (dọn dẹp commits)
git rebase -i HEAD~3

# Continue sau khi resolve conflicts
git rebase --continue

# Hủy rebase
git rebase --abort
```

---

## 🎯 Best Practices

### Tần Suất Commit

**✅ NÊN:**
- Commit logical units of work
- Một commit mỗi feature/fix khi có thể
- Commit sau khi hoàn thành task

**❌ KHÔNG NÊN:**
- Commit công việc dở dang
- Tạo commits "WIP" (dùng git stash thay thế)
- Trộn nhiều thay đổi không liên quan trong một commit

### Kích Thước Commit

**Commit lý tưởng:**
- Thay đổi 1-3 files cho fixes đơn giản
- Thay đổi 5-10 files cho features nhỏ
- Có thể review trong dưới 10 phút

**Quá lớn nếu:**
- Thay đổi hơn 20 files
- Thêm hơn 500 dòng
- Mất hơn 30 phút để review
→ **Giải pháp:** Chia thành nhiều PRs

### Viết Commit Messages Tốt

```bash
# ✅ TỐT - Rõ ràng và cụ thể
feat(messages): thêm pagination cho message list endpoint
fix(auth): ngăn tạo duplicate session khi login
docs(api): thêm ví dụ cho authentication endpoints
refactor(service): extract user validation thành method riêng

# ❌ TỆ - Mơ hồ và không cung cấp thông tin
cập nhật code
sửa bug
thay đổi
wip
asdf
```

---

## 🚨 Những Thứ KHÔNG BAO GIỜ Commit

```bash
# ❌ KHÔNG BAO GIỜ commit:
- Dữ liệu nhạy cảm (passwords, API keys, tokens)
- application.yaml với credentials thật
- .env files với secrets
- Database dumps với dữ liệu thật
- Large binary files (images, videos)
- Files đặc thù IDE (.idea/, *.iml)
- Build artifacts (target/, *.jar)
- node_modules/, vendor/
- Personal notes hoặc TODO files

# ✅ Dùng .gitignore:
target/
*.jar
*.class
.env
application-local.yaml
.idea/
*.iml
```

---

## 📊 Git Aliases (Tùy Chọn)

Thêm vào `~/.gitconfig` cho shortcuts:

```ini
[alias]
    st = status
    co = checkout
    br = branch
    ci = commit
    cm = commit -m
    ca = commit --amend
    lg = log --oneline --graph --all
    unstage = reset HEAD --
    last = log -1 HEAD
    visual = log --graph --oneline --all --decorate
```

Sử dụng:
```bash
git st          # Thay vì: git status
git co develop  # Thay vì: git checkout develop
git cm "feat: thêm tính năng"  # Thay vì: git commit -m "feat: thêm tính năng"
```

---

## 🔍 Troubleshooting

### Merge Conflicts

```bash
# 1. Xem files bị conflict
git status

# 2. Mở files và resolve conflicts
# Tìm markers: <<<<<<<, =======, >>>>>>>

# 3. Đánh dấu đã resolved
git add <resolved-file>

# 4. Hoàn tất merge
git commit
```

### Vô Tình Commit Vào Sai Branch

```bash
# 1. Tạo branch đúng
git branch feature/branch-dung

# 2. Reset branch hiện tại
git reset --hard HEAD~1

# 3. Chuyển sang branch đúng
git checkout feature/branch-dung
```

### Push Sai Commit

```bash
# ⚠️ Chỉ dùng nếu chưa ai pull

# 1. Reset local
git reset --hard HEAD~1

# 2. Force push
git push --force-with-lease origin <branch>
```

---

## 📚 Tài Nguyên

- [Conventional Commits](https://www.conventionalcommits.org/)
- [GitHub Flow](https://guides.github.com/introduction/flow/)
- [Git Documentation](https://git-scm.com/doc)
- [Atlassian Git Tutorials](https://www.atlassian.com/git/tutorials)

---

**Version**: 1.0  
**Last Updated**: 21/01/2026  
**Maintained By**: Fruvia Development Team
