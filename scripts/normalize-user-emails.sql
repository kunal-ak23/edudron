-- Normalize existing user emails to lowercase
-- This script should be run after deploying the code changes for case-insensitive email authentication

-- Update all user emails to lowercase
UPDATE idp.users 
SET email = LOWER(TRIM(email))
WHERE email != LOWER(TRIM(email));

-- Check for any duplicate emails after normalization (within the same tenant)
-- This query will show if there are any conflicts
SELECT 
    client_id,
    LOWER(TRIM(email)) as normalized_email,
    COUNT(*) as count,
    STRING_AGG(email, ', ') as original_emails
FROM idp.users
GROUP BY client_id, LOWER(TRIM(email))
HAVING COUNT(*) > 1;

-- If the above query returns any rows, you'll need to manually resolve those conflicts
-- before running the UPDATE statement

-- For SYSTEM_ADMIN users (who have no client_id), check for duplicates
SELECT 
    LOWER(TRIM(email)) as normalized_email,
    COUNT(*) as count,
    STRING_AGG(email, ', ') as original_emails
FROM idp.users
WHERE client_id IS NULL AND role = 'SYSTEM_ADMIN'
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;
