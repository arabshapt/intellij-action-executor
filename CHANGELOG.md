# Changelog

All notable changes to the IntelliJ Action Executor plugin will be documented in this file.

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