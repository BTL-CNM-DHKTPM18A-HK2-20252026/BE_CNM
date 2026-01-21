# Git Commit & GitHub Workflow Rules

> 🎯 **Purpose**: Maintain clean git history and streamline collaboration  
> 👥 **Applies to**: All developers working on Fruvia Chat Backend  
> 📦 **Version Control**: Git + GitHub

---

## 📋 Table of Contents
1. [Commit Message Format](#commit-message-format)
2. [Branch Naming Conventions](#branch-naming-conventions)
3. [Git Workflow](#git-workflow)
4. [Pull Request Guidelines](#pull-request-guidelines)
5. [Code Review Process](#code-review-process)
6. [Common Commands](#common-commands)

---

## 1. Commit Message Format

### Conventional Commits Standard

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Commit Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat(messages): add message reactions` |
| `fix` | Bug fix | `fix(auth): resolve JWT expiration issue` |
| `docs` | Documentation only | `docs: update API documentation` |
| `style` | Code style changes (formatting, semicolons) | `style(controller): fix indentation` |
| `refactor` | Code refactoring (no behavior change) | `refactor(service): simplify message logic` |
| `perf` | Performance improvements | `perf(query): optimize conversation query` |
| `test` | Adding or updating tests | `test(message): add unit tests for service` |
| `chore` | Build process, dependencies, tools | `chore: update Spring Boot to 3.2.1` |
| `ci` | CI/CD changes | `ci: add GitHub Actions workflow` |
| `revert` | Revert previous commit | `revert: revert "feat: add reactions"` |

### Scopes (Optional but Recommended)

Common scopes for Fruvia Chat:
- `auth` - Authentication/Authorization
- `messages` - Message features
- `conversations` - Conversation management
- `users` - User management
- `friends` - Friend system
- `files` - File upload/download
- `config` - Configuration
- `security` - Security-related changes
- `db` - Database changes

### Subject Line Rules

**✅ DO:**
- Use imperative mood ("add" not "added" or "adds")
- Start with lowercase
- No period at the end
- Keep it under 50 characters
- Be specific and descriptive

```bash
# ✅ GOOD
git commit -m "feat(messages): add emoji reactions to messages"
git commit -m "fix(auth): prevent token refresh race condition"
git commit -m "docs: add API documentation for user endpoints"
```

**❌ DON'T:**
```bash
# ❌ BAD
git commit -m "fixed bug"
git commit -m "updated files"
git commit -m "changes"
git commit -m "WIP"
git commit -m "asdfasdf"
```

### Body (Optional)

Provide additional context when needed:
- Explain **why** the change was made
- What problem does it solve
- Any side effects or considerations

```bash
git commit -m "fix(auth): resolve JWT token validation failure

The JWT decoder was using the wrong algorithm (RS256 instead of HS256),
causing intermittent validation failures. This fix ensures consistent
token validation across all requests.

Fixes #123"
```

### Footer (Optional)

Reference issues, breaking changes:

```bash
# Reference issue
Fixes #123
Closes #456
Related to #789

# Breaking change
BREAKING CHANGE: API endpoint /api/messages changed to /api/v1/messages
```

---

## 2. Branch Naming Conventions

### Format

```
<type>/<ticket-id>-<short-description>
```

### Branch Types

- `feature/` - New features
- `fix/` - Bug fixes
- `hotfix/` - Urgent production fixes
- `refactor/` - Code refactoring
- `docs/` - Documentation updates
- `test/` - Test additions/updates
- `chore/` - Maintenance tasks

### Examples

```bash
# ✅ GOOD
feature/MSG-123-add-message-reactions
fix/AUTH-456-jwt-expiration-bug
hotfix/CRIT-789-database-connection-leak
refactor/SVC-101-simplify-message-service
docs/DOC-202-update-api-docs
test/MSG-303-add-unit-tests

# ❌ BAD
new-feature
fix-bug
johns-branch
test123
```

### Protected Branches

- `main` / `master` - Production-ready code
- `develop` - Integration branch for features
- `staging` - Pre-production testing

**Rules:**
- ❌ No direct commits to protected branches
- ✅ All changes via Pull Requests
- ✅ Require code review before merge
- ✅ CI/CD must pass

---

## 3. Git Workflow

### Feature Development Flow

```bash
# 1. Start from latest develop
git checkout develop
git pull origin develop

# 2. Create feature branch
git checkout -b feature/MSG-123-add-reactions

# 3. Make changes and commit regularly
git add src/main/java/iuh/fit/controller/MessageController.java
git commit -m "feat(messages): add reaction endpoint"

git add src/main/java/iuh/fit/service/MessageService.java
git commit -m "feat(messages): implement reaction business logic"

git add src/test/java/iuh/fit/service/MessageServiceTest.java
git commit -m "test(messages): add unit tests for reactions"

# 4. Keep branch updated with develop
git fetch origin
git rebase origin/develop

# 5. Push to remote
git push origin feature/MSG-123-add-reactions

# 6. Create Pull Request on GitHub
# 7. After approval, merge via GitHub
```

### Hotfix Flow

```bash
# 1. Branch from main
git checkout main
git pull origin main
git checkout -b hotfix/CRIT-789-critical-bug

# 2. Fix the bug
git add .
git commit -m "fix(auth): resolve critical security vulnerability"

# 3. Push and create PR
git push origin hotfix/CRIT-789-critical-bug

# 4. Merge to main AND develop
```

---

## 4. Pull Request Guidelines

### PR Title Format

Follow same format as commit messages:

```
<type>(<scope>): <description>
```

**Examples:**
```
feat(messages): Add message reactions feature
fix(auth): Resolve JWT token expiration issue
docs: Update API documentation for v1 endpoints
```

### PR Description Template

```markdown
## Description
Brief description of changes made.

## Type of Change
- [ ] New feature
- [ ] Bug fix
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring

## Related Issues
Fixes #123
Related to #456

## Changes Made
- Added reaction endpoint at POST /api/v1/messages/{id}/reactions
- Implemented ReactionService with business logic
- Created Reaction entity and repository
- Added unit tests (coverage: 92%)

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed
- [ ] All tests pass

## Screenshots (if applicable)
[Add screenshots for UI changes]

## Checklist
- [ ] Code follows project conventions (docs/RULE_BACKEND.md)
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated
- [ ] No new warnings
- [ ] Tests pass locally
```

### Before Creating PR

**Checklist:**
- [ ] Code is clean and follows conventions
- [ ] All tests pass (`mvn test`)
- [ ] No merge conflicts with target branch
- [ ] Commit messages follow conventions
- [ ] Documentation updated if needed
- [ ] Self-review completed

---

## 5. Code Review Process

### For Authors

**Before requesting review:**
1. Self-review your own code
2. Run tests locally
3. Check for console errors/warnings
4. Update documentation
5. Add clear PR description

**During review:**
- Respond to all comments
- Make requested changes promptly
- Re-request review after updates
- Don't take feedback personally

### For Reviewers

**What to check:**
- [ ] Code quality and readability
- [ ] Follows project conventions
- [ ] Security concerns
- [ ] Performance issues
- [ ] Test coverage
- [ ] Error handling
- [ ] Documentation

**Review comments format:**

```markdown
# Blocking issues (must fix)
🔴 **CRITICAL:** Missing authorization check at line 45
User can delete other users' messages.

# Suggestions (should fix)
🟡 **SUGGESTION:** Consider extracting this logic to a separate method
for better reusability.

# Nitpicks (optional)
🟢 **NITPICK:** Variable name could be more descriptive.
Consider `unreadMessageCount` instead of `count`.

# Praise (always appreciated!)
✅ **NICE:** Great test coverage! Very thorough edge case handling.
```

**Review Response Time:**
- Critical bugs: Within 2 hours
- Features: Within 1 business day
- Docs: Within 2 business days

---

## 6. Common Commands

### Daily Workflow

```bash
# Check current status
git status

# View changes
git diff
git diff --staged

# Stage changes
git add <file>
git add .

# Commit
git commit -m "feat(scope): description"

# Push
git push origin <branch-name>

# Pull latest changes
git pull origin develop
```

### Branch Management

```bash
# List branches
git branch
git branch -a  # Include remote branches

# Switch branches
git checkout <branch-name>
git checkout -b <new-branch>  # Create and switch

# Delete branch
git branch -d <branch-name>  # Local
git push origin --delete <branch-name>  # Remote

# Rename branch
git branch -m <old-name> <new-name>
```

### Syncing with Remote

```bash
# Fetch latest from remote (doesn't merge)
git fetch origin

# Pull and merge
git pull origin develop

# Pull and rebase (cleaner history)
git pull --rebase origin develop

# Push changes
git push origin <branch-name>

# Force push (use carefully!)
git push --force-with-lease origin <branch-name>
```

### Fixing Mistakes

```bash
# Undo last commit (keep changes)
git reset --soft HEAD~1

# Undo last commit (discard changes)
git reset --hard HEAD~1

# Amend last commit message
git commit --amend -m "new message"

# Amend last commit (add forgotten files)
git add <forgotten-file>
git commit --amend --no-edit

# Discard local changes
git checkout -- <file>
git restore <file>

# Stash changes temporarily
git stash
git stash pop

# Revert a commit (create new commit)
git revert <commit-hash>
```

### History & Logs

```bash
# View commit history
git log
git log --oneline
git log --graph --oneline --all

# View changes in a file
git log -p <file>

# Find who changed a line
git blame <file>

# Search commits
git log --grep="keyword"
git log --author="name"
```

### Rebasing

```bash
# Rebase current branch onto develop
git rebase develop

# Interactive rebase (clean up commits)
git rebase -i HEAD~3

# Continue after resolving conflicts
git rebase --continue

# Abort rebase
git rebase --abort
```

---

## 🎯 Best Practices

### Commit Frequency

**✅ DO:**
- Commit logical units of work
- One commit per feature/fix when possible
- Commit after completing a task

**❌ DON'T:**
- Commit half-done work
- Create "WIP" commits (use git stash instead)
- Mix multiple unrelated changes in one commit

### Commit Size

**Ideal commit:**
- Changes 1-3 files for simple fixes
- Changes 5-10 files for small features
- Can be reviewed in under 10 minutes

**Too large if:**
- Changes more than 20 files
- Adds more than 500 lines
- Takes more than 30 minutes to review
→ **Solution:** Split into multiple PRs

### Writing Good Commit Messages

```bash
# ✅ GOOD - Clear and specific
feat(messages): add pagination to message list endpoint
fix(auth): prevent duplicate session creation on login
docs(api): add examples for authentication endpoints
refactor(service): extract user validation to separate method

# ❌ BAD - Vague and uninformative
update code
fix bug
changes
wip
asdf
```

---

## 🚨 Things to NEVER Commit

```bash
# ❌ NEVER commit:
- Sensitive data (passwords, API keys, tokens)
- application.yaml with real credentials
- .env files with secrets
- Database dumps with real data
- Large binary files (images, videos)
- IDE-specific files (.idea/, *.iml)
- Build artifacts (target/, *.jar)
- node_modules/, vendor/
- Personal notes or TODO files

# ✅ Use .gitignore:
target/
*.jar
*.class
.env
application-local.yaml
.idea/
*.iml
```

---

## 📊 Git Aliases (Optional)

Add to `~/.gitconfig` for shortcuts:

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

Usage:
```bash
git st          # Instead of: git status
git co develop  # Instead of: git checkout develop
git cm "feat: add feature"  # Instead of: git commit -m "feat: add feature"
```

---

## 🔍 Troubleshooting

### Merge Conflicts

```bash
# 1. See conflicted files
git status

# 2. Open files and resolve conflicts
# Look for markers: <<<<<<<, =======, >>>>>>>

# 3. Mark as resolved
git add <resolved-file>

# 4. Complete merge
git commit
```

### Accidentally Committed to Wrong Branch

```bash
# 1. Create correct branch
git branch feature/correct-branch

# 2. Reset current branch
git reset --hard HEAD~1

# 3. Switch to correct branch
git checkout feature/correct-branch
```

### Pushed Wrong Commit

```bash
# ⚠️ Only if no one else has pulled

# 1. Reset local
git reset --hard HEAD~1

# 2. Force push
git push --force-with-lease origin <branch>
```

---

## 📚 Resources

- [Conventional Commits](https://www.conventionalcommits.org/)
- [GitHub Flow](https://guides.github.com/introduction/flow/)
- [Git Documentation](https://git-scm.com/doc)
- [Atlassian Git Tutorials](https://www.atlassian.com/git/tutorials)

---

**Version**: 1.0  
**Last Updated**: 21/01/2026  
**Maintained By**: Fruvia Development Team
