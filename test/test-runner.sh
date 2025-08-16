#!/bin/bash

# IntelliJ Plugin Test Runner
# Main orchestrator for all test suites

# Source helpers
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test-helpers.sh"

# Configuration
VERBOSE=false
SPECIFIC_SUITE=""
LIST_ONLY=false
CLEAN_ONLY=false
OUTPUT_FORMAT="console"  # console, json, junit
RESULTS_FILE="$SCRIPT_DIR/test-results.json"
JUNIT_FILE="$SCRIPT_DIR/test-results.xml"

# Test suite registry
declare -a TEST_SUITES=(
    "test-basic.sh:Basic action execution"
    "test-conditional.sh:Conditional execution (if-then-else)"
    "test-negation.sh:Negation operators (! and not:)"
    "test-chains.sh:Action chains and OR fallbacks"
    "test-fire-forget.sh:Fire-and-forget modes"
    "test-toolwindows.sh:Tool window detection"
    "test-focus.sh:Focus detection"
)

# Global counters
TOTAL_PASSED=0
TOTAL_FAILED=0
TOTAL_SKIPPED=0
SUITE_RESULTS=()

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -s|--suite)
                SPECIFIC_SUITE="$2"
                shift 2
                ;;
            -l|--list)
                LIST_ONLY=true
                shift
                ;;
            -c|--clean)
                CLEAN_ONLY=true
                shift
                ;;
            -f|--format)
                OUTPUT_FORMAT="$2"
                shift 2
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

# Show usage information
show_usage() {
    cat << EOF
IntelliJ Plugin Test Runner

Usage: $0 [OPTIONS]

OPTIONS:
    -v, --verbose          Show detailed test output
    -s, --suite <name>     Run specific test suite
    -l, --list             List available test suites
    -c, --clean            Clean test artifacts
    -f, --format <type>    Output format: console (default), json, junit
    -h, --help             Show this help message

EXAMPLES:
    $0                     Run all test suites
    $0 -v                  Run all tests with verbose output
    $0 -s test-basic.sh    Run only basic tests
    $0 -l                  List available test suites
    $0 -f junit            Generate JUnit XML output

EOF
}

# List available test suites
list_suites() {
    echo -e "${CYAN}Available Test Suites:${NC}"
    echo
    for suite_info in "${TEST_SUITES[@]}"; do
        IFS=':' read -r suite_file suite_desc <<< "$suite_info"
        printf "  ${BOLD}%-25s${NC} %s\n" "$suite_file" "$suite_desc"
    done
    echo
}

# Clean test artifacts
clean_artifacts() {
    echo -e "${YELLOW}Cleaning test artifacts...${NC}"
    rm -f "$RESULTS_FILE" "$JUNIT_FILE"
    rm -f "$SCRIPT_DIR"/*.log
    rm -f "$SCRIPT_DIR"/.test-*
    echo -e "${GREEN}✓ Test artifacts cleaned${NC}"
}

# Run a single test suite
run_suite() {
    local suite_file="$1"
    local suite_desc="$2"
    local suite_path="$SCRIPT_DIR/$suite_file"
    
    if [[ ! -f "$suite_path" ]]; then
        echo -e "${RED}✗ Suite not found: $suite_file${NC}"
        return 1
    fi
    
    echo
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Running: $suite_desc${NC}"
    echo -e "${DIM}File: $suite_file${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    # Record start time
    local start_time=$(date +%s)
    
    # Run the test suite
    if [[ "$VERBOSE" == "true" ]]; then
        bash "$suite_path"
    else
        bash "$suite_path" 2>&1 | grep -E "(✓|✗|⚠|Running|PASSED|FAILED|Summary)"
    fi
    
    local exit_code=$?
    
    # Record end time
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # Extract test results from the suite's output
    # This assumes each suite exports these variables
    local suite_passed=${TEST_PASSED:-0}
    local suite_failed=${TEST_FAILED:-0}
    local suite_skipped=${TEST_SKIPPED:-0}
    
    # Update global counters
    TOTAL_PASSED=$((TOTAL_PASSED + suite_passed))
    TOTAL_FAILED=$((TOTAL_FAILED + suite_failed))
    TOTAL_SKIPPED=$((TOTAL_SKIPPED + suite_skipped))
    
    # Store suite result
    SUITE_RESULTS+=("$suite_file:$suite_passed:$suite_failed:$suite_skipped:$duration:$exit_code")
    
    # Show suite summary
    echo
    if [[ $exit_code -eq 0 ]]; then
        echo -e "${GREEN}✓ Suite completed successfully${NC} (${duration}s)"
    else
        echo -e "${RED}✗ Suite failed${NC} (${duration}s)"
    fi
    
    return $exit_code
}

# Generate JSON report
generate_json_report() {
    cat > "$RESULTS_FILE" << EOF
{
    "timestamp": "$(date -Iseconds)",
    "total_passed": $TOTAL_PASSED,
    "total_failed": $TOTAL_FAILED,
    "total_skipped": $TOTAL_SKIPPED,
    "suites": [
EOF
    
    local first=true
    for result in "${SUITE_RESULTS[@]}"; do
        IFS=':' read -r name passed failed skipped duration exit_code <<< "$result"
        
        if [[ "$first" != "true" ]]; then
            echo "," >> "$RESULTS_FILE"
        fi
        first=false
        
        cat >> "$RESULTS_FILE" << EOF
        {
            "name": "$name",
            "passed": $passed,
            "failed": $failed,
            "skipped": $skipped,
            "duration": $duration,
            "status": $([ $exit_code -eq 0 ] && echo '"passed"' || echo '"failed"')
        }
EOF
    done
    
    cat >> "$RESULTS_FILE" << EOF

    ]
}
EOF
    
    echo -e "${GREEN}✓ JSON report saved to: $RESULTS_FILE${NC}"
}

# Generate JUnit XML report
generate_junit_report() {
    local total_tests=$((TOTAL_PASSED + TOTAL_FAILED + TOTAL_SKIPPED))
    local total_time=0
    
    for result in "${SUITE_RESULTS[@]}"; do
        IFS=':' read -r _ _ _ _ duration _ <<< "$result"
        total_time=$((total_time + duration))
    done
    
    cat > "$JUNIT_FILE" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="IntelliJ Plugin Tests" tests="$total_tests" failures="$TOTAL_FAILED" skipped="$TOTAL_SKIPPED" time="$total_time">
EOF
    
    for result in "${SUITE_RESULTS[@]}"; do
        IFS=':' read -r name passed failed skipped duration exit_code <<< "$result"
        local suite_tests=$((passed + failed + skipped))
        
        cat >> "$JUNIT_FILE" << EOF
    <testsuite name="$name" tests="$suite_tests" failures="$failed" skipped="$skipped" time="$duration">
        <!-- Individual test cases would go here in a real implementation -->
    </testsuite>
EOF
    done
    
    cat >> "$JUNIT_FILE" << EOF
</testsuites>
EOF
    
    echo -e "${GREEN}✓ JUnit report saved to: $JUNIT_FILE${NC}"
}

# Show final summary
show_summary() {
    echo
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}TEST SUMMARY${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo
    
    # Suite-by-suite results
    echo -e "${BOLD}Suite Results:${NC}"
    for result in "${SUITE_RESULTS[@]}"; do
        IFS=':' read -r name passed failed skipped duration exit_code <<< "$result"
        local status_icon=$([ $exit_code -eq 0 ] && echo "${GREEN}✓${NC}" || echo "${RED}✗${NC}")
        printf "  %s %-25s %s passed, %s failed, %s skipped (%ss)\n" \
            "$status_icon" "$name" "$passed" "$failed" "$skipped" "$duration"
    done
    
    echo
    echo -e "${BOLD}Overall Results:${NC}"
    echo -e "  ${GREEN}Passed:${NC}  $TOTAL_PASSED"
    echo -e "  ${RED}Failed:${NC}  $TOTAL_FAILED"
    echo -e "  ${YELLOW}Skipped:${NC} $TOTAL_SKIPPED"
    echo
    
    if [[ $TOTAL_FAILED -eq 0 ]]; then
        echo -e "${GREEN}${BOLD}✓ ALL TESTS PASSED!${NC}"
        return 0
    else
        echo -e "${RED}${BOLD}✗ SOME TESTS FAILED${NC}"
        return 1
    fi
}

# Main execution
main() {
    parse_args "$@"
    
    # Handle special modes
    if [[ "$LIST_ONLY" == "true" ]]; then
        list_suites
        exit 0
    fi
    
    if [[ "$CLEAN_ONLY" == "true" ]]; then
        clean_artifacts
        exit 0
    fi
    
    # Start test run
    echo -e "${BOLD}${CYAN}IntelliJ Plugin Test Runner${NC}"
    echo -e "${DIM}Starting test execution at $(date)${NC}"
    
    # Check IntelliJ is running
    if ! check_intellij_running; then
        echo -e "${YELLOW}⚠ Warning: IntelliJ doesn't appear to be running${NC}"
        echo -e "${YELLOW}  Some tests may fail. Please start IntelliJ and try again.${NC}"
        echo
    fi
    
    # Run test suites
    local overall_exit_code=0
    
    if [[ -n "$SPECIFIC_SUITE" ]]; then
        # Run specific suite
        local found=false
        for suite_info in "${TEST_SUITES[@]}"; do
            IFS=':' read -r suite_file suite_desc <<< "$suite_info"
            if [[ "$suite_file" == "$SPECIFIC_SUITE" ]]; then
                found=true
                run_suite "$suite_file" "$suite_desc"
                overall_exit_code=$?
                break
            fi
        done
        
        if [[ "$found" != "true" ]]; then
            echo -e "${RED}✗ Suite not found: $SPECIFIC_SUITE${NC}"
            echo "Use -l to list available suites"
            exit 1
        fi
    else
        # Run all suites
        for suite_info in "${TEST_SUITES[@]}"; do
            IFS=':' read -r suite_file suite_desc <<< "$suite_info"
            run_suite "$suite_file" "$suite_desc"
            if [[ $? -ne 0 ]]; then
                overall_exit_code=1
            fi
        done
    fi
    
    # Generate reports based on format
    case "$OUTPUT_FORMAT" in
        json)
            generate_json_report
            ;;
        junit)
            generate_junit_report
            ;;
        console|*)
            # Console output is default, already shown
            ;;
    esac
    
    # Show summary
    show_summary
    exit_code=$?
    
    echo
    echo -e "${DIM}Test execution completed at $(date)${NC}"
    
    exit $exit_code
}

# Run main function
main "$@"