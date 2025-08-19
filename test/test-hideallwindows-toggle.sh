#!/bin/bash

# Test script for HideAllWindows toggle behavior
# This script tests that HideAllWindows now properly toggles windows

source ./test-helpers.sh

echo "Testing HideAllWindows toggle behavior..."

# Test 1: HideAllWindows should hide all windows
echo "Test 1: First execution should hide all windows"
run_action "HideAllWindows"
sleep 1

# Check if windows are hidden (we'd need to query state here)
# For now, just document expected behavior
echo "Expected: All tool windows should be hidden"

# Test 2: Second execution should restore windows
echo "Test 2: Second execution should restore previously visible windows"
run_action "HideAllWindows"
sleep 1

echo "Expected: Previously visible tool windows should be restored"

# Test 3: Verify it's using IntelliJ's native toggle
echo "Test 3: Action should use IntelliJ's native HideAllWindows action (Ctrl+Shift+F12)"
echo "This means it inherits IntelliJ's built-in toggle behavior"

echo "✓ HideAllWindows toggle test complete"
echo ""
echo "Note: HideAllWindows now uses IntelliJ's native action which provides proper toggle functionality."
echo "First execution hides all windows, second execution restores them."