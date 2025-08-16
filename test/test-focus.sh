#!/bin/bash

# Focus detection tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Focus Detection"

# Test basic focus conditions
test_command "If focus in editor" "ij --if focusInEditor --then CollapseAll --else ExpandAll"
test_command "If focus in project" "ij --if focusInProject --then CollapseAll --else ExpandAll"
test_command "If focus in terminal" "ij --if focusInTerminal --then CollapseAll --else ExpandAll"
test_command "If focus in any tool window" "ij --if focusInToolWindow --then CollapseAll --else ExpandAll"
test_command "If IDE has focus" "ij --if hasFocus --then CollapseAll --else ExpandAll"

# Test negated focus conditions
test_command "If not focus in editor" "ij --if not:focusInEditor --then CollapseAll --else ExpandAll"
test_command "If not focus in project" "ij --if not:focusInProject --then CollapseAll --else ExpandAll"
test_command "If not focus in terminal" "ij --if not:focusInTerminal --then CollapseAll --else ExpandAll"
test_command "If IDE doesn't have focus" "ij --if not:hasFocus --then CollapseAll --else ExpandAll"

# Test parameterized focus conditions
test_command "Focus in specific tool window" "ij --if focusInToolWindow:Project --then CollapseAll --else ExpandAll"
test_command "Focus in Database tool window" "ij --if focusInToolWindow:Database --then CollapseAll --else ExpandAll"
test_command "Focus in specific file" "ij --if focusInFile:Main.java --then CollapseAll --else ExpandAll"

# Test API focus conditions
test_condition "Focus in editor check" "focusInEditor" "true"
test_condition "Focus in project check" "focusInProject" "true"
test_condition "Has focus check" "hasFocus" "true"

# Test focus condition combinations
test_command "Complex focus check" "ij --if focusInEditor --then SaveAll --else 'ij --if focusInProject --then ExpandAll --else CollapseAll'"

# Test focus with action execution
test_command "Save if editor focused" "ij --if focusInEditor --then SaveAll"
test_command "Clear terminal if focused" "ij --if focusInTerminal --then TerminalClearBuffer --else CollapseAll"
test_command "Hide window if tool window focused" "ij --if focusInToolWindow --then HideActiveWindow --else CollapseAll"

# Test edge cases
test_command "Invalid focus target" "ij --if focusInToolWindow:InvalidWindow --then CollapseAll --else ExpandAll"
test_command "Invalid file focus" "ij --if focusInFile:NonExistent.txt --then CollapseAll --else ExpandAll"

# Test focus alternative syntax
test_command "Editor has focus alternative" "ij --if editorHasFocus --then SaveAll --else CollapseAll"
test_command "IDE focused alternative" "ij --if ideFocused --then CollapseAll --else ExpandAll"

# Test focus with other conditions
test_command "Focus and modifications check" "ij --if focusInEditor --then 'ij --if hasModifications --then SaveAll --else CollapseAll'"
test_command "Focus and project check" "ij --if hasFocus --then 'ij --if project --then CollapseAll'"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi