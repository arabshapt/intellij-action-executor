#!/bin/bash

# Fire-and-forget mode tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Fire-and-Forget Modes"

# Test detach mode (-d)
test_command "Detach mode single action" "ij -d CollapseAll"
test_command "Detach mode multiple actions" "ij -d SaveAll,CollapseAll"
test_output_contains "Detach mode shows minimal output" "ij -d CollapseAll" "Detached:"

# Test quiet mode (-q)
test_command "Quiet mode single action" "ij -q CollapseAll"
test_command "Quiet mode multiple actions" "ij -q SaveAll,CollapseAll"

# Test if quiet mode suppresses output
output=$(ij -q CollapseAll 2>&1)
if [ -z "$output" ] || ! echo "$output" | grep -q "Action executed"; then
    echo -e "${GREEN}✓${NC} Quiet mode suppresses output"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗${NC} Quiet mode should suppress output"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))

# Test force mode (-f)
test_command "Force mode continues on failure" "ij -f InvalidAction,CollapseAll"
test_command "Force mode with all valid actions" "ij -f SaveAll,CollapseAll,ExpandAll"

# Test combined modes
test_command "Detach + quiet mode" "ij -dq SaveAll,CollapseAll"
test_command "Detach + force mode" "ij -df InvalidAction,CollapseAll"
test_command "Quiet + force mode" "ij -qf InvalidAction,CollapseAll"
test_command "All three modes combined" "ij -dqf InvalidAction,SaveAll,CollapseAll"

# Test combined short flags
test_command "Combined flags -dq" "ij -dq CollapseAll"
test_command "Combined flags -df" "ij -df CollapseAll"
test_command "Combined flags -qf" "ij -qf CollapseAll"
test_command "Combined flags -dqf" "ij -dqf CollapseAll"

# Test long flag versions
test_command "Long flag --detach" "ij --detach CollapseAll"
test_command "Long flag --quiet" "ij --quiet CollapseAll"
test_command "Long flag --force" "ij --force InvalidAction,CollapseAll"

# Test with conditional execution
test_command "Detach with conditional" "ij -d --if project --then CollapseAll"
test_command "Quiet with conditional" "ij -q --if project --then SaveAll"
test_command "Force with conditional chains" "ij -f --if project --then InvalidAction,CollapseAll"

# Test with OR chains
test_command "Detach with OR chain" "ij -d 'InvalidAction | CollapseAll'"
test_command "Force with OR chain" "ij -f 'Invalid1 | Invalid2 | CollapseAll'"

# Test rapid execution
echo -e "${BLUE}Testing rapid detached execution...${NC}"
success=true
for i in {1..5}; do
    if ! ij -d CollapseAll > /dev/null 2>&1; then
        success=false
        break
    fi
done

if $success; then
    echo -e "${GREEN}✓${NC} Rapid detached execution (5 times)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}✗${NC} Rapid detached execution failed"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
TESTS_RUN=$((TESTS_RUN + 1))

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi