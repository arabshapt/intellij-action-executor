#!/bin/bash

# Action chains and OR fallback tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Action Chains and OR Fallbacks"

# Test sequential action chains with comma
test_command "Two actions with comma" "ij SaveAll,CollapseAll"
test_command "Three actions with comma" "ij SaveAll,CollapseAll,ExpandAll"
test_command "Four actions with comma" "ij SaveAll,CollapseAll,ExpandAll,HideActiveWindow"

# Test sequential action chains with space
test_command "Two actions with space" "ij SaveAll CollapseAll"
test_command "Three actions with space" "ij SaveAll CollapseAll ExpandAll"

# Test OR chains with pipe
test_command "OR chain with valid first action" "ij CollapseAll | ExpandAll | SaveAll"
test_command "OR chain with invalid first action" "ij InvalidAction | CollapseAll | ExpandAll"
test_command "OR chain with all invalid except last" "ij Invalid1 | Invalid2 | CollapseAll"

# Test force mode with chains
test_command "Force mode continues on failure" "ij -f InvalidAction,CollapseAll,ExpandAll"
test_command "Force mode with OR chain" "ij -f Invalid1 | Invalid2 | CollapseAll"

# Test chains without force mode (should stop on failure)
test_output_contains "Chain stops on failure without -f" "ij InvalidAction,CollapseAll 2>&1" "Action failed"

# Test mixed operators
test_command "Comma chain followed by OR" "ij SaveAll,CollapseAll | ExpandAll"

# Test conditional with chains
test_command "Conditional with then chain" "ij --if project --then SaveAll,CollapseAll,ExpandAll"
test_command "Conditional with else chain" "ij --if InvalidCondition --then CollapseAll --else SaveAll,ExpandAll"
test_command "Conditional with OR in then" "ij --if project --then 'InvalidAction | CollapseAll'"

# Test API OR chain execution
local response=$(curl -s "${API_BASE}/execute/conditional?actions=InvalidAction|CollapseAll|ExpandAll")
if echo "$response" | grep -q '"success":true'; then
    echo -e "${GREEN}✓${NC} API OR chain execution"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗${NC} API OR chain execution"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))

# Test complex chain scenarios
test_command "Long chain with valid actions" "ij CollapseAll,SaveAll,ExpandAll,HideActiveWindow,SaveAll"
test_command "OR chain with comma chains" "ij 'SaveAll,CollapseAll | ExpandAll,HideActiveWindow'"

# Test chain with fire-and-forget modes
test_command "Detached chain execution" "ij -d SaveAll,CollapseAll"
test_command "Quiet chain execution" "ij -q SaveAll,CollapseAll,ExpandAll"
test_command "Detached quiet force chain" "ij -dqf InvalidAction,CollapseAll,SaveAll"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi