#!/bin/bash

# Script to create a super admin user for EduDron
# Usage: ./scripts/create-super-admin.sh <email> <password> <name> [phone]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if required parameters are provided
if [ $# -lt 3 ]; then
    echo -e "${RED}Usage: $0 <email> <password> <name> [phone]${NC}"
    echo "Example: $0 admin@edudron.com 'SecurePass123!' 'Super Admin' '+1234567890'"
    exit 1
fi

EMAIL="$1"
PASSWORD="$2"
NAME="$3"
PHONE="${4:-}"

# Database connection parameters (from .env or defaults)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-edudron}"
DB_USER="${DB_USER:-postgres}"

echo -e "${YELLOW}Creating super admin user for EduDron...${NC}"
echo "Email: $EMAIL"
echo "Name: $NAME"
echo "Phone: $PHONE"
echo ""

# Generate ULID (using a simple approach - in production, use proper ULID generator)
ULID=$(date +%s%3N | base64 | tr -d '=' | tr '+/' 'AB' | cut -c1-26 | tr '[:lower:]' '[:upper:]')

# Generate BCrypt hash using Python (requires bcrypt library)
echo -e "${YELLOW}Generating password hash...${NC}"

# Try Python bcrypt first
BCRYPT_HASH=$(python3 -c "
try:
    import bcrypt
    import sys
    password = sys.argv[1]
    # Use strength 10 (rounds) to match Spring Security BCryptPasswordEncoder default
    # Strength 10 = 2^10 = 1024 rounds
    salt = bcrypt.gensalt(rounds=10)
    hash = bcrypt.hashpw(password.encode('utf-8'), salt)
    print(hash.decode('utf-8'))
except ImportError:
    print('BCRYPT_NOT_AVAILABLE')
except Exception as e:
    print('BCRYPT_ERROR')
" "$PASSWORD" 2>/dev/null)

if [ "$BCRYPT_HASH" = "BCRYPT_NOT_AVAILABLE" ]; then
    echo -e "${YELLOW}Python bcrypt not available. Trying alternative method...${NC}"
    
    # Try using Node.js bcrypt if available
    if command -v node >/dev/null 2>&1; then
        BCRYPT_HASH=$(node -e "
            try {
                const bcrypt = require('bcrypt');
                const password = process.argv[1];
                const hash = bcrypt.hashSync(password, 10);
                console.log(hash);
            } catch (e) {
                console.log('NODE_BCRYPT_NOT_AVAILABLE');
            }
        " "$PASSWORD" 2>/dev/null)
        
        if [ "$BCRYPT_HASH" = "NODE_BCRYPT_NOT_AVAILABLE" ]; then
            echo -e "${RED}Error: Neither Python bcrypt nor Node.js bcrypt is available.${NC}"
            echo -e "${YELLOW}Please install one of the following:${NC}"
            echo "1. Python: pip3 install bcrypt"
            echo "2. Node.js: npm install -g bcrypt-cli"
            echo ""
            echo -e "${YELLOW}Or use the manual SQL method with a pre-generated hash:${NC}"
            echo "1. Go to: https://bcrypt-generator.com/"
            echo "2. Enter your password: $PASSWORD"
            echo "3. Copy the generated hash"
            echo "4. Run: psql -U $DB_USER -d $DB_NAME -c \"INSERT INTO idp.users (id, client_id, email, password, name, role, created_at, active) VALUES ('$ULID', NULL, '$EMAIL', 'YOUR_HASH_HERE', '$NAME', 'SYSTEM_ADMIN', NOW(), true);\""
            exit 1
        fi
    else
        echo -e "${RED}Error: Neither Python bcrypt nor Node.js is available.${NC}"
        echo -e "${YELLOW}Please install one of the following:${NC}"
        echo "1. Python: pip3 install bcrypt"
        echo "2. Node.js: npm install -g bcrypt-cli"
        echo ""
        echo -e "${YELLOW}Or use the manual SQL method with a pre-generated hash:${NC}"
        echo "1. Go to: https://bcrypt-generator.com/"
        echo "2. Enter your password: $PASSWORD"
        echo "3. Copy the generated hash"
        echo "4. Run: psql -U $DB_USER -d $DB_NAME -c \"INSERT INTO idp.users (id, client_id, email, password, name, role, created_at, active) VALUES ('$ULID', NULL, '$EMAIL', 'YOUR_HASH_HERE', '$NAME', 'SYSTEM_ADMIN', NOW(), true);\""
        exit 1
    fi
elif [ "$BCRYPT_HASH" = "BCRYPT_ERROR" ] || [ -z "$BCRYPT_HASH" ]; then
    echo -e "${RED}Error: Failed to generate password hash.${NC}"
    exit 1
fi

# Create the SQL script
SQL_FILE="/tmp/create_super_admin_$(date +%s).sql"
cat > "$SQL_FILE" << EOF
-- Create super admin user (NOT tied to any tenant)
INSERT INTO idp.users (
    id,
    client_id,  -- NULL for SYSTEM_ADMIN users
    email,
    password,
    name,
    phone,
    role,
    created_at,
    active
) VALUES (
    '$ULID',
    NULL,  -- SYSTEM_ADMIN users are not tied to any tenant
    '$EMAIL',
    '$BCRYPT_HASH',
    '$NAME',
    '$PHONE',
    'SYSTEM_ADMIN',
    NOW(),
    true
) ON CONFLICT (email) WHERE client_id IS NULL AND role = 'SYSTEM_ADMIN' DO UPDATE SET
    role = 'SYSTEM_ADMIN',
    active = true,
    updated_at = NOW();

-- Verify the user was created
SELECT 
    id,
    email,
    name,
    role,
    client_id,
    active,
    created_at
FROM idp.users 
WHERE role = 'SYSTEM_ADMIN' AND email = '$EMAIL';
EOF

echo -e "${YELLOW}Executing SQL...${NC}"
if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SQL_FILE"; then
    echo -e "${GREEN}✅ Super admin user created successfully!${NC}"
    echo -e "${GREEN}User ID: $ULID${NC}"
    echo -e "${GREEN}Email: $EMAIL${NC}"
    echo -e "${GREEN}Role: SYSTEM_ADMIN${NC}"
else
    echo -e "${RED}❌ Failed to create super admin user${NC}"
    exit 1
fi

# Clean up
rm -f "$SQL_FILE"

echo ""
echo -e "${YELLOW}You can now login with:${NC}"
echo "Email: $EMAIL"
echo "Password: [your provided password]"
echo ""
echo -e "${YELLOW}API Login Example (NO tenant context required for SYSTEM_ADMIN):${NC}"
echo "curl -X POST http://localhost:8081/auth/login \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"email\":\"$EMAIL\",\"password\":\"[your password]\"}'"
echo ""
echo -e "${YELLOW}Note: SYSTEM_ADMIN users can access the entire system across all tenants${NC}"

