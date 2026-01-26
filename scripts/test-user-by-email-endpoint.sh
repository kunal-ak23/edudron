#!/bin/bash

# Test script for the new /idp/users/by-email endpoint
# This helps verify the endpoint is deployed and working correctly

# Configuration
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TEST_EMAIL="${1}"
TOKEN="${2}"
CLIENT_ID="${3}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

if [ -z "$TEST_EMAIL" ]; then
    echo -e "${RED}Usage: $0 <email> <jwt-token> <client-id>${NC}"
    echo ""
    echo "Example:"
    echo "  $0 student@example.com eyJhbG... uuid-here"
    echo ""
    echo "Or set environment variables:"
    echo "  export GATEWAY_URL=http://localhost:8080"
    echo "  export JWT_TOKEN=your-token"
    echo "  export CLIENT_ID=your-client-id"
    echo "  $0 student@example.com"
    exit 1
fi

# Use environment variables if token and client_id not provided
TOKEN="${TOKEN:-$JWT_TOKEN}"
CLIENT_ID="${CLIENT_ID:-$CLIENT_ID}"

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Error: JWT token is required${NC}"
    exit 1
fi

if [ -z "$CLIENT_ID" ]; then
    echo -e "${YELLOW}Warning: CLIENT_ID not provided, using token without tenant context${NC}"
fi

# URL encode the email
ENCODED_EMAIL=$(printf %s "$TEST_EMAIL" | jq -sRr @uri)
URL="$GATEWAY_URL/idp/users/by-email?email=$ENCODED_EMAIL"

echo -e "${YELLOW}Testing new endpoint: GET /idp/users/by-email${NC}"
echo "URL: $URL"
echo "Email: $TEST_EMAIL"
echo ""

# Make the request
if [ -n "$CLIENT_ID" ]; then
    echo -e "${YELLOW}Making request with tenant context...${NC}"
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $TOKEN" \
        -H "X-Client-Id: $CLIENT_ID" \
        -H "Content-Type: application/json" \
        "$URL")
else
    echo -e "${YELLOW}Making request without tenant context...${NC}"
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        "$URL")
fi

# Split response and status code
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo ""
echo "HTTP Status Code: $HTTP_CODE"
echo ""

case $HTTP_CODE in
    200)
        echo -e "${GREEN}✓ Success! User found:${NC}"
        echo "$BODY" | jq '.'
        echo ""
        echo -e "${GREEN}The endpoint is working correctly!${NC}"
        exit 0
        ;;
    404)
        echo -e "${YELLOW}User not found (404)${NC}"
        echo "This is expected if the user doesn't exist"
        echo ""
        echo -e "${GREEN}The endpoint is working correctly (returns 404 for non-existent users)${NC}"
        exit 0
        ;;
    403)
        echo -e "${RED}✗ Permission denied (403)${NC}"
        echo "$BODY"
        echo ""
        echo -e "${RED}Check that the token has proper permissions${NC}"
        exit 1
        ;;
    404)
        echo -e "${RED}✗ Endpoint not found (404)${NC}"
        echo "This means the new endpoint is NOT deployed yet"
        echo ""
        echo -e "${RED}You need to rebuild and restart the identity service:${NC}"
        echo "  ./gradlew :identity:build"
        echo "  # Then restart the identity service"
        exit 1
        ;;
    *)
        echo -e "${RED}✗ Unexpected error ($HTTP_CODE)${NC}"
        echo "$BODY"
        exit 1
        ;;
esac
