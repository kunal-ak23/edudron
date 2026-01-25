# Email Case-Insensitive Authentication Fix

## Problem
Emails were being stored and authenticated in a case-sensitive manner. If a student was imported with "Student@test.com", they needed to type exactly "Student@test.com" to authenticate. This is incorrect behavior as email addresses are case-insensitive by RFC standards.

## Solution
Implemented comprehensive email normalization to lowercase throughout the authentication and user management system.

## Changes Made

### 1. User Entity (`identity/domain/User.java`)
- Updated `setEmail()` method to automatically normalize emails to lowercase and trim whitespace
- Updated the constructor to normalize emails when creating new User instances
- This ensures all emails are stored consistently in lowercase

### 2. AuthService (`identity/service/AuthService.java`)
- **login()**: Normalize email input before querying for users
- **register()**: Normalize email input before creating new users
- Ensures case-insensitive authentication for both SYSTEM_ADMIN and tenant-scoped users

### 3. UserService (`identity/service/UserService.java`)
- **createUser()**: Normalize email input before creating users
- **updateUser()**: Normalize email input before updating user emails
- **createSystemAdminUser()**: Normalize email input for SYSTEM_ADMIN creation
- Ensures consistent email handling across all user management operations

## Database Migration

A SQL migration script has been created at `scripts/normalize-user-emails.sql` to normalize existing user emails in the database.

### Steps to Apply:

1. **Check for potential conflicts** (emails that differ only in case within the same tenant):
   ```sql
   SELECT 
       client_id,
       LOWER(TRIM(email)) as normalized_email,
       COUNT(*) as count,
       STRING_AGG(email, ', ') as original_emails
   FROM idp.users
   GROUP BY client_id, LOWER(TRIM(email))
   HAVING COUNT(*) > 1;
   ```

2. **If no conflicts exist**, run the update:
   ```sql
   UPDATE idp.users 
   SET email = LOWER(TRIM(email))
   WHERE email != LOWER(TRIM(email));
   ```

3. **Verify the update**:
   ```sql
   SELECT COUNT(*) FROM idp.users WHERE email != LOWER(TRIM(email));
   -- Should return 0
   ```

## Benefits

1. **User-friendly**: Users can now login with any case variation of their email
   - `Student@test.com`, `student@test.com`, `STUDENT@TEST.COM` all work
2. **RFC Compliant**: Email addresses are case-insensitive per RFC 5321
3. **Prevents duplicate accounts**: Users can't accidentally create multiple accounts with different cases
4. **Consistent behavior**: Works across all authentication flows (login, registration, bulk import)

## Testing

After deployment, test the following scenarios:

1. **Login with different cases**:
   - Import a student with `Student@test.com`
   - Try logging in with `student@test.com`
   - Try logging in with `STUDENT@TEST.COM`
   - All should work successfully

2. **Registration**:
   - Register with `NewUser@Test.com`
   - Verify email is stored as `newuser@test.com` in database
   - Login with `NEWUSER@TEST.COM` should work

3. **Bulk Import**:
   - Import students with mixed case emails
   - Verify all can login regardless of case used

## Files Modified

1. `identity/src/main/java/com/datagami/edudron/identity/domain/User.java`
2. `identity/src/main/java/com/datagami/edudron/identity/service/AuthService.java`
3. `identity/src/main/java/com/datagami/edudron/identity/service/UserService.java`

## Files Created

1. `scripts/normalize-user-emails.sql` - Database migration script
2. `EMAIL_CASE_INSENSITIVE_FIX.md` - This documentation

## Important Notes

- **Existing JWTs remain valid**: The email in JWT tokens will be whatever was issued, but authentication will normalize it for lookups
- **No breaking changes**: Existing users will continue to work with their current credentials
- **Automatic normalization**: All future user creation will automatically normalize emails
- **Bulk import compatibility**: Works with existing bulk import functionality
