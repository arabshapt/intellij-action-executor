#!/bin/bash

# Basic action execution tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Basic Action Execution"

# Test single actions
test_command "Execute CollapseAll action" "ij CollapseAll"
test_command "Execute SaveAll action" "ij SaveAll"
test_command "Execute ExpandAll action" "ij ExpandAll"

# Test multiple sequential actions with comma
test_command "Execute multiple actions with comma" "ij SaveAll,CollapseAll"
test_command "Execute three actions with comma" "ij SaveAll,CollapseAll,ExpandAll"

# Test multiple sequential actions with space
test_command "Execute multiple actions with space" "ij SaveAll CollapseAll"
test_command "Execute three actions with space" "ij SaveAll CollapseAll ExpandAll"

# Test invalid actions (should fail)
test_command_fails "Execute invalid action" "ij InvalidActionThatDoesNotExist"

# Test action availability check
test_api "Check action availability endpoint" "/check?action=CollapseAll"
test_api "Health check endpoint" "/health"

# Test that output contains expected text
test_output_contains "CollapseAll action output" "ij CollapseAll" "Action executed"
test_output_contains "Invalid action error" "ij InvalidAction 2>&1" "Action failed"

# Test discovery features
test_command "Search for actions" "ij --search format"
test_command "Explain an action" "ij --explain CollapseAll"
test_command "List all actions" "ij --list"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi