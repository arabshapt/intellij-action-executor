#!/bin/bash

# Test helper functions for IntelliJ Action Executor

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
CURRENT_SUITE=""

# API endpoint
API_BASE="http://localhost:63343/api/intellij-actions"

# Start a test suite
start_suite() {
    local suite_name="$1"
    CURRENT_SUITE="$suite_name"
    echo -e "\n${BLUE}=== Testing: $suite_name ===${NC}"
}

# Run a test and check if it succeeds (exit code 0)
test_command() {
    local description="$1"
    local command="$2"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $description"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        echo -e "  ${YELLOW}Command: $command${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Run a test and check if it fails (exit code non-zero)
test_command_fails() {
    local description="$1"
    local command="$2"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${RED}✗${NC} $description (expected to fail but succeeded)"
        echo -e "  ${YELLOW}Command: $command${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    else
        echo -e "${GREEN}✓${NC} $description (correctly failed)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    fi
}

# Test if a condition returns the expected value
test_condition() {
    local description="$1"
    local condition="$2"
    local expected="$3"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    local response=$(curl -s "${API_BASE}/state/query?checks=$condition")
    local actual=$(echo "$response" | grep -o "\"$condition\":[^,}]*" | cut -d: -f2)
    
    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}✓${NC} $description (condition: $condition = $expected)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        echo -e "  ${YELLOW}Condition: $condition, Expected: $expected, Got: $actual${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Test if an API endpoint returns success
test_api() {
    local description="$1"
    local endpoint="$2"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    local response=$(curl -s "${API_BASE}${endpoint}")
    
    if echo "$response" | grep -q '"success":true' || echo "$response" | grep -q '"status":"healthy"'; then
        echo -e "${GREEN}✓${NC} $description"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        echo -e "  ${YELLOW}Endpoint: ${endpoint}${NC}"
        echo -e "  ${YELLOW}Response: ${response:0:100}...${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Test if output contains expected text
test_output_contains() {
    local description="$1"
    local command="$2"
    local expected="$3"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    local output=$(eval "$command" 2>&1)
    
    if echo "$output" | grep -q "$expected"; then
        echo -e "${GREEN}✓${NC} $description"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}✗${NC} $description"
        echo -e "  ${YELLOW}Command: $command${NC}"
        echo -e "  ${YELLOW}Expected to contain: $expected${NC}"
        echo -e "  ${YELLOW}Got: ${output:0:100}...${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Check if a tool window is visible
is_tool_window_visible() {
    local window_id="$1"
    local response=$(curl -s "${API_BASE}/state/query?checks=${window_id}:window")
    echo "$response" | grep -q "\"${window_id}:window\":true"
}

# Wait for a condition to be true (with timeout)
wait_for_condition() {
    local condition="$1"
    local timeout="${2:-5}"  # Default 5 seconds
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        if test_condition "Waiting for $condition" "$condition" "true" > /dev/null 2>&1; then
            return 0
        fi
        sleep 0.5
        elapsed=$((elapsed + 1))
    done
    
    return 1
}

# Print test summary
print_summary() {
    echo -e "\n${BLUE}=== Test Summary ===${NC}"
    echo -e "Total tests: $TESTS_RUN"
    echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
    
    if [ $TESTS_FAILED -gt 0 ]; then
        echo -e "${RED}Failed: $TESTS_FAILED${NC}"
        return 1
    else
        echo -e "${GREEN}All tests passed!${NC}"
        return 0
    fi
}

# Reset test counters
reset_counters() {
    TESTS_RUN=0
    TESTS_PASSED=0
    TESTS_FAILED=0
}

# Check if IntelliJ is running
check_intellij_running() {
    if ! curl -s "${API_BASE}/health" | grep -q '"status":"healthy"'; then
        echo -e "${RED}Error: IntelliJ IDEA is not running or the plugin is not loaded${NC}"
        echo "Please start IntelliJ IDEA and ensure the Action Executor plugin is installed"
        exit 1
    fi
}

# Skip test with reason
skip_test() {
    local description="$1"
    local reason="$2"
    echo -e "${YELLOW}⊘${NC} $description (SKIPPED: $reason)"
}