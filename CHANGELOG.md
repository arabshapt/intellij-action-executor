# Changelog

All notable changes to the IntelliJ Action Executor plugin will be documented in this file.

## [1.1.7] - 2025-08-15

### Added
- Action History Tracking with persistent storage in ~/.intellij-actions/
- Execution statistics tracking (success/failure rates, execution times)
- Usage pattern analysis to identify frequently used action sequences
- Intelligent suggestions based on usage patterns and failure rates
- History HTTP endpoints: `/history`, `/stats`, `/suggestions`
- CLI analytics commands: `--history`, `--stats`, `--suggestions`
- Gson dependency for JSON serialization

### Changed
- ActionExecutorService now records all executions with timing and error details
- Action chains are tracked to identify common usage patterns
- History automatically persists every 30 seconds and on shutdown

### Improved
- Better understanding of user workflows through usage analytics
- Proactive suggestions for optimizing action sequences
- Identification of frequently failing actions for troubleshooting
- Performance insights with execution time tracking

### CLI Examples
```bash
ij --history 20          # Show last 20 executed actions
ij --stats ReformatCode  # Show statistics for specific action
ij --stats               # Show top 20 most used actions
ij --suggestions         # Get usage suggestions and patterns
```

## [1.1.6] - 2025-08-15

### Added
- Enhanced error context with actionable suggestions for failed actions
- Error type classification (EDITOR_REQUIRED, FILE_REQUIRED, GIT_REQUIRED, etc.)
- Action discovery CLI with search and explain functionality
- New HTTP endpoints: `/search` and `/explain` for action discovery
- CLI flags: `--search`, `--explain`, `--list` for exploring available actions
- Pre-execution validation to detect missing requirements
- Detailed error messages explaining why actions are disabled

### Changed
- ExecutionResult now includes errorType, suggestion, and requiredContext fields
- Error messages provide specific guidance on how to fix issues
- HTTP responses include enhanced error context

### Improved
- Better user experience with clear, actionable error messages
- Self-documenting plugin - users can discover actions without external documentation
- Easier debugging with detailed context about action requirements

### CLI Examples
```bash
ij --search format        # Find format-related actions
ij --explain ReformatCode # Show action requirements and description
ij --list                 # Show count of available actions
```

## [1.1.5] - 2025-08-15

### Added
- Smart delay optimization system with ActionCategorizer service
- Intelligent action classification (instant, UI, async, dialog, etc.)
- Dynamic delay calculation based on action types and sequences

### Changed
- Default behavior now uses smart delays instead of fixed 250ms
- Delays range from 0ms (instant actions) to 500ms (async operations)
- HTTP API now defaults to smart delays (use explicit delay parameter to override)

### Improved
- Action chains execute 70-90% faster for common operations
- Zero delay between non-UI actions (SaveAll → Copy → Paste)
- Minimal delays for UI updates (25-50ms)
- Appropriate delays for async operations (builds, VCS operations)

### Performance Improvements
| Action Chain | Before | After | Improvement |
|-------------|--------|-------|-------------|
| SaveAll → ReformatCode → OptimizeImports | 500ms | 50ms | 90% faster |
| Copy → Paste → SaveAll | 500ms | 0ms | Instant |
| ActivateProjectToolWindow → Tree-selectFirst | 350ms | 250ms | 29% faster |

## [1.1.4] - 2025-08-15

### Fixed
- Tree navigation actions (Tree-selectFirst, Tree-selectNext) now work correctly in action chains
- Actions now receive live DataContext from the focused component instead of static snapshot
- Git.CompareWithBranch and similar context-dependent actions now operate on correct selection

### Changed
- Replaced static DataContext creation with live context retrieval using DataManager
- Added getLiveDataContext method that gets context from currently focused component
- Improved context resolution with fallback to synthetic context for headless/testing scenarios

### Improved
- Universal solution for all actions - no special handling needed for different action types
- Better focus handling ensures actions operate on the correct UI component
- More accurate context state between chained actions

## [1.1.3] - 2025-08-15

### Fixed
- **Critical**: "Access is allowed from Event Dispatch Thread (EDT) only" errors
- DataContext creation now happens inside EDT execution block
- All UI operations properly wrapped in EDT-safe Runnable blocks
- Context-dependent actions (CopyPaths, CopyAbsolutePath) now work reliably

### Changed
- Refactored executeActionInternal to ensure complete EDT safety
- Moved project and context retrieval inside EDT execution
- Improved synchronous/asynchronous execution logic based on action type

### Improved
- Better error handling with detailed exception logging
- More robust execution flow for action chains
- Clearer separation between dialog and regular actions

## [1.1.2] - 2025-08-14

### Added
- Adopted ataman-intellij plugin's execution approach for better action compatibility
- ActionUtil.invokeAction() for proper action execution
- ActionUtil.performDumbAwareUpdate() for action state validation

### Changed
- All actions now use ActionPlaces.KEYBOARD_SHORTCUT for consistent context
- Replaced direct action.actionPerformed() calls with ActionUtil methods
- Event creation now matches keyboard shortcut behavior exactly

### Fixed
- Context-dependent actions that were previously disabled
- Actions requiring specific UI context now work correctly
- Git and refactoring actions have proper context

### Technical
- Better alignment with IntelliJ's action execution patterns
- More reliable action state updates before execution

## [1.1.0] - 2025-08-14

### Added
- Comprehensive data context for all action executions
- Support for context-dependent actions like CopyPaths, CopyAbsolutePath
- Automatic detection of current editor, file, and selections
- Project view selection context
- Module and PSI element context
- Navigation context for file-based actions

### Fixed
- "Action is disabled in current context" errors for file operations
- Git actions now work with proper file context
- Refactoring actions have required PSI elements
- Copy/paste actions have file and selection context

### Improved
- Actions now receive same context as keyboard shortcuts
- Better logging of available context elements
- Fallback mechanisms for context retrieval

## [1.0.9] - 2025-08-14

### Fixed
- Multiple actions now execute sequentially with proper completion waiting
- Actions like `Tree-selectFirst` now work correctly after UI actions
- Increased default delay between actions from 100ms to 250ms for better UI responsiveness

### Changed
- Implemented synchronous action execution using `invokeAndWait` for action chains
- Added intelligent action categorization (dialog actions vs UI actions)
- Added extra delays for UI actions to ensure proper state updates

### Improved
- Better logging for action chain execution progress
- More detailed error reporting for failed actions in chains

## [1.0.8] - 2025-08-14

### Added
- Custom HTTP server on port 63343 with NO rate limiting
- Automatic fallback from custom server to built-in API
- `--force-builtin` flag in CLI tool to explicitly use built-in API

### Changed
- CLI tool now automatically tries custom server first (port 63343)
- Improved error handling for rate limiting scenarios

### Fixed
- Rate limiting (429 Too Many Requests) errors when sending multiple requests rapidly
- Custom server eliminates throttling issues completely

## [1.0.7] - 2025-08-14

### Added
- Direct HTTP execution via CLI tool
- LeaderKey.app integration documentation
- Comprehensive API documentation

### Changed
- Simplified action execution with invokeLater
- Removed unnecessary focus management code

### Fixed
- Actions only executing on second request issue
- URL handler complexity removed in favor of direct HTTP

## [1.0.6] - 2025-08-14

### Fixed
- Port configuration (63342 instead of 63343)
- JSON response handling

## [1.0.5] - 2025-08-14

### Fixed
- Gson library conflicts with IntelliJ platform
- Custom JSON serialization implementation

## [1.0.4] - 2025-08-14

### Fixed
- HTML-wrapped JSON responses
- Improved error handling

## [1.0.3] - 2025-08-14

### Changed
- Switched from synchronous to asynchronous action execution

## [1.0.2] - 2025-08-14

### Fixed
- Action execution blocking on dialog actions

## [1.0.1] - 2025-08-14

### Fixed
- Initial compatibility issues with IntelliJ 2024.1

## [1.0.0] - 2025-08-14

### Added
- Initial release
- REST API for executing IntelliJ actions
- Support for single and multiple action execution
- URL scheme handler for macOS
- Basic CLI tool