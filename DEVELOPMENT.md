# Development Guide

## Table of Contents
1. [Project Structure](#project-structure)
2. [Development Environment Setup](#development-environment-setup)
3. [Building the Plugin](#building-the-plugin)
4. [Running and Debugging](#running-and-debugging)
5. [Architecture Overview](#architecture-overview)
6. [Contributing Guidelines](#contributing-guidelines)
7. [Testing](#testing)
8. [Release Process](#release-process)

## Project Structure

```
intellijPlugin/
├── src/main/kotlin/com/leaderkey/intellij/
│   ├── ActionExecutorService.kt    # Core action execution logic
│   ├── ActionRestService.kt        # Built-in REST API (port 63342)
│   ├── CustomHttpServer.kt         # Custom HTTP server (port 63343)
│   └── PluginStartup.kt           # Plugin initialization
├── src/main/resources/META-INF/
│   └── plugin.xml                  # Plugin configuration
├── cli/
│   └── ij                          # CLI tool script
├── docs/                           # Documentation
├── build.gradle.kts               # Build configuration
└── gradle.properties              # Gradle properties
```

## Development Environment Setup

### Prerequisites

- **JDK 17** or later
- **IntelliJ IDEA 2024.1** or later (Ultimate or Community)
- **Kotlin 1.9.22** or later
- **Gradle 8.x** (included via wrapper)

### Initial Setup

1. **Clone the repository:**
```bash
git clone https://github.com/arabshapt/intellij-action-executor.git
cd intellij-action-executor
```

2. **Open in IntelliJ IDEA:**
- File → Open → Select project directory
- Trust the project when prompted
- Wait for Gradle sync to complete

3. **Configure IntelliJ Platform Plugin:**
- The project uses IntelliJ Platform Plugin 1.17.4
- Plugin will automatically download IntelliJ Platform SDK

## Building the Plugin

### Build Commands

```bash
# Clean build artifacts
./gradlew clean

# Build plugin distribution
./gradlew buildPlugin

# Run tests
./gradlew test

# Run IntelliJ with plugin
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin
```

### Build Configuration

The `build.gradle.kts` configures:
- **Target Platform**: IntelliJ IDEA Community 2024.1
- **Kotlin JVM Target**: 17
- **Compatibility Range**: 241 to 251.*

### Build Output

Built plugin location: `build/distributions/intellijPlugin-1.1.3.zip`

## Running and Debugging

### Run Plugin in IDE

1. **Using Gradle:**
```bash
./gradlew runIde
```

2. **Using IntelliJ Run Configuration:**
- Click the "Run Plugin" configuration in toolbar
- Or press `Shift+F10`

### Debugging

1. **Set breakpoints** in Kotlin code
2. **Run Debug Configuration:**
- Click "Debug Plugin" configuration
- Or press `Shift+F9`

3. **Common Debug Points:**
- `ActionExecutorService.executeActionInternal()` - Action execution
- `CustomHttpServer.ExecuteHandler.handle()` - HTTP request handling
- `createComprehensiveDataContext()` - Context creation

### Testing HTTP Endpoints

```bash
# Test custom server (port 63343)
curl http://localhost:63343/api/intellij-actions/health

# Test built-in API (port 63342)
curl http://localhost:63342/api/intellij-actions/health

# Execute action
curl "http://localhost:63343/api/intellij-actions/execute?action=About"
```

## Architecture Overview

### Core Components

#### ActionExecutorService
- **Purpose**: Core service for executing IntelliJ actions
- **Key Methods**:
  - `executeAction()` - Single action execution
  - `executeActions()` - Chain execution with delays
  - `createComprehensiveDataContext()` - Context creation

#### Threading Model
- **EDT Safety**: All UI operations on Event Dispatch Thread
- **Execution Modes**:
  - Synchronous: `invokeAndWait` for action chains
  - Asynchronous: `invokeLater` for dialog actions

#### Dual Server Architecture
1. **ActionRestService** (Built-in, port 63342)
   - Uses IntelliJ's RestService extension point
   - Subject to rate limiting
   - Automatic registration via plugin.xml

2. **CustomHttpServer** (Custom, port 63343)
   - No rate limiting
   - 10-thread executor pool
   - Lightweight JSON serialization

### Action Execution Flow

```
HTTP Request → Server Handler → ActionExecutorService
                                        ↓
                              Check if EDT required
                                        ↓
                         Create Runnable with UI operations
                                        ↓
                              Execute on EDT
                                        ↓
                         Create DataContext (on EDT)
                                        ↓
                    Create AnActionEvent with KEYBOARD_SHORTCUT
                                        ↓
                         ActionUtil.performDumbAwareUpdate()
                                        ↓
                           ActionUtil.invokeAction()
```

## Contributing Guidelines

### Code Style

1. **Kotlin Conventions:**
   - Use idiomatic Kotlin patterns
   - Prefer `val` over `var`
   - Use data classes for DTOs
   - Leverage null safety features

2. **Naming:**
   - Classes: PascalCase
   - Functions/Variables: camelCase
   - Constants: UPPER_SNAKE_CASE

3. **Documentation:**
   - Add KDoc comments for public APIs
   - Include usage examples in comments
   - Document edge cases and limitations

### Making Changes

1. **Create a feature branch:**
```bash
git checkout -b feature/your-feature-name
```

2. **Make your changes:**
   - Follow existing patterns
   - Add tests for new functionality
   - Update documentation

3. **Test thoroughly:**
```bash
./gradlew clean test buildPlugin
./gradlew runIde  # Manual testing
```

4. **Commit with clear messages:**
```bash
git commit -m "feat: Add support for new action type"
```

### Pull Request Process

1. **Before submitting:**
   - Run all tests
   - Update CHANGELOG.md
   - Update documentation
   - Verify plugin builds

2. **PR Description should include:**
   - Problem being solved
   - Solution approach
   - Testing performed
   - Breaking changes (if any)

### Common Development Tasks

#### Adding a New Endpoint

1. Add handler in `CustomHttpServer.kt`:
```kotlin
createContext("/api/intellij-actions/new-endpoint", NewHandler())
```

2. Add corresponding method in `ActionRestService.kt`

3. Update API documentation

#### Supporting New Action Types

1. Update action categorization in `ActionExecutorService`:
```kotlin
private fun isSpecialAction(actionId: String): Boolean {
    val specialActions = setOf("YourNewAction")
    return specialActions.contains(actionId)
}
```

2. Add specific handling if needed

#### Improving Context Creation

1. Enhance `createComprehensiveDataContext()`:
```kotlin
// Add new context data
builder.add(YourDataKey.KEY, yourData)
```

2. Test with actions requiring that context

## Testing

### Unit Tests

Location: `src/test/kotlin/`

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "ActionExecutorServiceTest"
```

### Integration Testing

1. **Manual Testing Checklist:**
   - [ ] Single action execution
   - [ ] Multiple action chain
   - [ ] Context-dependent actions (CopyPaths)
   - [ ] Dialog actions (ShowSettings)
   - [ ] UI actions (SplitVertically)
   - [ ] CLI tool functionality
   - [ ] Rate limiting bypass

2. **Test Scenarios:**
```bash
# Test basic action
curl "http://localhost:63343/api/intellij-actions/execute?action=About"

# Test action chain
curl "http://localhost:63343/api/intellij-actions/execute?actions=SaveAll,ReformatCode,OptimizeImports"

# Test with delay
curl "http://localhost:63343/api/intellij-actions/execute?actions=SaveAll,ReformatCode&delay=500"
```

### Performance Testing

Monitor for:
- Memory leaks in long-running sessions
- Thread pool exhaustion
- EDT blocking
- Response times under load

## Release Process

### Version Bumping

1. Update version in `build.gradle.kts`:
```kotlin
version = "1.1.4"
```

2. Update README.md version badge

3. Update CHANGELOG.md with release notes

### Building Release

```bash
# Clean and build
./gradlew clean buildPlugin

# Verify plugin
./gradlew verifyPlugin

# Plugin will be in build/distributions/
```

### Publishing

1. **GitHub Release:**
   - Create tag: `git tag v1.1.4`
   - Push tag: `git push origin v1.1.4`
   - Create release on GitHub
   - Upload plugin ZIP

2. **JetBrains Marketplace (Optional):**
   - Configure publishing credentials
   - Run: `./gradlew publishPlugin`

## Troubleshooting Development Issues

### Gradle Sync Failed
- Check JDK version (must be 17+)
- Clear Gradle cache: `./gradlew clean --refresh-dependencies`

### Plugin Not Loading
- Verify plugin.xml is correct
- Check for compilation errors
- Review IDE logs: Help → Show Log in Finder/Explorer

### EDT Violations
- Always use `invokeAndWait` or `invokeLater` for UI operations
- Check thread with `SwingUtilities.isEventDispatchThread()`
- Enable EDT violation detection in IDE

### Action Not Found
- Verify action ID is correct
- Check if action requires specific IDE edition
- Ensure action is registered in platform

## Resources

- [IntelliJ Platform SDK Documentation](https://plugins.jetbrains.com/docs/intellij/)
- [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [IntelliJ Community Source](https://github.com/JetBrains/intellij-community)

## Support

For development questions:
- Create an issue on [GitHub](https://github.com/arabshapt/intellij-action-executor/issues)
- Check existing issues for solutions
- Review test cases for usage examples