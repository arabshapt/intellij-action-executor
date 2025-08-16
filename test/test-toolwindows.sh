#!/bin/bash

# Tool window detection tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Tool Window Detection"

# Test basic tool window visibility
test_condition "Project window exists" "Project:window" "true"
test_condition "Terminal window exists" "Terminal:window" "true"

# Test projectView and terminal shortcuts
if is_tool_window_visible "Project"; then
    test_condition "projectView visible" "projectView" "true"
    test_condition "not:projectView" "not:projectView" "false"
else
    test_condition "projectView not visible" "projectView" "false"
    test_condition "not:projectView" "not:projectView" "true"
fi

if is_tool_window_visible "Terminal"; then
    test_condition "terminal visible" "terminal" "true"
    test_condition "not:terminal" "not:terminal" "false"
else
    test_condition "terminal not visible" "terminal" "false"
    test_condition "not:terminal" "not:terminal" "true"
fi

# Test tool window conditionals
test_command "If project view conditional" "ij --if projectView --then CollapseAll --else ExpandAll"
test_command "If terminal conditional" "ij --if terminal --then CollapseAll --else ExpandAll"
test_command "If not project view" "ij --if not:projectView --then CollapseAll --else ExpandAll"
test_command "If not terminal" "ij --if not:terminal --then CollapseAll --else ExpandAll"

# Test specific tool window checks
test_command "Check Project window" "ij --if Project:window --then CollapseAll --else ExpandAll"
test_command "Check Terminal window" "ij --if Terminal:window --then CollapseAll --else ExpandAll"
test_command "Check Version Control window" "ij --if 'Version Control:window' --then CollapseAll --else ExpandAll"

# Test tool window active state
test_command "Check if Project is active" "ij --if Project:active --then CollapseAll --else ExpandAll"
test_command "Check if Terminal is active" "ij --if Terminal:active --then CollapseAll --else ExpandAll"

# Test API tool windows endpoint
test_api "Tool windows listing endpoint" "/state/toolwindows"

# Get all tool windows and verify structure
response=$(curl -s "${API_BASE}/state/toolwindows")
if echo "$response" | grep -q '"visible":' && echo "$response" | grep -q '"active":' && echo "$response" | grep -q '"available":'; then
    echo -e "${GREEN}✓${NC} Tool windows API returns correct structure"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗${NC} Tool windows API structure incorrect"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))

# Test activating tool windows conditionally
test_command "Activate Terminal if not visible" "ij --if not:terminal --then ActivateTerminalToolWindow --else CollapseAll"
test_command "Activate Project if not visible" "ij --if not:projectView --then ActivateProjectToolWindow --else CollapseAll"

# Test with -iw shortcut flag
test_command "Short flag -iw for Terminal" "ij -iw Terminal --then CollapseAll --else ExpandAll"
test_command "Short flag -iw for Project" "ij -iw Project --then CollapseAll --else ExpandAll"

# Test invalid tool windows
test_condition "Invalid tool window returns false" "InvalidToolWindow:window" "false"
test_condition "Not invalid tool window returns true" "not:InvalidToolWindow:window" "true"
test_command "Invalid tool window conditional" "ij --if InvalidToolWindow:window --then CollapseAll --else ExpandAll"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi