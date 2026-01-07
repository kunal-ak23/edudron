# BCrypt Round Factor Configuration

## What is BCrypt Round Factor?

The **round factor** (also called **strength** or **cost factor**) in BCrypt determines how many iterations of hashing are performed. It's a critical security parameter.

- **Round Factor 10** = 2^10 = **1,024 rounds**
- **Round Factor 12** = 2^12 = **4,096 rounds**
- **Round Factor 14** = 2^14 = **16,384 rounds**

## Why Round Factor Matters

1. **Security**: Higher rounds = more secure (harder to brute force)
2. **Performance**: Higher rounds = slower (more CPU time)
3. **Consistency**: **MUST match** between password creation and verification

## Recommended Round Factor

**Strength 10** is the recommended default:
- Good balance of security and performance
- Standard in Spring Security
- Used by both Gulliyo and EduDron

## Configuration

### Spring Security BCryptPasswordEncoder

**Default**: Strength 10 (if not specified)

```java
// Explicit (recommended)
new BCryptPasswordEncoder(10)

// Implicit (uses default 10)
new BCryptPasswordEncoder()
```

### Python bcrypt

**Default**: Strength 12 (different from Spring!)

```python
# Explicit (recommended - use 10 to match Spring)
bcrypt.gensalt(rounds=10)

# Implicit (uses default 12 - WRONG!)
bcrypt.gensalt()
```

### Node.js bcrypt

```javascript
// Explicit (recommended)
bcrypt.hashSync(password, 10)
```

## Fix Applied

### 1. SecurityConfig.java
- **EduDron**: Now explicitly uses `BCryptPasswordEncoder(10)`
- **Gulliyo**: Updated to explicitly use `BCryptPasswordEncoder(10)`

### 2. create-super-admin.sh Scripts
- **EduDron**: Updated to use `bcrypt.gensalt(rounds=10)`
- **Gulliyo**: Updated to use `bcrypt.gensalt(rounds=10)`

## Important Notes

1. **BCrypt stores rounds in the hash**: The hash itself contains the round factor used, so verification should work even if rounds differ. However, it's best practice to be consistent.

2. **If authentication fails**:
   - Check if password was created with different rounds
   - Verify the hash format: `$2a$10$...` or `$2b$10$...` (the `10` is the round factor)
   - Recreate password with correct rounds if needed

3. **Verifying existing passwords**:
   - BCrypt hashes look like: `$2a$10$N9qo8uLOickgx2ZMRZoMye...`
   - The `10` after `$2a$` is the round factor
   - If your hash shows `$2a$12$...`, it was created with rounds 12

## Testing

After restarting Identity service:

1. **Check existing password hash**:
   ```sql
   SELECT email, password FROM idp.users WHERE email = 'your-email@example.com';
   ```
   Look at the hash format - it should show `$2a$10$...` or `$2b$10$...`

2. **If hash shows different rounds** (e.g., `$2a$12$...`):
   - The password was created with rounds 12
   - BCryptPasswordEncoder can still verify it (it reads rounds from hash)
   - But for consistency, recreate with rounds 10

3. **Create new password with correct rounds**:
   ```bash
   ./scripts/create-super-admin.sh admin@edudron.com 'Password123!' 'Admin User'
   ```

## Summary

✅ **Round Factor**: 10 (explicitly set)
✅ **Spring Security**: `BCryptPasswordEncoder(10)`
✅ **Scripts**: `bcrypt.gensalt(rounds=10)`
✅ **Both Projects**: Now in sync

Authentication should work correctly now! 🎉


