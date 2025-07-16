#!/bin/bash

# ADK Integration Test Script
# Tests the ADK agents through REST API endpoints

BASE_URL="http://localhost:8080/api/adk"
LOG_DIR="tmp/logs"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== ADK Integration Test Script ===${NC}"
echo "Base URL: $BASE_URL"
echo "Log Directory: $LOG_DIR"
echo ""

# Create log directory if it doesn't exist
mkdir -p $LOG_DIR

# Test 1: Health Check
echo -e "${YELLOW}Test 1: Health Check${NC}"
curl -X GET "$BASE_URL/health" \
  -H "Accept: application/json" | jq .
echo -e "\n"

# Test 2: Simple Query
echo -e "${YELLOW}Test 2: Simple Query - What does UserService do?${NC}"
curl -X POST "$BASE_URL/query" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "query": "What does UserService do?"
  }' | jq .
echo -e "\n"

# Test 3: Complex Query with Options
echo -e "${YELLOW}Test 3: Complex Query - Payment Processing${NC}"
curl -X POST "$BASE_URL/query" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "query": "How does the payment processing system work? Show me the flow from order to payment completion.",
    "options": {
      "depth": "comprehensive",
      "includeTests": "false"
    }
  }' | jq .
echo -e "\n"

# Test 4: Architecture Query
echo -e "${YELLOW}Test 4: Architecture Query${NC}"
curl -X POST "$BASE_URL/query" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "query": "Explain the architecture of the authentication system"
  }' | jq .
echo -e "\n"

# Test 5: Debugging Query
echo -e "${YELLOW}Test 5: Debugging Query${NC}"
curl -X POST "$BASE_URL/query" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "query": "Trace the execution flow when a user logs in"
  }' | jq .
echo -e "\n"

# Test 6: Test Endpoint
echo -e "${YELLOW}Test 6: Test Endpoint with Default Query${NC}"
curl -X GET "$BASE_URL/test" \
  -H "Accept: application/json" | jq .
echo -e "\n"

# Test 7: Test Endpoint with Custom Query
echo -e "${YELLOW}Test 7: Test Endpoint with Custom Query${NC}"
curl -X GET "$BASE_URL/test?query=What%20is%20the%20purpose%20of%20OrderService" \
  -H "Accept: application/json" | jq .
echo -e "\n"

# List generated log files
echo -e "${GREEN}=== Generated Log Files ===${NC}"
ls -la $LOG_DIR/adk_query_*.log 2>/dev/null || echo "No log files found"
echo ""

# Show latest log file
LATEST_LOG=$(ls -t $LOG_DIR/adk_query_*.log 2>/dev/null | head -1)
if [ -f "$LATEST_LOG" ]; then
    echo -e "${GREEN}=== Latest Log File Content (first 50 lines) ===${NC}"
    echo "File: $LATEST_LOG"
    echo "---"
    head -50 "$LATEST_LOG"
fi

echo -e "\n${GREEN}=== Test Complete ===${NC}"