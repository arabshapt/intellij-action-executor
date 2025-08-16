# IntelliJ Plugin Test Suite

Comprehensive test suite for the IntelliJ Plugin CLI and conditional execution system.

## Quick Start

```bash
# Run all tests
./test-runner.sh

# Run with verbose output
./test-runner.sh -v

# Run specific test suite
./test-runner.sh -s test-basic.sh

# List available test suites
./test-runner.sh -l
```

## Test Suites

| Suite | Description | Coverage |
|-------|-------------|----------|
| `test-basic.sh` | Basic action execution | Single actions, error handling |
| `test-conditional.sh` | Conditional execution | if-then-else logic |
| `test-negation.sh` | Negation operators | `!` and `not:` prefixes |
| `test-chains.sh` | Action chains | Multiple actions, OR fallbacks |
| `test-fire-forget.sh` | Fire-and-forget modes | `-ff` and `-fff` flags |
| `test-toolwindows.sh` | Tool window detection | projectView, terminal, etc. |
| `test-focus.sh` | Focus detection | Editor focus, project focus |

## Test Runner Options

```bash
OPTIONS:
    -v, --verbose          Show detailed test output
    -s, --suite <name>     Run specific test suite
    -l, --list             List available test suites
    -c, --clean            Clean test artifacts
    -f, --format <type>    Output format: console, json, junit
    -h, --help             Show help message
```

## Output Formats

### Console (default)
Color-coded output with pass/fail indicators and summary statistics.

### JSON
```bash
./test-runner.sh -f json
```
Generates `test-results.json` with detailed test metrics.

### JUnit XML
```bash
./test-runner.sh -f junit
```
Generates `test-results.xml` for CI/CD integration.

## Requirements

- IntelliJ IDEA must be running
- Plugin must be installed and HTTP server active
- `ij` CLI script must be in PATH

## Writing New Tests

Tests use the helper functions from `test-helpers.sh`:

```bash
#!/bin/bash
source "$(dirname "$0")/test-helpers.sh"

# Start test suite
start_test_suite "My Test Suite"

# Run individual tests
run_test "Test name" "ij --action MyAction" "expected output"

# Check conditions
if check_condition "editor"; then
    pass_test "Editor is focused"
else
    fail_test "Editor not focused"
fi

# End suite and show summary
end_test_suite
```

## CI/CD Integration

```yaml
# GitHub Actions example
- name: Run tests
  run: |
    cd test
    ./test-runner.sh -f junit
    
- name: Publish test results
  uses: EnricoMi/publish-unit-test-result-action@v2
  if: always()
  with:
    files: test/test-results.xml
```

## Troubleshooting

### Tests failing with "IntelliJ not running"
Ensure IntelliJ IDEA is started before running tests.

### Connection refused errors
Check that the plugin's HTTP server is running on port 63342.

### Specific test failures
Run with `-v` flag for detailed output:
```bash
./test-runner.sh -v -s test-basic.sh
```

## Maintenance

Clean test artifacts:
```bash
./test-runner.sh -c
```

Update test expectations after API changes:
1. Run tests to identify failures
2. Update expected values in test files
3. Re-run to verify fixes