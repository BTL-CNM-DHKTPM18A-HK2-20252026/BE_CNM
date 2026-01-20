#!/bin/bash
# Test Script for User Registration (Bash/Linux/Mac)

echo "========================================"
echo "Fruvia Backend - User Registration Test"
echo "========================================"
echo ""

BASE_URL="http://localhost:8080/api/v1"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;37m'
NC='\033[0m' # No Color

# Test 1: Register User Successfully
echo -e "${YELLOW}Test 1: Register New User${NC}"
echo -e "${GRAY}Endpoint: POST $BASE_URL/users${NC}"

REGISTER_JSON='{
  "phoneNumber": "0123456789",
  "email": "john.doe@example.com",
  "password": "password123",
  "displayName": "John Doe",
  "firstName": "John",
  "lastName": "Doe"
}'

echo -e "${GRAY}Request Body:${NC}"
echo "$REGISTER_JSON"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d "$REGISTER_JSON")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ SUCCESS${NC}"
  echo -e "${GRAY}Response:${NC}"
  echo "$BODY" | jq .
  
  USER_ID=$(echo "$BODY" | jq -r '.user_id')
  echo ""
  echo -e "${GREEN}User ID: $USER_ID${NC}"
  
  # Test 2: Get User by ID
  echo ""
  echo -e "${YELLOW}Test 2: Get User By ID${NC}"
  echo -e "${GRAY}Endpoint: GET $BASE_URL/users/$USER_ID${NC}"
  
  USER_RESPONSE=$(curl -s -X GET "$BASE_URL/users/$USER_ID")
  echo -e "${GREEN}✓ SUCCESS${NC}"
  echo -e "${GRAY}Response:${NC}"
  echo "$USER_RESPONSE" | jq .
else
  echo -e "${RED}✗ FAILED${NC}"
  echo -e "${RED}HTTP Code: $HTTP_CODE${NC}"
  echo "$BODY"
fi

echo ""
echo "========================================"

# Test 3: Register Duplicate Email (Should Fail)
echo ""
echo -e "${YELLOW}Test 3: Register Duplicate Email (Should Fail)${NC}"

DUPLICATE_JSON='{
  "phoneNumber": "0999888777",
  "email": "john.doe@example.com",
  "password": "password456",
  "displayName": "Duplicate User"
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d "$DUPLICATE_JSON")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" -ne 201 ]; then
  echo -e "${GREEN}✓ EXPECTED FAILURE${NC}"
else
  echo -e "${RED}✗ UNEXPECTED SUCCESS${NC}"
fi

echo ""
echo "========================================"

# Test 4: Register Another Valid User
echo ""
echo -e "${YELLOW}Test 4: Register Another Valid User${NC}"

REGISTER_JSON2='{
  "phoneNumber": "0987654321",
  "email": "jane.smith@example.com",
  "password": "securepass456",
  "displayName": "Jane Smith",
  "firstName": "Jane",
  "lastName": "Smith"
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/users" \
  -H "Content-Type: application/json" \
  -d "$REGISTER_JSON2")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 201 ]; then
  echo -e "${GREEN}✓ SUCCESS${NC}"
  USER_ID2=$(echo "$BODY" | jq -r '.user_id')
  echo -e "${GREEN}User ID: $USER_ID2${NC}"
else
  echo -e "${RED}✗ FAILED${NC}"
  echo "$BODY"
fi

echo ""
echo "========================================"
echo -e "${CYAN}All Tests Completed!${NC}"
echo "========================================"
