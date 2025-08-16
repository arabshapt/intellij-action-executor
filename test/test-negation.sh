#!/bin/bash

# Negation operator tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Negation Operators"

# Test negation with ! prefix (requires quotes in zsh)
test_command "Negation with ! for editor" "ij --if '!InvalidCondition' --then CollapseAll"
test_command "Negation with ! for projectView" "ij --if '!InvalidToolWindow' --then CollapseAll"
test_command "Negation with ! for isIndexing" "ij --if '!isIndexing' --then CollapseAll"

# Test negation with not: prefix
test_command "Negation with not: for editor" "ij --if not:InvalidCondition --then CollapseAll"
test_command "Negation with not: for projectView" "ij --if not:InvalidToolWindow --then CollapseAll"
test_command "Negation with not: for isIndexing" "ij --if not:isIndexing --then CollapseAll"
test_command "Negation with not: for hasModifications" "ij --if not:hasModifications --then CollapseAll --else SaveAll"

# Test negation API conditions
test_condition "Not project (should be false)" "not:project" "false"
test_condition "Not invalid condition (should be true)" "not:InvalidCondition" "true"

# Test negation with focus conditions
test_command "Not focus in invalid window" "ij --if not:focusInInvalidWindow --then CollapseAll"
test_command "Not focus with !" "ij --if '!focusInInvalidWindow' --then CollapseAll"

# Test negation with tool windows
test_command "Not invalid tool window" "ij --if not:InvalidWindow:window --then CollapseAll"
test_command "Tool window negation with !" "ij --if '!InvalidWindow:window' --then CollapseAll"

# Test negation with file conditions
test_command "Not invalid file type" "ij --if not:fileType:invalidtype --then CollapseAll"
test_command "Not invalid extension" "ij --if not:hasExtension:invalidext --then CollapseAll"

# Test negation with action availability
test_command "Not invalid action enabled" "ij --if not:InvalidAction:enabled --then CollapseAll"
test_command "Action negation with !" "ij --if '!InvalidAction:enabled' --then CollapseAll"

# Test complex negation scenarios
test_command "Double condition with negation" "ij --if not:isIndexing --then SaveAll,CollapseAll"
test_command "Negation in else branch" "ij --if isIndexing --then CollapseAll --else SaveAll"

# Test API negation queries
local response=$(curl -s "${API_BASE}/state/query?checks=project,not:project")
if echo "$response" | grep -q '"project":true' && echo "$response" | grep -q '"not:project":false'; then
    echo -e "${GREEN}✓${NC} API negation query returns correct opposites"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗${NC} API negation query failed"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))

# Test edge cases
test_command "Empty negation defaults to false" "ij --if 'not:' --then CollapseAll --else ExpandAll"
test_command "Multiple negations" "ij --if not:not:project --then CollapseAll --else ExpandAll"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi