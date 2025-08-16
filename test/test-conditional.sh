#!/bin/bash

# Conditional execution tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Conditional Action Execution"

# Test basic conditions
test_condition "Project is open" "project" "true"
test_condition "Has project" "hasProject" "true"

# Test if-then patterns
test_command "If project then CollapseAll" "ij --if project --then CollapseAll"
test_command "If editor then SaveAll" "ij --if editor --then SaveAll"
test_command "If file then SaveAll" "ij --if file --then SaveAll"

# Test if-then-else patterns
test_command "If-then-else with true condition" "ij --if project --then CollapseAll --else ExpandAll"
test_command "If-then-else with likely false condition" "ij --if InvalidCondition --then CollapseAll --else ExpandAll"

# Test document state conditions
test_command "If has modifications conditional" "ij --if hasModifications --then SaveAll --else CollapseAll"
test_command "If has errors conditional" "ij --if hasErrors --then ShowErrorDescription --else CollapseAll"
test_command "If has selection conditional" "ij --if hasSelection --then CommentByLineComment --else CollapseAll"

# Test IDE state conditions
test_command "If indexing conditional" "ij --if isIndexing --then CollapseAll --else ExpandAll"
test_command "If git repository conditional" "ij --if gitRepository --then CollapseAll --else ExpandAll"

# Test focus conditions
test_command "If focus in editor" "ij --if focusInEditor --then SaveAll --else CollapseAll"
test_command "If focus in project" "ij --if focusInProject --then CollapseAll --else ExpandAll"
test_command "If focus in terminal" "ij --if focusInTerminal --then CollapseAll --else ExpandAll"
test_command "If IDE has focus" "ij --if hasFocus --then CollapseAll --else ExpandAll"

# Test file type conditions (may pass or fail depending on current file)
test_command "If file type conditional" "ij --if fileType:java --then CollapseAll --else ExpandAll"
test_command "If file extension conditional" "ij --if hasExtension:kt --then CollapseAll --else ExpandAll"

# Test action availability conditions
test_command "If action enabled" "ij --if CollapseAll:enabled --then CollapseAll"
test_command "If SaveAll enabled" "ij --if SaveAll:enabled --then SaveAll"

# Test tool window conditions
test_command "If terminal window visible" "ij --if Terminal:window --then CollapseAll --else ExpandAll"
test_command "If project window visible" "ij --if Project:window --then CollapseAll --else ExpandAll"

# Test shortcut conditional flags
test_command "Short flag -ie (if editor)" "ij -ie --then SaveAll --else OpenFile"
test_command "Short flag -if (if file)" "ij -if --then SaveAll"
test_command "Short flag -ip (if project)" "ij -ip --then CollapseAll"
test_command "Short flag -ia (if action)" "ij -ia CollapseAll --then CollapseAll"
test_command "Short flag -iw (if window)" "ij -iw Terminal --then CollapseAll --else ExpandAll"

# Test conditional with multiple actions
test_command "Conditional with action chain" "ij --if project --then SaveAll,CollapseAll,ExpandAll"
test_command "Conditional with else chain" "ij --if InvalidCondition --then CollapseAll --else SaveAll,ExpandAll"

# Test API conditional endpoint
test_api "Conditional execution via API" "/execute/conditional?if=project&then=CollapseAll"
test_api "Conditional with else via API" "/execute/conditional?if=InvalidCondition&then=CollapseAll&else=ExpandAll"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi