#!/bin/bash

# Terminal focus switching tests

# Source helper functions
source "$(dirname "$0")/test-helpers.sh"

# Check if IntelliJ is running
check_intellij_running

# Start test suite
start_suite "Terminal Focus Switching"

echo -e "${CYAN}Testing terminal focus switching actions...${NC}"
echo "Note: These tests require manual verification of focus changes"
echo ""

# Test FocusEditor (Universal action)
echo -e "${YELLOW}Test 0: FocusEditor - Universal Focus Action${NC}"
echo "1. Test from terminal"
test_command "Open Terminal" "ij ActivateTerminalToolWindow"
sleep 1
test_command "Focus Editor from Terminal" "ij FocusEditor"
sleep 1

echo "2. Test from project view"
test_command "Open Project View" "ij ActivateProjectToolWindow"
sleep 1
test_command "Focus Editor from Project View" "ij FocusEditor"
sleep 1

echo "3. Check focus state after FocusEditor"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor" | jq '.' 2>/dev/null || cat
echo ""

# Test Terminal.MoveToEditor
echo -e "${YELLOW}Test 1: Terminal.MoveToEditor${NC}"
echo "1. First, open the terminal (if not already open)"
test_command "Open Terminal" "ij ActivateTerminalToolWindow"
sleep 2

echo "2. Now move focus from terminal to editor"
test_command "Move focus from Terminal to Editor" "ij Terminal.MoveToEditor"
sleep 1

echo "3. Check current focus state"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,focusInTerminal" | jq '.' 2>/dev/null || cat
echo ""

# Test Terminal.Escape
echo -e "${YELLOW}Test 2: Terminal.Escape${NC}"
echo "1. Activate terminal again"
test_command "Activate Terminal" "ij ActivateTerminalToolWindow"
sleep 2

echo "2. Use Terminal.Escape to move focus away"
test_command "Terminal Escape" "ij Terminal.Escape"
sleep 1

echo "3. Check focus state after escape"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,focusInTerminal" | jq '.' 2>/dev/null || cat
echo ""

# Test EditorEscape
echo -e "${YELLOW}Test 3: EditorEscape${NC}"
echo "1. Make sure focus is in editor"
test_command "Focus Editor (via JumpToLastWindow)" "ij JumpToLastWindow"
sleep 1

echo "2. Use EditorEscape"
test_command "Editor Escape" "ij EditorEscape"
sleep 1

echo "3. Check focus state"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,focusInTerminal,focusInToolWindow" | jq '.' 2>/dev/null || cat
echo ""

# Test JumpToLastWindow
echo -e "${YELLOW}Test 4: JumpToLastWindow${NC}"
echo "1. Focus editor first"
test_command "Focus editor" "ij --if focusInToolWindow --then JumpToLastWindow"
sleep 1

echo "2. Open Project view"
test_command "Open Project view" "ij ActivateProjectToolWindow"
sleep 2

echo "3. Jump to last window (should go back to editor)"
test_command "Jump to last window" "ij JumpToLastWindow"
sleep 1

echo "4. Check focus is back in editor"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,focusInProject" | jq '.' 2>/dev/null || cat
echo ""

# Test conditional focus switching
echo -e "${YELLOW}Test 5: Conditional Focus Switching${NC}"
test_command "If terminal focused, move to editor" "ij --if focusInTerminal --then Terminal.MoveToEditor --else ActivateTerminalToolWindow"
sleep 1

test_command "If editor focused, open terminal" "ij --if focusInEditor --then ActivateTerminalToolWindow --else JumpToLastWindow"
sleep 1

# Test focus chain
echo -e "${YELLOW}Test 6: Focus Action Chain${NC}"
test_command "Chain: Open terminal, then move to editor" "ij ActivateTerminalToolWindow Terminal.MoveToEditor"
sleep 2

# Test HideActiveWindow with focus
echo -e "${YELLOW}Test 7: Hide Window and Focus${NC}"
test_command "Open terminal" "ij ActivateTerminalToolWindow"
sleep 1

test_command "Hide active window (terminal)" "ij HideActiveWindow"
sleep 1

echo "Check that focus moved to editor after hiding terminal:"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,terminal" | jq '.' 2>/dev/null || cat
echo ""

# Test HideAllWindows
echo -e "${YELLOW}Test 8: Hide All Windows${NC}"
test_command "Open multiple tool windows" "ij ActivateProjectToolWindow ActivateTerminalToolWindow"
sleep 2

test_command "Hide all windows" "ij HideAllWindows"
sleep 1

echo "Check that all tool windows are hidden and focus is in editor:"
curl -s "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,projectView,terminal,focusInToolWindow" | jq '.' 2>/dev/null || cat
echo ""

# Advanced focus state checks
echo -e "${YELLOW}Test 9: Focus State Verification${NC}"
echo "Checking various focus states..."

# Check if focus detection is working properly
test_condition "Has IDE focus" "hasFocus" "true"
test_condition "Focus in editor check" "focusInEditor" "true|false"
test_condition "Focus in terminal check" "focusInTerminal" "true|false"
test_condition "Focus in any tool window" "focusInToolWindow" "true|false"

# Test error cases
echo -e "${YELLOW}Test 10: Error Cases${NC}"
echo "Testing focus actions when preconditions are not met..."

test_command "Terminal.MoveToEditor when terminal not open" "ij --if not:terminal --then Terminal.MoveToEditor"
test_command "EditorEscape when no editor open" "ij --if not:editor --then EditorEscape"

# Summary of focus action availability
echo -e "${CYAN}Focus Action Summary:${NC}"
echo "Available focus switching actions:"
echo "  - FocusEditor: ⭐ Universal action to focus editor from anywhere (RECOMMENDED)"
echo "  - Terminal.MoveToEditor: Move focus from terminal to editor"
echo "  - Terminal.Escape: Escape from terminal (moves focus to editor)"
echo "  - EditorEscape: Escape from editor (closes popups, returns focus)"
echo "  - JumpToLastWindow: Toggle between last two focused windows"
echo "  - HideActiveWindow: Hide current tool window and focus editor"
echo "  - HideAllWindows: Hide all tool windows and focus editor"
echo ""
echo "Focus state conditions:"
echo "  - focusInEditor: Check if focus is in editor"
echo "  - focusInTerminal: Check if focus is in terminal"
echo "  - focusInToolWindow: Check if focus is in any tool window"
echo "  - hasFocus: Check if IDE window has OS focus"

# Print summary if run standalone
if [ "${BASH_SOURCE[0]}" == "${0}" ]; then
    print_summary
    exit $?
fi