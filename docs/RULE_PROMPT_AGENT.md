# AI Agent Prompt Guidelines

> 🤖 **Rules for AI-Generated Code & Agent Interactions**  
> 📅 **Last Updated**: January 2026

---

## 📋 Table of Contents
1. [Context Gathering](#context-gathering)
2. [Code Generation Rules](#code-generation-rules)
3. [Code Review Guidelines](#code-review-guidelines)
4. [Refactoring Guidelines](#refactoring-guidelines)
5. [Bug Fixing Guidelines](#bug-fixing-guidelines)
6. [Documentation Generation](#documentation-generation)
7. [Testing Generation](#testing-generation)
8. [Best Practices](#best-practices)

---

## 1. Context Gathering

### Before Generating Code

**✅ ALWAYS:**

1. **Understand the project structure**
   ```
   Read: docs/CODE_STRUCTURE.md
   Check: Current package organization
   Verify: Existing similar implementations
   ```

2. **Check existing patterns**
   ```
   Find similar controllers/services/entities
   Follow the same pattern
   Use the same naming conventions
   ```

3. **Review related documentation**
   ```
   SECURITY_ARCHITECTURE.md - For auth/security features
   CORS_CONFIG.md - For API configurations
   UTILS_DOCUMENTATION.md - For available utilities
   RULE_BACKEND.md - For coding standards
   ```

4. **Ask clarifying questions**
   ```
   "Which package should this go in?"
   "Should this require authentication?"
   "What's the expected response format?"
   "Are there existing similar endpoints?"
   ```

### Context Requirements Checklist

Before generating any code, ensure you know:

- ✅ **What**: What feature/fix is needed?
- ✅ **Where**: Which package/layer to modify?
- ✅ **Why**: What problem does it solve?
- ✅ **How**: What pattern to follow?
- ✅ **Dependencies**: What existing code to use?
- ✅ **Security**: Does it need authentication/authorization?
- ✅ **Validation**: What validation rules apply?

---

## 2. Code Generation Rules

### Controller Generation

**Prompt Template:**
```
Generate a REST controller for {Resource} with the following endpoints:
- GET /{resources} - List all (with pagination)
- GET /{resources}/{id} - Get by ID
- POST /{resources} - Create new
- PUT /{resources}/{id} - Update
- DELETE /{resources}/{id} - Delete

Requirements:
- Follow the pattern in UserController.java
- Use {Resource}Service for business logic
- Use {Resource}Response DTO for responses
- Use Create{Resource}Request and Update{Resource}Request for inputs
- Add appropriate @Valid annotations
- Add Swagger documentation (@Operation, @ApiResponses)
- Check authentication with JwtUtils where needed

Location: src/main/java/iuh/fit/controller/{Resource}Controller.java
```

**Expected Output:**
```java
@RestController
@RequestMapping("/{resources}")
@Tag(name = "{Resource} Management", description = "APIs for {resource} operations")
@RequiredArgsConstructor
public class {Resource}Controller {
    
    private final {Resource}Service {resource}Service;
    
    @Operation(summary = "List all {resources}")
    @GetMapping
    public ResponseEntity<List<{Resource}Response>> getAll() {
        return ResponseEntity.ok({resource}Service.getAll());
    }
    
    // ... other endpoints
}
```

### Service Generation

**Prompt Template:**
```
Generate a service layer for {Resource} with:
- Interface: {Resource}Service
- Implementation: {Resource}ServiceImpl

Business logic needed:
- {Describe operations}

Validation rules:
- {List validation requirements}

Use these dependencies:
- {Resource}Repository
- {Resource}Mapper
- Other services if needed

Location:
- Interface: src/main/java/iuh/fit/service/{Resource}Service.java
- Implementation: src/main/java/iuh/fit/service/impl/{Resource}ServiceImpl.java

Follow the pattern in UserServiceImpl.java
```

### Entity Generation

**Prompt Template:**
```
Generate a JPA entity for {Entity} with these fields:
- {field1}: {type} ({constraints})
- {field2}: {type} ({constraints})
- ...

Relationships:
- {Relationship type} with {Other Entity}

Requirements:
- Use UUID for ID (@GeneratedValue(strategy = GenerationType.UUID))
- Add audit fields (createdAt, updatedAt)
- Add soft delete (isActive)
- Use appropriate column constraints
- Add necessary indexes

Location: src/main/java/iuh/fit/entity/{Entity}.java
```

### DTO Generation

**Prompt Template:**
```
Generate DTOs for {Resource}:

1. Create{Resource}Request
   - Fields for creation: {list fields}
   - Validation: {validation rules}

2. Update{Resource}Request
   - Fields for update: {list fields}
   - Optional fields allowed

3. {Resource}Response
   - All fields to return to client
   - NO sensitive data (passwords, tokens, etc.)

Location: src/main/java/iuh/fit/dto/
```

---

## 3. Code Review Guidelines

### When Reviewing AI-Generated Code

**Check for:**

1. **Follows project patterns** ✅
   - Same structure as existing code
   - Consistent naming conventions
   - Uses project's established patterns

2. **Security considerations** ✅
   - Authentication checks where needed
   - Authorization (role-based access)
   - Input validation
   - No sensitive data exposure
   - SQL injection prevention

3. **Error handling** ✅
   - Try-catch where appropriate
   - Meaningful error messages
   - Proper exception types
   - Global exception handler integration

4. **Performance** ✅
   - No N+1 queries
   - Appropriate fetch types (LAZY vs EAGER)
   - Pagination for large datasets
   - Efficient algorithms

5. **Code quality** ✅
   - DRY (Don't Repeat Yourself)
   - SOLID principles
   - Clear variable names
   - Appropriate comments
   - No code smells

### Review Checklist

```markdown
## Code Review Checklist

### Structure
- [ ] Code in correct package
- [ ] Follows naming conventions
- [ ] Consistent with existing patterns
- [ ] No unnecessary dependencies

### Functionality
- [ ] Implements all requirements
- [ ] Edge cases handled
- [ ] Null safety
- [ ] Validation present

### Security
- [ ] Authentication required where needed
- [ ] Authorization checks present
- [ ] No sensitive data exposed
- [ ] Input sanitized

### Database
- [ ] Proper entity relationships
- [ ] Correct cascade types
- [ ] Appropriate fetch types
- [ ] Indexes where needed

### Testing
- [ ] Unit tests present
- [ ] Test cases cover edge cases
- [ ] Integration tests if needed

### Documentation
- [ ] JavaDoc present
- [ ] API documentation (Swagger)
- [ ] Comments for complex logic
```

---

## 4. Refactoring Guidelines

### When to Refactor

**Indicators:**
- 🔴 Code duplication (same logic in multiple places)
- 🔴 Long methods (> 50 lines)
- 🔴 Complex conditionals (nested if/else)
- 🔴 God classes (too many responsibilities)
- 🔴 Tight coupling

### Refactoring Prompt Template

```
Refactor the following code:

{Paste code}

Issues:
- {List code smells}

Requirements:
- Extract methods for better readability
- Remove duplication
- Follow single responsibility principle
- Keep existing functionality
- Maintain backward compatibility

Context:
- Project uses: {list tech stack}
- Existing patterns: {describe patterns}
```

### Example Refactoring

**Before (Bad):**
```java
@PostMapping
public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
    // ❌ Too much logic in controller
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new DuplicateException("Username exists");
    }
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new DuplicateException("Email exists");
    }
    User user = new User();
    user.setUsername(request.getUsername());
    user.setEmail(request.getEmail());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setCreatedAt(LocalDateTime.now());
    user = userRepository.save(user);
    return ResponseEntity.ok(userMapper.toResponse(user));
}
```

**After (Good):**
```java
@PostMapping
public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    // ✅ Delegate to service
    UserResponse response = userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

---

## 5. Bug Fixing Guidelines

### Bug Fix Prompt Template

```
Fix the following bug:

Bug description:
{Describe the bug}

Steps to reproduce:
1. {Step 1}
2. {Step 2}
3. {Expected vs Actual}

Current code:
{Paste relevant code}

Error message/logs:
{Paste error logs}

Requirements:
- Fix the root cause, not symptoms
- Add validation to prevent similar issues
- Add/update tests to cover the bug
- Document the fix in comments if complex

Context:
- Related files: {list files}
- Dependencies: {list dependencies}
```

### Bug Fix Checklist

```markdown
- [ ] Root cause identified
- [ ] Fix tested locally
- [ ] No side effects on other features
- [ ] Tests added/updated
- [ ] Error handling improved
- [ ] Logs added for debugging
- [ ] Documentation updated if needed
```

---

## 6. Documentation Generation

### JavaDoc Prompt Template

```
Generate JavaDoc for the following class:

{Paste class code}

Include:
- Class-level description
- @author tag
- @since tag
- Method descriptions
- @param descriptions
- @return descriptions
- @throws descriptions
- Usage examples for complex methods

Follow Java documentation standards.
```

### API Documentation Prompt Template

```
Generate Swagger/OpenAPI annotations for the following controller:

{Paste controller code}

Include:
- @Tag for controller
- @Operation for each endpoint (summary + description)
- @ApiResponses for all possible responses (200, 400, 401, 404, etc.)
- @Parameter for path/query parameters
- Request/Response examples

Follow project's documentation style in other controllers.
```

---

## 7. Testing Generation

### Unit Test Prompt Template

```
Generate unit tests for the following service:

{Paste service code}

Requirements:
- Test all public methods
- Cover happy path and error cases
- Mock all dependencies
- Use JUnit 5 and Mockito
- Test data setup in @BeforeEach
- Clear test names (should_doSomething_when_condition)

Follow the test pattern in UserServiceTest.java
```

### Integration Test Prompt Template

```
Generate integration tests for the following controller:

{Paste controller code}

Requirements:
- Use @SpringBootTest
- Use MockMvc for HTTP requests
- Test all endpoints
- Test authentication/authorization
- Verify response status and body
- Use test database

Follow the test pattern in existing integration tests.
```

---

## 8. Best Practices

### DO's ✅

1. **Always provide context**
   ```
   "I'm working on {feature} in the {package} package.
    The project uses {tech stack}.
    Similar implementation exists in {file}.
    Please follow the same pattern."
   ```

2. **Be specific about requirements**
   ```
   "Generate a POST endpoint that:
    - Accepts CreateUserRequest
    - Validates input with @Valid
    - Requires authentication (use JwtUtils)
    - Returns UserResponse with 201 status
    - Handles duplicate username error"
   ```

3. **Reference existing code**
   ```
   "Follow the same pattern as UserController.java
    Use the same error handling approach
    Keep the same response format"
   ```

4. **Ask for explanations**
   ```
   "Explain why you chose this approach
    What are the alternatives?
    What are the trade-offs?"
   ```

5. **Request incremental changes**
   ```
   "First, generate the entity
    Then, generate the repository
    Then, generate the service
    Finally, generate the controller"
   ```

### DON'Ts ❌

1. **Don't give vague prompts**
   ```
   ❌ "Create a user API"
   ✅ "Create a REST API for user management with CRUD operations,
       following the pattern in UserController.java"
   ```

2. **Don't skip context**
   ```
   ❌ "Generate a service"
   ✅ "Generate a service for User entity that uses UserRepository,
       follows UserServiceImpl pattern, and includes validation"
   ```

3. **Don't ignore existing code**
   ```
   ❌ Generate code from scratch without checking existing patterns
   ✅ Review existing code first, then follow the same pattern
   ```

4. **Don't accept blindly**
   ```
   ❌ Use AI-generated code without review
   ✅ Review for security, performance, and correctness
   ```

5. **Don't forget error handling**
   ```
   ❌ "Generate a controller"
   ✅ "Generate a controller with proper error handling and validation"
   ```

### Prompt Quality Levels

**❌ Poor Prompt:**
```
Generate user code
```

**⚠️ Average Prompt:**
```
Generate a REST API for users with CRUD operations
```

**✅ Good Prompt:**
```
Generate a REST controller for User management with CRUD operations.
Follow the pattern in UserController.java.
Use UserService for business logic.
Add authentication checks with JwtUtils.
Include Swagger documentation.
Location: src/main/java/iuh/fit/controller/UserController.java
```

**🌟 Excellent Prompt:**
```
Generate a REST controller for User management with the following requirements:

Endpoints:
- GET /users (paginated, authenticated)
- GET /users/{id} (authenticated)
- POST /users (public for registration)
- PUT /users/{id} (authenticated, owner only)
- DELETE /users/{id} (authenticated, owner or admin)

Requirements:
- Follow the exact pattern in UserController.java
- Use UserService for all business logic
- Use CreateUserRequest, UpdateUserRequest, UserResponse DTOs
- Add @Valid for input validation
- Check authentication with JwtUtils.getCurrentUserId()
- Check authorization: users can only update/delete their own profile
- Add Swagger documentation (@Operation, @ApiResponses)
- Return appropriate HTTP status codes (200, 201, 204, 400, 401, 403, 404)
- Use @RequiredArgsConstructor for dependency injection

Dependencies:
- UserService (constructor injection)

Location: src/main/java/iuh/fit/controller/UserController.java

Reference: Check UserController.java for the exact pattern to follow.
```

---

## 🎯 Effective AI Collaboration

### Iterative Approach

```
Step 1: Generate basic structure
↓
Step 2: Review and provide feedback
↓
Step 3: Refine based on feedback
↓
Step 4: Add error handling
↓
Step 5: Add tests
↓
Step 6: Add documentation
```

### Feedback Loop

```
AI generates code
→ Developer reviews
→ Developer provides specific feedback
→ AI refines code
→ Developer verifies
→ Code is ready
```

### Example Feedback

**Good Feedback:**
```
"The controller looks good, but:
1. Add authentication check in getUserById()
2. Change return status to 201 for createUser()
3. Add @Operation annotation for Swagger
4. Use ResponseEntity.noContent() for deleteUser()
Please update the code accordingly."
```

**Poor Feedback:**
```
"Fix it" or "Doesn't work"
```

---

## 📚 References

- [Backend Development Rules](./RULE_BACKEND.md)
- [Code Structure](./CODE_STRUCTURE.md)
- [Security Architecture](./SECURITY_ARCHITECTURE.md)

---

## 🤖 AI Tools Integration

### Recommended AI Tools

1. **GitHub Copilot**
   - Inline code suggestions
   - Comment-to-code generation
   - Pattern recognition

2. **ChatGPT / Claude**
   - Code generation
   - Code review
   - Refactoring suggestions
   - Documentation generation

3. **Tabnine**
   - Context-aware completions
   - Team learning

### Tool-Specific Tips

**For GitHub Copilot:**
```java
// Write descriptive comments, Copilot will generate code
// Generate a service method to create user with validation
// The method should check if username exists, hash password, and save user
```

**For ChatGPT/Claude:**
- Provide full context in prompts
- Reference existing files
- Ask for explanations
- Request multiple approaches

---

**Last Updated**: January 21, 2026  
**Maintainer**: Fruvia Development Team
