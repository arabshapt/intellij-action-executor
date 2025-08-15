# Testing Guide

## Table of Contents
1. [Testing Strategy](#testing-strategy)
2. [Unit Testing](#unit-testing)
3. [Integration Testing](#integration-testing)
4. [Manual Testing](#manual-testing)
5. [Performance Testing](#performance-testing)
6. [CLI Testing](#cli-testing)
7. [Regression Testing](#regression-testing)
8. [Test Environments](#test-environments)
9. [Troubleshooting Tests](#troubleshooting-tests)

## Testing Strategy

### Test Pyramid

```
    /\
   /  \     Manual/E2E Tests (Few)
  /____\    
 /      \    Integration Tests (Some)  
/__________\  Unit Tests (Many)
```

### Test Categories

1. **Unit Tests**: Individual component testing
2. **Integration Tests**: Component interaction testing  
3. **Manual Tests**: Full workflow validation
4. **Performance Tests**: Load and stress testing
5. **Regression Tests**: Critical bug prevention

## Unit Testing

### Test Structure

```kotlin
// Example test structure
class ActionExecutorServiceTest {
    private lateinit var service: ActionExecutorService
    private lateinit var mockActionManager: ActionManager
    
    @BeforeEach
    fun setUp() {
        service = ActionExecutorService()
        // Setup mocks
    }
    
    @Test
    fun `should execute valid action successfully`() {
        // Given
        val actionId = "About"
        
        // When
        val result = service.executeAction(actionId)
        
        // Then
        assertTrue(result.success)
        assertEquals(actionId, result.actionId)
    }
}
```

### Running Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "ActionExecutorServiceTest"

# Run with verbose output
./gradlew test --info

# Generate test report
./gradlew test jacocoTestReport
```

### Test Coverage Areas

#### ActionExecutorService Tests
- [ ] Single action execution
- [ ] Multiple action chain execution  
- [ ] Error handling for invalid actions
- [ ] EDT threading behavior
- [ ] Context creation logic
- [ ] Action categorization (dialog, UI, regular)

#### CustomHttpServer Tests
- [ ] Request parsing
- [ ] Response formatting
- [ ] Error handling
- [ ] JSON serialization
- [ ] Thread pool management

#### ActionRestService Tests
- [ ] Endpoint routing
- [ ] Parameter validation
- [ ] Host trust validation
- [ ] Response format consistency

## Integration Testing

### HTTP API Integration Tests

#### Test Environment Setup
```bash
# Start IntelliJ with plugin for testing
./gradlew runIde &
INTELLIJ_PID=$!

# Wait for servers to start
sleep 10

# Run integration tests
./run-integration-tests.sh

# Cleanup
kill $INTELLIJ_PID
```

#### Basic API Tests
```bash
#!/bin/bash
# integration-tests.sh

BASE_URL_CUSTOM="http://localhost:63343/api/intellij-actions"
BASE_URL_BUILTIN="http://localhost:63342/api/intellij-actions"

# Test 1: Health Check
echo "Testing health endpoints..."
curl -s "$BASE_URL_CUSTOM/health" | grep -q "healthy" && echo "✓ Custom server healthy" || echo "✗ Custom server failed"
curl -s "$BASE_URL_BUILTIN/health" | grep -q "healthy" && echo "✓ Built-in server healthy" || echo "✗ Built-in server failed"

# Test 2: Single Action
echo "Testing single action execution..."
RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=About")
echo "$RESPONSE" | grep -q '"success":true' && echo "✓ Single action success" || echo "✗ Single action failed"

# Test 3: Multiple Actions
echo "Testing action chain..."
RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?actions=SaveAll,ReformatCode")
echo "$RESPONSE" | grep -q '"success":true' && echo "✓ Action chain success" || echo "✗ Action chain failed"

# Test 4: Invalid Action
echo "Testing error handling..."
RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=NonExistentAction")
echo "$RESPONSE" | grep -q '"success":false' && echo "✓ Error handling works" || echo "✗ Error handling failed"

# Test 5: Action Check
echo "Testing action availability..."
RESPONSE=$(curl -s "$BASE_URL_CUSTOM/check?action=About")
echo "$RESPONSE" | grep -q '"exists":true' && echo "✓ Action check works" || echo "✗ Action check failed"

# Test 6: Action List
echo "Testing action listing..."
RESPONSE=$(curl -s "$BASE_URL_CUSTOM/list")
echo "$RESPONSE" | grep -q '"actions":\[' && echo "✓ Action list works" || echo "✗ Action list failed"
```

### Context-Dependent Action Tests

Test actions that require specific IDE context:

```bash
# Test file operations (requires open file)
test_file_operations() {
    echo "Testing file operations..."
    
    # These require proper file context
    local file_actions=(
        "CopyPaths"
        "CopyAbsolutePath" 
        "SaveDocument"
        "ReformatCode"
        "OptimizeImports"
    )
    
    for action in "${file_actions[@]}"; do
        RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=$action")
        echo "Testing $action: $(echo "$RESPONSE" | jq -r '.success')"
    done
}

# Test VCS operations (requires Git repository)
test_vcs_operations() {
    echo "Testing VCS operations..."
    
    local vcs_actions=(
        "Git.Branches"
        "Vcs.ShowTabbedFileHistory"
        "CheckinProject"
    )
    
    for action in "${vcs_actions[@]}"; do
        RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=$action")
        echo "Testing $action: $(echo "$RESPONSE" | jq -r '.success')"
    done
}
```

### Performance Integration Tests

```bash
# Test concurrent requests
test_concurrent_execution() {
    echo "Testing concurrent execution..."
    
    for i in {1..10}; do
        curl -s "$BASE_URL_CUSTOM/execute?action=About" &
    done
    wait
    echo "✓ Concurrent test completed"
}

# Test action chain performance
test_chain_performance() {
    echo "Testing action chain performance..."
    
    start_time=$(date +%s%N)
    curl -s "$BASE_URL_CUSTOM/execute?actions=SaveAll,OptimizeImports,ReformatCode"
    end_time=$(date +%s%N)
    
    duration=$(( (end_time - start_time) / 1000000 ))
    echo "Action chain completed in ${duration}ms"
}
```

## Manual Testing

### Pre-Release Testing Checklist

#### Environment Setup
- [ ] IntelliJ IDEA 2024.1+ running
- [ ] Plugin installed and enabled
- [ ] Sample project opened (preferably with Git)
- [ ] At least one file open in editor

#### Core Functionality Tests

##### Single Action Execution
- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?action=About"`
  - [ ] Response contains `"success":true`
  - [ ] About dialog appears
  - [ ] Response time < 500ms

- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?action=ReformatCode"`
  - [ ] Code formatting applied if file is open
  - [ ] Response indicates success

- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?action=NonExistentAction"`
  - [ ] Response contains `"success":false`
  - [ ] Error message indicates action not found

##### Action Chain Execution  
- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?actions=SaveAll,ReformatCode,OptimizeImports"`
  - [ ] All actions execute in sequence
  - [ ] Final response shows all successes
  - [ ] File changes are applied

- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?actions=SaveAll,InvalidAction,ReformatCode"`
  - [ ] Chain stops at invalid action
  - [ ] Response indicates which action failed

##### Context-Dependent Actions
- [ ] Open a file in editor
- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?action=CopyPaths"`
  - [ ] File path copied to clipboard
  - [ ] No EDT violation errors in logs

- [ ] Select files in Project View
- [ ] `curl "http://localhost:63343/api/intellij-actions/execute?action=CopyPaths"`
  - [ ] Selected file paths copied
  - [ ] Multiple files handled correctly

##### Server Functionality
- [ ] Both servers respond to health checks:
  - [ ] `curl "http://localhost:63343/api/intellij-actions/health"`
  - [ ] `curl "http://localhost:63342/api/intellij-actions/health"`

- [ ] Action listing works:
  - [ ] `curl "http://localhost:63343/api/intellij-actions/list"`
  - [ ] Returns array of action IDs
  - [ ] Count matches array length

- [ ] Action checking works:
  - [ ] `curl "http://localhost:63343/api/intellij-actions/check?action=About"`
  - [ ] Returns `"exists":true` for valid actions
  - [ ] Returns `"exists":false` for invalid actions

#### CLI Tool Tests

- [ ] `~/ij About`
  - [ ] Shows success message
  - [ ] About dialog appears

- [ ] `~/ij SaveAll ReformatCode OptimizeImports`
  - [ ] Shows success for each action
  - [ ] All actions execute

- [ ] `~/ij NonExistentAction`
  - [ ] Shows error message
  - [ ] Indicates action not found

- [ ] `~/ij --force-builtin About`
  - [ ] Forces use of built-in API
  - [ ] Still executes successfully

#### Error Handling Tests

- [ ] Stop IntelliJ and test:
  - [ ] `curl "http://localhost:63343/api/intellij-actions/health"`
  - [ ] Should fail with connection refused

- [ ] Start IntelliJ without plugin:
  - [ ] `curl "http://localhost:63342/api/intellij-actions/health"`
  - [ ] Should return 404 or connection refused

- [ ] Test with no project open:
  - [ ] Most actions should still work
  - [ ] Context-dependent actions may fail gracefully

#### Performance Tests

- [ ] Rapid sequential requests:
  ```bash
  for i in {1..50}; do 
    curl -s "http://localhost:63343/api/intellij-actions/execute?action=About"
  done
  ```
  - [ ] No rate limiting errors
  - [ ] All requests succeed
  - [ ] No memory leaks

- [ ] Concurrent requests:
  ```bash
  for i in {1..10}; do 
    curl -s "http://localhost:63343/api/intellij-actions/execute?action=About" &
  done
  wait
  ```
  - [ ] All requests complete
  - [ ] No threading errors
  - [ ] No EDT violations

### Test Scenarios by Action Type

#### File Operations
Required: Open file in editor

- [ ] `SaveDocument` - Saves current file
- [ ] `SaveAll` - Saves all open files  
- [ ] `ReformatCode` - Formats current file
- [ ] `OptimizeImports` - Optimizes imports
- [ ] `CopyPaths` - Copies file path to clipboard
- [ ] `CopyAbsolutePath` - Copies absolute path

#### Navigation Actions
Required: Open project with multiple files

- [ ] `GotoDeclaration` - Goes to symbol definition
- [ ] `FindUsages` - Shows usage popup
- [ ] `GotoClass` - Opens class search
- [ ] `GotoFile` - Opens file search
- [ ] `FindInPath` - Opens global search

#### VCS Actions  
Required: Project with Git repository

- [ ] `Git.Branches` - Shows branch popup
- [ ] `Vcs.ShowTabbedFileHistory` - Shows file history
- [ ] `CheckinProject` - Opens commit dialog
- [ ] `Vcs.UpdateProject` - Pulls from remote

#### UI Actions
Required: Standard IDE setup

- [ ] `SplitVertically` - Splits editor
- [ ] `ActivateProjectToolWindow` - Shows project view
- [ ] `MaximizeToolWindow` - Maximizes active tool window
- [ ] `HideAllWindows` - Hides all tool windows

## Performance Testing

### Load Testing

#### Concurrent Request Test
```bash
#!/bin/bash
# load-test.sh

REQUESTS=100
CONCURRENT=10
URL="http://localhost:63343/api/intellij-actions/execute?action=About"

echo "Running load test: $REQUESTS requests, $CONCURRENT concurrent"

# Apache Bench alternative with curl
for ((i=1; i<=REQUESTS; i++)); do
    if ((i % CONCURRENT == 0)); then
        wait  # Wait for batch to complete
    fi
    (
        start=$(date +%s%N)
        response=$(curl -s -w "%{http_code}" "$URL")
        end=$(date +%s%N)
        duration=$(( (end - start) / 1000000 ))
        echo "Request $i: ${response: -3} in ${duration}ms"
    ) &
done
wait
```

#### Memory Leak Test
```bash
#!/bin/bash
# memory-test.sh

echo "Starting memory leak test..."
for i in {1..1000}; do
    curl -s "http://localhost:63343/api/intellij-actions/execute?action=About" > /dev/null
    if ((i % 100 == 0)); then
        echo "Completed $i requests"
        # Check IntelliJ memory usage
        jps | grep "Main" | awk '{print $1}' | xargs jstat -gc
    fi
done
```

### Stress Testing

#### Action Chain Stress Test
```bash
#!/bin/bash
# chain-stress-test.sh

CHAINS=50
ACTIONS="SaveAll,OptimizeImports,ReformatCode"

for i in $(seq 1 $CHAINS); do
    echo "Chain $i/$CHAINS"
    curl -s "http://localhost:63343/api/intellij-actions/execute?actions=$ACTIONS" | \
        jq -r '.success'
    sleep 0.5
done
```

## CLI Testing

### CLI Functionality Tests

```bash
# Test basic execution
test_cli_basic() {
    echo "Testing basic CLI functionality..."
    
    # Test single action
    ~/ij About && echo "✓ Single action CLI" || echo "✗ Single action CLI failed"
    
    # Test multiple actions
    ~/ij SaveAll ReformatCode && echo "✓ Multiple actions CLI" || echo "✗ Multiple actions CLI failed"
    
    # Test invalid action
    ~/ij NonExistentAction 2>&1 | grep -q "failed" && echo "✓ CLI error handling" || echo "✗ CLI error handling failed"
}

# Test CLI server selection
test_cli_server_selection() {
    echo "Testing CLI server selection..."
    
    # Test custom server preference
    ~/ij About 2>&1 | grep -q "Action executed" && echo "✓ CLI custom server" || echo "✗ CLI custom server failed"
    
    # Test built-in server fallback
    ~/ij --force-builtin About 2>&1 | grep -q "Action executed" && echo "✓ CLI built-in server" || echo "✗ CLI built-in server failed"
}

# Test CLI error conditions
test_cli_errors() {
    echo "Testing CLI error conditions..."
    
    # Test with no arguments
    ~/ij 2>&1 | grep -q "Usage:" && echo "✓ CLI usage message" || echo "✗ CLI usage message failed"
    
    # Test connection failure (stop IntelliJ first)
    # ~/ij About 2>&1 | grep -q "Failed to connect" && echo "✓ CLI connection error" || echo "✗ CLI connection error failed"
}
```

## Regression Testing

### Critical Bug Prevention

#### EDT Threading Regression Test (v1.1.3)
```bash
# Test for EDT violations that were fixed in v1.1.3
test_edt_safety() {
    echo "Testing EDT safety (regression test for v1.1.3)..."
    
    # These actions previously caused EDT violations
    local edt_sensitive_actions=(
        "CopyPaths"
        "CopyAbsolutePath"
        "SaveDocument"
        "Git.Branches"
    )
    
    for action in "${edt_sensitive_actions[@]}"; do
        echo "Testing EDT safety for $action..."
        RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=$action")
        
        # Check response and IDE logs for EDT violations
        if echo "$RESPONSE" | grep -q '"success":true'; then
            echo "✓ $action executed without EDT violations"
        else
            echo "✗ $action failed - check for EDT violations"
        fi
    done
}
```

#### Context-Dependent Action Regression Test (v1.1.2)
```bash
# Test for context issues fixed in v1.1.2
test_context_actions() {
    echo "Testing context-dependent actions (regression test for v1.1.2)..."
    
    # Open a file first
    curl -s "$BASE_URL_CUSTOM/execute?action=GotoFile"
    sleep 1
    
    # These actions require proper context
    local context_actions=(
        "CopyPaths"
        "ReformatCode"
        "OptimizeImports"
        "GotoDeclaration"
    )
    
    for action in "${context_actions[@]}"; do
        RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=$action")
        echo "Context test $action: $(echo "$RESPONSE" | jq -r '.success')"
    done
}
```

#### Rate Limiting Regression Test (v1.0.8)
```bash
# Test for rate limiting issues fixed in v1.0.8
test_rate_limiting() {
    echo "Testing rate limiting bypass (regression test for v1.0.8)..."
    
    # Send many rapid requests to built-in server (should rate limit)
    echo "Testing built-in server rate limiting..."
    rate_limited=false
    for i in {1..20}; do
        RESPONSE=$(curl -s "$BASE_URL_BUILTIN/execute?action=About")
        if echo "$RESPONSE" | grep -q "429\|Too Many Requests"; then
            rate_limited=true
            break
        fi
    done
    
    if [ "$rate_limited" = true ]; then
        echo "✓ Built-in server properly rate limits"
    else
        echo "? Built-in server may not be rate limiting (check IntelliJ version)"
    fi
    
    # Send many rapid requests to custom server (should not rate limit)
    echo "Testing custom server rate limiting bypass..."
    all_success=true
    for i in {1..20}; do
        RESPONSE=$(curl -s "$BASE_URL_CUSTOM/execute?action=About")
        if ! echo "$RESPONSE" | grep -q '"success":true'; then
            all_success=false
            break
        fi
    done
    
    if [ "$all_success" = true ]; then
        echo "✓ Custom server bypasses rate limiting"
    else
        echo "✗ Custom server may be rate limiting"
    fi
}
```

## Test Environments

### Development Environment
- **IntelliJ Version**: Latest EAP
- **Project Type**: Mixed (Java, Kotlin, etc.)
- **System**: macOS/Linux/Windows
- **JDK**: 17+

### CI/CD Environment
```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: ./gradlew test
      - run: ./gradlew buildPlugin
      - run: ./gradlew verifyPlugin
```

### Release Testing Environment
- **IntelliJ Version**: Minimum supported (2024.1)
- **Project Types**: Various (Java, Kotlin, Python, etc.)
- **Operating Systems**: macOS, Windows, Linux
- **Load Conditions**: Multiple projects, large files

## Troubleshooting Tests

### Common Test Failures

#### "Connection Refused" Error
```bash
# Check if IntelliJ is running
ps aux | grep -i intellij

# Check if plugin is enabled
curl -s "http://localhost:63342/api/intellij-actions/health"
curl -s "http://localhost:63343/api/intellij-actions/health"

# Check ports are not blocked
netstat -an | grep :63342
netstat -an | grep :63343
```

#### "Action Not Found" Error
```bash
# Verify action ID is correct
curl -s "http://localhost:63343/api/intellij-actions/list" | grep -i "actionname"

# Check action availability
curl -s "http://localhost:63343/api/intellij-actions/check?action=ActionId"
```

#### EDT Violation Errors
```bash
# Check IntelliJ logs for EDT violations
tail -f ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | grep -i "edt"

# Look for specific error patterns
grep -A 5 -B 5 "Access is allowed from Event Dispatch Thread" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

#### Performance Issues
```bash
# Monitor CPU and memory usage
top -p $(pgrep -f "idea")

# Check thread count
jstack $(pgrep -f "idea") | grep -c "Thread"

# Monitor GC activity
jstat -gc $(pgrep -f "idea") 1s
```

### Test Debugging

#### Enable Verbose Logging
```bash
# Add to VM options in IntelliJ:
-Dcom.leaderkey.intellij.debug=true
-Didea.log.debug.categories=com.leaderkey.intellij
```

#### Monitor Network Traffic
```bash
# Monitor HTTP requests
sudo tcpdump -i lo0 -A -s0 port 63342 or port 63343

# Use Charles Proxy or similar for HTTP analysis
```

#### Test with Different IDE States
- Fresh IDE start
- IDE with multiple projects
- IDE during indexing
- IDE with heavy CPU usage
- IDE with low memory

### Automated Test Execution

#### Complete Test Suite
```bash
#!/bin/bash
# run-all-tests.sh

echo "Starting comprehensive test suite..."

# Unit tests
echo "Running unit tests..."
./gradlew test

# Build verification
echo "Building plugin..."
./gradlew buildPlugin

# Start IntelliJ for integration tests
echo "Starting IntelliJ for integration tests..."
./gradlew runIde &
INTELLIJ_PID=$!

# Wait for startup
sleep 30

# Integration tests
echo "Running integration tests..."
./integration-tests.sh

# Performance tests
echo "Running performance tests..."
./load-test.sh

# Regression tests
echo "Running regression tests..."
test_edt_safety
test_context_actions
test_rate_limiting

# Cleanup
echo "Cleaning up..."
kill $INTELLIJ_PID

echo "Test suite completed!"
```

This comprehensive testing guide ensures the IntelliJ Action Executor plugin maintains high quality and reliability across all supported use cases and environments.