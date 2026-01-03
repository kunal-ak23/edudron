# JWT Secret Configuration Fix

## Issue
JWT authentication might fail if the secret key is too short or not properly configured.

## Fix Applied

1. **Updated JwtUtil.java default secret**
   - Changed default from `mySecretKey` (10 bytes) to `mySecretKey123456789012345678901234567890` (40 bytes)
   - Added validation to ensure secret is at least 32 bytes (256 bits) for HS256 algorithm
   - This matches the default in `application.yml`

2. **Secret Length Requirement**
   - HS256 (HMAC-SHA256) requires at least 32 bytes (256 bits)
   - The default `mySecretKey` was only 10 bytes, which could cause issues
   - Now using 40-byte default secret that matches application.yml

## Configuration

The JWT secret is configured in two places:

1. **application.yml**:
   ```yaml
   jwt:
     secret: ${JWT_SECRET:mySecretKey123456789012345678901234567890}
   ```

2. **JwtUtil.java**:
   ```java
   @Value("${jwt.secret:mySecretKey123456789012345678901234567890}")
   private String secret;
   ```

Both now use the same default value.

## Environment Variable

You can override the secret using environment variable:
```bash
export JWT_SECRET=your-very-long-secret-key-at-least-32-characters-long
```

## Verification

After restarting the Identity service, JWT tokens should work properly:
- Token generation will succeed
- Token validation will work
- Token refresh will work

## Testing

1. Restart Identity service
2. Try to login
3. Check if JWT token is generated successfully
4. Verify token can be used for authenticated requests

